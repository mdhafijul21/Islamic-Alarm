package com.hafij.islamicalarm.restrict

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hafij.islamicalarm.MainActivity
import com.hafij.islamicalarm.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Local system-wide DNS-only VPN blocker.
 *
 * Routes ONLY virtual DNS addresses (10.120.0.2 / fd00:120::2) through the TUN interface so normal
 * app traffic runs at native speed while every DNS query across Chrome, Incognito tabs, VidMate,
 * SnapTube, Telegram, and other apps is inspected locally:
 *  - Adult/NSFW domains (including user custom domains) immediately receive NXDOMAIN.
 *  - All other DNS queries are forwarded over protected UDP sockets to Family-Safe upstream DNS
 *    (Cloudflare Family 1.1.1.3 / AdGuard Family 94.140.14.15), which also enforces SafeSearch.
 */
class SafeDnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile
    private var running = false
    private var readerThread: Thread? = null
    private val workerPool = Executors.newFixedThreadPool(4)
    private val writeLock = Any()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        val store = SafeModeStore(this)
        if (!store.isEnabled || !store.isDnsVpnEnabled) {
            stopVpn()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        if (vpnInterface == null) {
            if (!establishTunnel()) {
                store.isDnsVpnEnabled = false
                stopVpn()
                return START_NOT_STICKY
            }
        }

        if (!running) {
            running = true
            isVpnRunning = true
            readerThread = Thread(::runTunnelLoop, "SafeDnsVpnLoop").apply {
                isDaemon = true
                start()
            }
        }
        return START_STICKY
    }

    private fun establishTunnel(): Boolean {
        return try {
            val builder = Builder()
                .setSession("Islamic Alarm সেফ DNS")
                .setMtu(1500)
                .addAddress("10.120.0.1", 32)
                .addDnsServer("10.120.0.2")
                .addRoute("10.120.0.2", 32)
                .setBlocking(true)

            try {
                builder.addAddress("fd00:120::1", 128)
                    .addDnsServer("fd00:120::2")
                    .addRoute("fd00:120::2", 128)
            } catch (_: Exception) {
                // Carrier or device may not support IPv6 TUN; IPv4 DNS filtering still works cleanly.
            }

            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {
            }

            vpnInterface = builder.establish()
            vpnInterface != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun runTunnelLoop() {
        val pfd = vpnInterface ?: return
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val packet = ByteArray(4096)

        while (running) {
            val len = try {
                input.read(packet)
            } catch (_: Exception) {
                break
            }
            if (len <= 0) {
                if (len < 0) break
                continue
            }

            val packetCopy = packet.copyOf(len)
            val parsed = SafeDnsFilter.parseDnsQueryPacket(packetCopy, len) ?: continue
            workerPool.execute {
                handleDnsQuery(packetCopy, parsed, output)
            }
        }
    }

    private fun handleDnsQuery(
        requestPacket: ByteArray,
        query: SafeDnsFilter.ParsedDnsQuery,
        output: FileOutputStream
    ) {
        if (!running) return
        val store = SafeModeStore(applicationContext)
        val customWords = store.getCustomWords()

        if (SafeDnsFilter.isDomainBlocked(query.queryDomain, customWords)) {
            store.recordDnsBlock(query.queryDomain)
            val blockedPacket = SafeDnsFilter.buildBlockedDnsResponsePacket(requestPacket, query)
            writePacket(output, blockedPacket)
            return
        }

        val dnsQueryBytes = requestPacket.copyOfRange(
            query.dnsPayloadOffset,
            query.dnsPayloadOffset + query.dnsPayloadLength
        )

        for (upstreamIp in SafeDnsFilter.UPSTREAM_DNS_SERVERS) {
            if (!running) return
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protect(socket)
                socket.soTimeout = 2500
                val upstreamAddr = InetAddress.getByName(upstreamIp)
                socket.send(DatagramPacket(dnsQueryBytes, dnsQueryBytes.size, upstreamAddr, 53))

                val respBuf = ByteArray(2048)
                val respPacket = DatagramPacket(respBuf, respBuf.size)
                socket.receive(respPacket)
                if (respPacket.length > 0) {
                    val replyIpPacket = SafeDnsFilter.buildForwardedDnsResponsePacket(
                        requestPacket = requestPacket,
                        query = query,
                        dnsResponsePayload = respBuf,
                        dnsResponseLength = respPacket.length
                    )
                    writePacket(output, replyIpPacket)
                    return
                }
            } catch (_: Exception) {
                // Try next Family DNS upstream
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun writePacket(output: FileOutputStream, data: ByteArray) {
        if (!running) return
        synchronized(writeLock) {
            try {
                output.write(data)
            } catch (_: Exception) {
            }
        }
    }

    override fun onRevoke() {
        SafeModeStore(applicationContext).isDnsVpnEnabled = false
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpnInternal(stopSelfCall = false)
        super.onDestroy()
    }

    private fun stopVpn() {
        stopVpnInternal(stopSelfCall = true)
    }

    private fun stopVpnInternal(stopSelfCall: Boolean) {
        running = false
        isVpnRunning = false
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        try {
            workerPool.shutdownNow()
        } catch (_: Exception) {
        }
        if (stopSelfCall) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (_: Exception) {
            }
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "সেফ মোড DNS ব্লকার",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "সিস্টেম-ওয়াইড অ্যাডাল্ট সাইট ও ইনকগনিটো প্রতিরোধ"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            901,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🛡️ সেফ DNS VPN সক্রিয়")
            .setContentText("সিস্টেম-ওয়াইড অ্যাডাল্ট সাইট ও সেফ সার্চ সুরক্ষা চালু রয়েছে")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.hafij.islamicalarm.ACTION_START_SAFE_DNS_VPN"
        const val ACTION_STOP = "com.hafij.islamicalarm.ACTION_STOP_SAFE_DNS_VPN"
        private const val CHANNEL_ID = "safe_dns_vpn_channel"
        private const val NOTIFICATION_ID = 44210

        @Volatile
        var isVpnRunning: Boolean = false
            private set

        fun start(context: Context): Boolean {
            return try {
                if (prepare(context) != null) return false
                val intent = Intent(context, SafeDnsVpnService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SafeDnsVpnService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
            try {
                context.stopService(Intent(context, SafeDnsVpnService::class.java))
            } catch (_: Exception) {
            }
            isVpnRunning = false
        }
    }
}
