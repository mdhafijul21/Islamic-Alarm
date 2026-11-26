package com.hafij.islamicalarm.auto

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hafij.islamicalarm.AlarmReceiver
import com.hafij.islamicalarm.MainActivity
import com.hafij.islamicalarm.R
import java.util.Calendar
import kotlin.math.sqrt

/**
 * Listens passively during Asr and Isha prayer windows (only when Auto Mode and
 * RECORD_AUDIO permission are enabled) and triggers a 15-minute prayer lock session
 * as soon as sustained Azan audio is heard.
 */
class AzanListenerService : Service() {

    @Volatile
    private var running = false
    private var monitorThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServiceInternal()
            return START_NOT_STICKY
        }

        if (!AutoAlarmManager.isAutoModeEnabled(this) ||
            !AutoAlarmManager.hasRecordAudioPermission(this)
        ) {
            stopServiceInternal()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) {
            running = true
            isServiceRunning = true
            monitorThread = Thread(::runMonitorLoop, "AzanListenerLoop").apply {
                isDaemon = true
                start()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun runMonitorLoop() {
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate)
        val frameSamples = sampleRate / 4 // 250ms frames
        val buffer = ShortArray(frameSamples)

        while (running) {
            if (!AutoAlarmManager.isAutoModeEnabled(this) ||
                !AutoAlarmManager.hasRecordAudioPermission(this)
            ) {
                break
            }

            val now = Calendar.getInstance()
            val district = AutoAlarmManager.loadSavedDistrict(this)
            val window = AutoAlarmManager.findListeningWaqtWindow(now, district)
            if (window == null || AutoAlarmManager.hasTriggeredWaqtOnDate(this, window.waqtKey, now)) {
                sleepSafely(15_000L)
                continue
            }

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    sleepSafely(10_000L)
                    continue
                }
                recorder.startRecording()

                var vocalHits = 0
                var quietStreak = 0
                var framesScanned = 0

                // Scan for up to 30 seconds before re-checking window boundaries
                while (running && framesScanned < 120) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) {
                        sleepSafely(200L)
                        continue
                    }
                    framesScanned++

                    if (isAzanFrameLikelyVocal(buffer, read, sampleRate)) {
                        vocalHits++
                        quietStreak = 0
                    } else {
                        quietStreak++
                        if (quietStreak >= 5) {
                            // Reset if non-Azan silence/noise persists >1.25s so fragmented speech never accumulates
                            vocalHits = 0
                        } else if (quietStreak >= 3) {
                            vocalHits = (vocalHits - 4).coerceAtLeast(0)
                        }
                    }

                    // ~8 seconds of sustained harmonic Azan chanting/calling (32 frames x 250ms)
                    if (vocalHits >= REQUIRED_VOCAL_HITS) {
                        val triggerTime = Calendar.getInstance()
                        val stillInWindow = AutoAlarmManager.findListeningWaqtWindow(triggerTime, district)
                        if (stillInWindow != null &&
                            stillInWindow.waqtKey == window.waqtKey &&
                            !AutoAlarmManager.hasTriggeredWaqtOnDate(this, window.waqtKey, triggerTime)
                        ) {
                            AutoAlarmManager.markWaqtTriggeredOnDate(
                                this,
                                window.waqtKey,
                                triggerTime
                            )
                            val alarmId = "auto_azan_${window.waqtKey}_${System.currentTimeMillis() / 60000L}"
                            mainHandler.post {
                                if (AutoAlarmManager.isAutoModeEnabled(applicationContext)) {
                                    AlarmReceiver.triggerLockSession(
                                        context = applicationContext,
                                        alarmId = alarmId,
                                        lockDurationMinutes = AutoAlarmManager.AZAN_DURATION_MINUTES,
                                        label = window.waqtLabelBn,
                                        fromAlarm = true
                                    )
                                }
                            }
                        }
                        break
                    }
                }
            } catch (_: Exception) {
                sleepSafely(10_000L)
            } finally {
                try {
                    recorder?.stop()
                } catch (_: Exception) {
                }
                try {
                    recorder?.release()
                } catch (_: Exception) {
                }
            }
        }

        mainHandler.post { stopServiceInternal() }
    }

    private fun sleepSafely(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    private fun stopServiceInternal() {
        running = false
        isServiceRunning = false
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
        isServiceRunning = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "অটো আজান শনাক্ত সার্ভিস",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "আসর ও ইশার ওয়াক্তে আজান শুনে ১৫ মিনিটের অটো এলার্ম চালু করে"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            905,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🎙️ অটো আজান শনাক্ত সক্রিয়")
            .setContentText("আসর ও ইশার ওয়াক্তে আজান শুনলেই ১৫ মি. এলার্ম চালু হবে")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.hafij.islamicalarm.ACTION_START_AZAN_LISTENER"
        const val ACTION_STOP = "com.hafij.islamicalarm.ACTION_STOP_AZAN_LISTENER"
        private const val CHANNEL_ID = "azan_listener_channel"
        private const val NOTIFICATION_ID = 44225
        private const val REQUIRED_VOCAL_HITS = 32
        private const val MIN_AZAN_RMS = 1850.0
        private const val MIN_HARMONIC_CORR = 0.62

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        /**
         * Classifies a 16kHz PCM frame as likely carrying sustained melodic Azan vocal energy
         * using DC-removed RMS loudness, voiced vocal-band zero-crossing rate, and normalized
         * lag autocorrelation periodicity (rejects fan hum, traffic, clatter, and casual noise).
         */
        internal fun isAzanFrameLikelyVocal(
            samples: ShortArray,
            count: Int,
            sampleRate: Int = 16000
        ): Boolean {
            if (count <= 32 || sampleRate <= 0) return false

            var sum = 0.0
            for (i in 0 until count) {
                sum += samples[i].toDouble()
            }
            val mean = sum / count

            var sumSq = 0.0
            var zeroCrossings = 0
            var prev = samples[0].toDouble() - mean
            for (i in 0 until count) {
                val centered = samples[i].toDouble() - mean
                sumSq += centered * centered
                if ((prev >= 0.0 && centered < 0.0) || (prev < 0.0 && centered >= 0.0)) {
                    zeroCrossings++
                }
                prev = centered
            }

            val rms = sqrt(sumSq / count)
            if (rms < MIN_AZAN_RMS) return false

            val zcr = zeroCrossings.toDouble() / count
            if (zcr !in 0.025..0.18) return false

            // Check harmonic pitch periodicity across 100 Hz .. 500 Hz fundamental voice range
            val analyzeCount = minOf(count, (sampleRate / 8).coerceAtLeast(512))
            val minLag = (sampleRate / 500).coerceAtLeast(8)
            val maxLag = (sampleRate / 100).coerceAtMost(analyzeCount / 2)
            if (maxLag <= minLag) return false

            var bestCorr = 0.0
            for (lag in minLag..maxLag) {
                val windowLen = analyzeCount - lag
                var dot = 0.0
                var e0 = 0.0
                var e1 = 0.0
                for (i in 0 until windowLen) {
                    val a = samples[i].toDouble() - mean
                    val b = samples[i + lag].toDouble() - mean
                    dot += a * b
                    e0 += a * a
                    e1 += b * b
                }
                if (e0 > 0.0 && e1 > 0.0) {
                    val corr = dot / sqrt(e0 * e1)
                    if (corr > bestCorr) {
                        bestCorr = corr
                        if (bestCorr >= 0.85) break
                    }
                }
            }

            return bestCorr >= MIN_HARMONIC_CORR
        }

        fun start(context: Context): Boolean {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return try {
                val intent = Intent(context, AzanListenerService::class.java).apply {
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
                val intent = Intent(context, AzanListenerService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
            try {
                context.stopService(Intent(context, AzanListenerService::class.java))
            } catch (_: Exception) {
            }
            isServiceRunning = false
        }
    }
}
