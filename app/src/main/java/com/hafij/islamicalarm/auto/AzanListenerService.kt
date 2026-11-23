package com.hafij.islamicalarm.auto

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hafij.islamicalarm.AlarmReceiver
import com.hafij.islamicalarm.MainActivity
import com.hafij.islamicalarm.PrayerLockOverlayService
import com.hafij.islamicalarm.R
import java.util.Calendar
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Foreground microphone service for Auto Alarm mode.
 *
 * Strictly opens AudioRecord only during Fajr, Asr, Maghrib, and Isha prayer
 * time windows (as calculated for the user's location), detects sustained
 * melodic vocal energy typical of an external Azan broadcast, and launches the
 * 20-minute prayer lock alarm labeled with that specific Waqt.
 */
class AzanListenerService : Service() {

    @Volatile
    private var running = false
    private var workerThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AutoAlarmManager.isAutoModeEnabled(this) ||
            !AutoAlarmManager.hasRecordAudioPermission(this)
        ) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification("অটো আজান শনাক্ত সক্রিয় (ফজর/আসর/মাগরিব/ইশা)")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            // Android 14+ blocks starting microphone FGS from background if not already active
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) {
            running = true
            workerThread = thread(name = "AzanListenerThread", isDaemon = true) {
                listenLoop()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun listenLoop() {
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 2)

        val chunkSamples = sampleRate / 10 // 100 ms chunks
        val buffer = ShortArray(chunkSamples)

        // Sliding window of 80 chunks (8 seconds); need >= 45 vocal chunks (4.5s)
        val windowHistory = BooleanArray(80)
        var historyIdx = 0
        var vocalHitsInWindow = 0

        var audioRecord: AudioRecord? = null

        fun releaseRecorder() {
            try {
                audioRecord?.stop()
            } catch (_: Exception) {
            }
            try {
                audioRecord?.release()
            } catch (_: Exception) {
            }
            audioRecord = null
            windowHistory.fill(false)
            historyIdx = 0
            vocalHitsInWindow = 0
        }

        while (running) {
            if (!AutoAlarmManager.isAutoModeEnabled(this) ||
                !AutoAlarmManager.hasRecordAudioPermission(this)
            ) {
                releaseRecorder()
                break
            }

            if (PrayerLockOverlayService.isOverlayShowing) {
                releaseRecorder()
                sleepQuietly(3000L)
                continue
            }

            val now = Calendar.getInstance()
            val district = AutoAlarmManager.loadSavedDistrict(this)
            val activeWindow = AutoAlarmManager.findListeningWaqtWindow(now, district)

            if (activeWindow == null ||
                AutoAlarmManager.hasTriggeredWaqtOnDate(this, activeWindow.waqtKey, activeWindow.dateKey)
            ) {
                releaseRecorder()
                sleepQuietly(15_000L)
                continue
            }

            // Inside an eligible prayer window and not yet triggered today: listen via AudioRecord
            if (audioRecord == null) {
                try {
                    val rec = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf * 2
                    )
                    if (rec.state == AudioRecord.STATE_INITIALIZED) {
                        rec.startRecording()
                        audioRecord = rec
                    } else {
                        rec.release()
                        sleepQuietly(5000L)
                        continue
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    sleepQuietly(5000L)
                    continue
                }
            }

            val read = try {
                audioRecord?.read(buffer, 0, buffer.size) ?: -1
            } catch (e: Exception) {
                -1
            }

            if (read <= 0) {
                releaseRecorder()
                sleepQuietly(1000L)
                continue
            }

            val isAzanFrame = isSustainedVocalFrame(buffer, read, sampleRate)
            if (windowHistory[historyIdx]) vocalHitsInWindow--
            windowHistory[historyIdx] = isAzanFrame
            if (isAzanFrame) vocalHitsInWindow++
            historyIdx = (historyIdx + 1) % windowHistory.size

            if (vocalHitsInWindow >= REQUIRED_VOCAL_FRAMES) {
                AutoAlarmManager.markWaqtTriggeredOnDate(
                    this,
                    activeWindow.waqtKey,
                    activeWindow.dateKey
                )
                releaseRecorder()
                AlarmReceiver.triggerLockSession(
                    context = applicationContext,
                    alarmId = "auto_azan_${activeWindow.waqtKey}_${activeWindow.dateKey}",
                    lockDurationMinutes = AutoAlarmManager.AUTO_DURATION_MINUTES,
                    label = activeWindow.waqtLabelBn,
                    fromAlarm = true
                )
                sleepQuietly(10_000L)
            }
        }

        releaseRecorder()
        stopForegroundAndSelf()
    }

    /**
     * Checks whether a 100ms audio chunk contains sufficient acoustic energy concentrated
     * in human melodic Azan vocal frequencies (220-900 Hz fundamentals/harmonics) rather
     * than room noise, wind rumble, or broadband static.
     */
    private fun isSustainedVocalFrame(samples: ShortArray, count: Int, sampleRate: Int): Boolean {
        if (count < 256) return false
        var sumSq = 0.0
        var zeroCrossings = 0
        var prev = samples[0].toInt()
        for (i in 0 until count) {
            val s = samples[i].toInt()
            sumSq += s.toDouble() * s.toDouble()
            if ((s >= 0 && prev < 0) || (s < 0 && prev >= 0)) {
                zeroCrossings++
            }
            prev = s
        }
        val rms = sqrt(sumSq / count)
        if (rms < MIN_AZAN_RMS) return false

        // Estimate vocal band energy across 320-sample (20ms) sub-frames (~50Hz bin width)
        // so Azan vocal fundamentals & harmonics between 200-900 Hz do not fall between bins.
        val subLen = minOf(count, 320)
        val subOffset = (count - subLen) / 2
        val vocalFreqs = doubleArrayOf(220.0, 275.0, 330.0, 390.0, 460.0, 540.0, 630.0, 730.0, 840.0)
        var vocalEnergy = 0.0
        for (f in vocalFreqs) {
            vocalEnergy += goertzelPower(samples, subOffset, subLen, sampleRate, f)
        }
        val rumbleEnergy = goertzelPower(samples, subOffset, subLen, sampleRate, 70.0)
        var subSumSq = 0.0
        for (i in subOffset until subOffset + subLen) {
            val v = samples[i].toDouble()
            subSumSq += v * v
        }

        val zcrPerSec = (zeroCrossings.toDouble() * sampleRate) / count
        val isTonalRate = zcrPerSec in 250.0..2600.0
        val vocalRatio = vocalEnergy / (subSumSq + 1.0)
        return isTonalRate && vocalRatio > 0.14 && vocalEnergy > rumbleEnergy * 2.0
    }

    private fun goertzelPower(
        samples: ShortArray,
        offset: Int,
        count: Int,
        sampleRate: Int,
        targetHz: Double
    ): Double {
        val omega = 2.0 * PI * targetHz / sampleRate
        val coeff = 2.0 * cos(omega)
        var sPrev = 0.0
        var sPrev2 = 0.0
        val end = offset + count
        for (i in offset until end) {
            val s = samples[i].toDouble() + coeff * sPrev - sPrev2
            sPrev2 = sPrev
            sPrev = s
        }
        return (sPrev2 * sPrev2 + sPrev * sPrev - coeff * sPrev * sPrev2) / count.coerceAtLeast(1)
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "অটো আজান শনাক্ত সার্ভিস",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            this,
            9912,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ইসলামিক অ্যালার্ম — অটো মোড চালু")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopForegroundAndSelf() {
        running = false
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

    override fun onDestroy() {
        running = false
        workerThread?.interrupt()
        workerThread = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "auto_azan_listener_silent"
        private const val NOTIFICATION_ID = 4120
        private const val MIN_AZAN_RMS = 900.0
        private const val REQUIRED_VOCAL_FRAMES = 45 // 4.5s inside 8s sliding window

        fun start(context: Context, fromForeground: Boolean = true): Boolean {
            return try {
                val intent = Intent(context, AzanListenerService::class.java)
                if (fromForeground || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    ContextCompat.startForegroundService(context, intent)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AzanListenerService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
