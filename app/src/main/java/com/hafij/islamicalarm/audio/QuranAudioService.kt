package com.hafij.islamicalarm.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.quran.QuranRepository
import com.hafij.islamicalarm.quran.Surah
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

data class QuranAudioState(
    val surah: Surah? = null,
    val reciter: Reciter = ReciterData.getDefaultReciter(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isTtsMode: Boolean = false,
    val isRepeatOne: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val ttsAyahIndex: Int = 0,
    val ttsTotalAyahs: Int = 0,
    val statusText: String = "",
    val missingOfflinePromptSurahId: Int? = null
)

/**
 * Foreground Media Service for background Quran recitation with fast OkHttp multi-mirror
 * progressive streaming, MediaSession integration, and MediaStyle notification controls
 * (Previous | Play/Pause | Next).
 */
class QuranAudioService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    private var activeDataSource: ProgressiveStreamDataSource? = null
    private var streamJob: Job? = null
    private var playbackSessionToken = 0L

    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsArabicAvailable = false
    private var ttsAyahs: List<String> = emptyList()
    private var ttsAyahIndex = 0
    private var pendingTtsSurah: Surah? = null

    private var isRepeatOne = false
    private var noisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausePlayback()
            }
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pausePlayback()
        }
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            val current = _stateFlow.value
            if (mp != null && current.isPlaying && !current.isTtsMode) {
                try {
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition.coerceAtLeast(0)
                        val dur = mp.duration.coerceAtLeast(0)
                        _stateFlow.value = _stateFlow.value.copy(
                            positionMs = pos,
                            durationMs = dur
                        )
                        updateMediaSessionPlaybackState(true, false, pos.toLong())
                    }
                } catch (_: Exception) {
                }
                mainHandler.postDelayed(this, 500L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ensureNotificationChannel()
        initMediaSession()
        initTts()
        registerNoisyReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_SURAH -> {
                val surahId = intent.getIntExtra(EXTRA_SURAH_ID, 1)
                val reciterId = intent.getStringExtra(EXTRA_RECITER_ID)
                    ?: _stateFlow.value.reciter.id
                val surah = QuranRepository.surahList.find { it.id == surahId } ?: return START_NOT_STICKY
                val reciter = ReciterData.reciters.find { it.id == reciterId } ?: ReciterData.getDefaultReciter()
                playSurah(surah, reciter)
            }
            ACTION_TOGGLE_PLAY_PAUSE -> {
                val st = _stateFlow.value
                if (st.surah == null) return START_NOT_STICKY
                if (st.isPlaying || st.isBuffering) {
                    pausePlayback()
                } else {
                    resumePlayback()
                }
            }
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_NEXT -> playNextSurah()
            ACTION_PREV -> playPreviousSurah()
            ACTION_STOP -> stopEverythingAndShutdown()
            ACTION_SEEK -> {
                val posMs = intent.getIntExtra(EXTRA_SEEK_POS_MS, -1)
                if (posMs >= 0) seekToPosition(posMs)
            }
            ACTION_SET_REPEAT -> {
                isRepeatOne = intent.getBooleanExtra(EXTRA_REPEAT_ONE, false)
                _stateFlow.value = _stateFlow.value.copy(isRepeatOne = isRepeatOne)
            }
            ACTION_CHANGE_RECITER -> {
                val reciterId = intent.getStringExtra(EXTRA_RECITER_ID) ?: return START_NOT_STICKY
                val reciter = ReciterData.reciters.find { it.id == reciterId } ?: return START_NOT_STICKY
                val current = _stateFlow.value
                _stateFlow.value = current.copy(reciter = reciter)
                if (current.surah != null && (current.isPlaying || current.isBuffering)) {
                    playSurah(current.surah, reciter)
                }
            }
            ACTION_CLEAR_MISSING_PROMPT -> {
                _stateFlow.value = _stateFlow.value.copy(missingOfflinePromptSurahId = null)
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "কুরআন তিলাওয়াত অডিও",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ব্যাকগ্রাউন্ডে কুরআন তিলাওয়াত নিয়ন্ত্রণ"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "IslamicAlarmQuranAudio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    resumePlayback()
                }

                override fun onPause() {
                    pausePlayback()
                }

                override fun onSkipToNext() {
                    playNextSurah()
                }

                override fun onSkipToPrevious() {
                    playPreviousSurah()
                }

                override fun onSeekTo(pos: Long) {
                    seekToPosition(pos.toInt())
                }

                override fun onStop() {
                    stopEverythingAndShutdown()
                }
            })
            isActive = true
        }
    }

    private fun registerNoisyReceiver() {
        if (!noisyReceiverRegistered) {
            try {
                registerReceiver(
                    noisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
                noisyReceiverRegistered = true
            } catch (_: Exception) {
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) {
            true
        }
    }

    private fun abandonAudioFocus() {
        try {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {
        }
        audioFocusRequest = null
    }

    private fun playSurah(surah: Surah, reciter: Reciter) {
        releaseMediaOnly()
        playbackSessionToken++
        val token = playbackSessionToken

        requestAudioFocus()
        mediaSession.isActive = true

        val localFile = AudioSurahAdapter.resolveLocalAudioFile(this, reciter.id, surah.id)
        val cachedStream = resolvedStreamCacheFile(this, reciter.id, surah.id)
            .takeIf { it.exists() && it.length() > 16 * 1024 }

        when {
            localFile != null -> {
                _stateFlow.value = QuranAudioState(
                    surah = surah,
                    reciter = reciter,
                    isPlaying = false,
                    isBuffering = true,
                    isTtsMode = false,
                    isRepeatOne = isRepeatOne,
                    statusText = "${reciter.nameBn} (অফলাইনে সংরক্ষিত ✅)"
                )
                promoteToForegroundOrNotify()
                startLocalMediaPlayer(surah, reciter, localFile, token, isPermanentOffline = true)
            }
            cachedStream != null -> {
                _stateFlow.value = QuranAudioState(
                    surah = surah,
                    reciter = reciter,
                    isPlaying = false,
                    isBuffering = true,
                    isTtsMode = false,
                    isRepeatOne = isRepeatOne,
                    statusText = reciter.nameBn
                )
                promoteToForegroundOrNotify()
                startLocalMediaPlayer(surah, reciter, cachedStream, token, isPermanentOffline = false)
            }
            hasInternetConnection() -> {
                _stateFlow.value = QuranAudioState(
                    surah = surah,
                    reciter = reciter,
                    isPlaying = false,
                    isBuffering = true,
                    isTtsMode = false,
                    isRepeatOne = isRepeatOne,
                    statusText = "${reciter.nameBn} • দ্রুত চালু হচ্ছে..."
                )
                promoteToForegroundOrNotify()
                startFastRacingStream(surah, reciter, token)
            }
            else -> {
                _stateFlow.value = QuranAudioState(
                    surah = surah,
                    reciter = reciter,
                    isPlaying = true,
                    isBuffering = false,
                    isTtsMode = true,
                    isRepeatOne = isRepeatOne,
                    statusText = "অফলাইন আরবি ভয়েস তিলাওয়াত লোড হচ্ছে..."
                )
                promoteToForegroundOrNotify()
                playTtsRecitation(surah, resetIndex = true)
            }
        }
    }

    private fun startLocalMediaPlayer(
        surah: Surah,
        reciter: Reciter,
        file: File,
        token: Long,
        isPermanentOffline: Boolean
    ) {
        try {
            val fis = FileInputStream(file)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(fis.fd)
                fis.close()
                setOnPreparedListener { readyMp ->
                    if (token != playbackSessionToken) {
                        readyMp.release()
                        return@setOnPreparedListener
                    }
                    readyMp.start()
                    _stateFlow.value = _stateFlow.value.copy(
                        surah = surah,
                        reciter = reciter,
                        isPlaying = true,
                        isBuffering = false,
                        isTtsMode = false,
                        positionMs = 0,
                        durationMs = readyMp.duration.coerceAtLeast(0),
                        statusText = if (isPermanentOffline) {
                            "${reciter.nameBn} (অফলাইনে সংরক্ষিত ✅)"
                        } else {
                            reciter.nameBn
                        }
                    )
                    updateMetadataAndPlaybackState()
                    promoteToForegroundOrNotify()
                    mainHandler.removeCallbacks(progressRunnable)
                    mainHandler.post(progressRunnable)
                }
                setOnCompletionListener {
                    if (token != playbackSessionToken) return@setOnCompletionListener
                    onSurahCompleted(surah, reciter)
                }
                setOnErrorListener { _, _, _ ->
                    if (token != playbackSessionToken) return@setOnErrorListener true
                    if (!isPermanentOffline) {
                        file.delete()
                        if (hasInternetConnection()) {
                            startFastRacingStream(surah, reciter, token)
                            return@setOnErrorListener true
                        }
                    }
                    playTtsRecitation(surah, resetIndex = true)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (_: Exception) {
            if (hasInternetConnection() && !isPermanentOffline) {
                startFastRacingStream(surah, reciter, token)
            } else {
                playTtsRecitation(surah, resetIndex = true)
            }
        }
    }

    private fun startFastRacingStream(surah: Surah, reciter: Reciter, token: Long) {
        streamJob?.cancel()
        val urls = reciter.getAllPlaybackUrls(surah.id)
        val cacheDir = File(cacheDir, "quran_stream").apply { if (!exists()) mkdirs() }
        val partialFile = File(cacheDir, "${reciter.id}_${surah.id}.mp3.part")
        val finalCacheFile = resolvedStreamCacheFile(this, reciter.id, surah.id)

        streamJob = serviceScope.launch(Dispatchers.IO) {
            var winningResponse: Response? = null
            var winningUrl: String? = null

            // Race all mirrors concurrently; first HTTP 200 non-empty body wins.
            val winnerDeferred = CompletableDeferred<Pair<String, Response>?>()
            val racers = urls.map { url ->
                launch {
                    try {
                        val req = Request.Builder().url(url).get().build()
                        val resp = httpClient.newCall(req).execute()
                        if (resp.isSuccessful && resp.body != null && (resp.body?.contentLength() ?: 1L) != 0L) {
                            if (!winnerDeferred.complete(url to resp)) {
                                resp.close()
                            }
                        } else {
                            resp.close()
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            launch {
                racers.forEach { it.join() }
                winnerDeferred.complete(null)
            }

            val winner = winnerDeferred.await()
            racers.forEach { if (it.isActive) it.cancel() }

            if (winner == null || !isActive || token != playbackSessionToken) {
                mainHandler.post {
                    if (token == playbackSessionToken) {
                        playTtsRecitation(surah, resetIndex = true)
                    }
                }
                return@launch
            }

            winningUrl = winner.first
            winningResponse = winner.second
            val body = winningResponse.body ?: run {
                winningResponse.close()
                return@launch
            }
            val contentLength = body.contentLength()

            try {
                if (partialFile.exists()) partialFile.delete()
                val dataSource = ProgressiveStreamDataSource(
                    partialFile = partialFile,
                    contentLength = contentLength,
                    streamUrl = winningUrl,
                    httpClient = httpClient
                )
                activeDataSource = dataSource

                val buffer = ByteArray(16 * 1024)
                var launchedPlayer = false
                body.byteStream().use { input ->
                    RandomAccessFile(partialFile, "rw").use { out ->
                        var totalRead = 0L
                        while (isActive && token == playbackSessionToken) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            totalRead += read
                            dataSource.onBytesWritten(totalRead)

                            // Start MediaPlayer as soon as initial ~24 KB arrives (~0.2-0.5s)
                            if (!launchedPlayer && totalRead >= 24 * 1024L) {
                                launchedPlayer = true
                                mainHandler.post {
                                    if (token == playbackSessionToken) {
                                        startMediaPlayerWithDataSource(
                                            surah = surah,
                                            reciter = reciter,
                                            dataSource = dataSource,
                                            fallbackDirectUrl = winningUrl,
                                            token = token
                                        )
                                    }
                                }
                            }
                        }
                        out.fd.sync()
                    }
                }
                dataSource.markCompleted()

                // Very short surahs (<24KB total) finish reading in one shot: launch if not started yet
                if (!launchedPlayer && token == playbackSessionToken) {
                    launchedPlayer = true
                    mainHandler.post {
                        if (token == playbackSessionToken) {
                            startMediaPlayerWithDataSource(
                                surah = surah,
                                reciter = reciter,
                                dataSource = dataSource,
                                fallbackDirectUrl = winningUrl,
                                token = token
                            )
                        }
                    }
                }

                // Promote complete stream to cache for instant future seeks/replays
                if (token == playbackSessionToken && partialFile.length() > 4096) {
                    if (contentLength <= 0 || partialFile.length() >= contentLength) {
                        partialFile.copyTo(finalCacheFile, overwrite = true)
                    }
                }
            } catch (_: Exception) {
                activeDataSource?.markCompleted()
                if (token == playbackSessionToken) {
                    mainHandler.post {
                        if (mediaPlayer == null && token == playbackSessionToken) {
                            startMediaPlayerDirectUrl(surah, reciter, winningUrl, token)
                        }
                    }
                }
            } finally {
                try {
                    winningResponse.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun startMediaPlayerWithDataSource(
        surah: Surah,
        reciter: Reciter,
        dataSource: ProgressiveStreamDataSource,
        fallbackDirectUrl: String,
        token: Long
    ) {
        try {
            mediaPlayer?.release()
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(dataSource)
                setOnPreparedListener { readyMp ->
                    if (token != playbackSessionToken) {
                        readyMp.release()
                        return@setOnPreparedListener
                    }
                    readyMp.start()
                    _stateFlow.value = _stateFlow.value.copy(
                        surah = surah,
                        reciter = reciter,
                        isPlaying = true,
                        isBuffering = false,
                        isTtsMode = false,
                        positionMs = 0,
                        durationMs = readyMp.duration.coerceAtLeast(0),
                        statusText = reciter.nameBn
                    )
                    updateMetadataAndPlaybackState()
                    promoteToForegroundOrNotify()
                    mainHandler.removeCallbacks(progressRunnable)
                    mainHandler.post(progressRunnable)
                }
                setOnCompletionListener {
                    if (token != playbackSessionToken) return@setOnCompletionListener
                    onSurahCompleted(surah, reciter)
                }
                setOnErrorListener { _, _, _ ->
                    if (token != playbackSessionToken) return@setOnErrorListener true
                    startMediaPlayerDirectUrl(surah, reciter, fallbackDirectUrl, token)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (_: Exception) {
            startMediaPlayerDirectUrl(surah, reciter, fallbackDirectUrl, token)
        }
    }

    private fun startMediaPlayerDirectUrl(
        surah: Surah,
        reciter: Reciter,
        url: String,
        token: Long
    ) {
        try {
            mediaPlayer?.release()
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { readyMp ->
                    if (token != playbackSessionToken) {
                        readyMp.release()
                        return@setOnPreparedListener
                    }
                    readyMp.start()
                    _stateFlow.value = _stateFlow.value.copy(
                        surah = surah,
                        reciter = reciter,
                        isPlaying = true,
                        isBuffering = false,
                        isTtsMode = false,
                        positionMs = 0,
                        durationMs = readyMp.duration.coerceAtLeast(0),
                        statusText = reciter.nameBn
                    )
                    updateMetadataAndPlaybackState()
                    promoteToForegroundOrNotify()
                    mainHandler.removeCallbacks(progressRunnable)
                    mainHandler.post(progressRunnable)
                }
                setOnCompletionListener {
                    if (token != playbackSessionToken) return@setOnCompletionListener
                    onSurahCompleted(surah, reciter)
                }
                setOnErrorListener { _, _, _ ->
                    if (token != playbackSessionToken) return@setOnErrorListener true
                    playTtsRecitation(surah, resetIndex = true)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (_: Exception) {
            playTtsRecitation(surah, resetIndex = true)
        }
    }

    private fun onSurahCompleted(surah: Surah, reciter: Reciter) {
        if (isRepeatOne) {
            playSurah(surah, reciter)
        } else {
            playNextSurah()
        }
    }

    private fun playTtsRecitation(surah: Surah, resetIndex: Boolean) {
        if (ttsReady && !ttsArabicAvailable) {
            _stateFlow.value = _stateFlow.value.copy(
                surah = surah,
                isPlaying = false,
                isBuffering = false,
                isTtsMode = false,
                statusText = "অফলাইন আরবি ভয়েস ফোনে পাওয়া যায়নি",
                missingOfflinePromptSurahId = surah.id
            )
            updateMetadataAndPlaybackState()
            updateNotificationOnly()
            return
        }

        if (!ttsReady) {
            pendingTtsSurah = surah
        }

        val token = playbackSessionToken
        if (resetIndex || ttsAyahs.isEmpty()) {
            ttsAyahIndex = 0
            serviceScope.launch {
                val ayahs = QuranRepository.getOfflineArabicSurahAyahs(this@QuranAudioService, surah.id)
                    .map { it.textArabic }
                    .ifEmpty { listOf("سورة ${surah.nameArabic}. بسم الله الرحمن الرحيم") }
                if (token != playbackSessionToken || _stateFlow.value.surah?.id != surah.id) return@launch
                ttsAyahs = ayahs
                _stateFlow.value = _stateFlow.value.copy(
                    isTtsMode = true,
                    isPlaying = true,
                    isBuffering = false,
                    ttsAyahIndex = 0,
                    ttsTotalAyahs = ayahs.size
                )
                if (ttsReady && ttsArabicAvailable) {
                    speakCurrentTtsAyah(surah, token)
                } else if (ttsReady) {
                    _stateFlow.value = _stateFlow.value.copy(
                        isPlaying = false,
                        isTtsMode = false,
                        statusText = "অফলাইন আরবি ভয়েস ফোনে পাওয়া যায়নি",
                        missingOfflinePromptSurahId = surah.id
                    )
                    updateNotificationOnly()
                }
            }
        } else {
            _stateFlow.value = _stateFlow.value.copy(
                isTtsMode = true,
                isPlaying = true,
                isBuffering = false
            )
            if (ttsReady && ttsArabicAvailable) {
                speakCurrentTtsAyah(surah, token)
            }
        }
    }

    private fun speakCurrentTtsAyah(surah: Surah, token: Long) {
        val current = _stateFlow.value
        if (!current.isTtsMode || !current.isPlaying || current.surah?.id != surah.id || token != playbackSessionToken) {
            return
        }
        val total = ttsAyahs.size.coerceAtLeast(1)
        if (ttsAyahIndex >= total) {
            if (isRepeatOne) {
                ttsAyahIndex = 0
            } else {
                playNextSurah()
                return
            }
        }

        val idx = ttsAyahIndex
        val ayahText = ttsAyahs[idx]
        _stateFlow.value = _stateFlow.value.copy(
            ttsAyahIndex = idx,
            ttsTotalAyahs = total,
            statusText = "অফলাইন আরবি ভয়েস তিলাওয়াত (${idx + 1}/$total)"
        )
        updateMetadataAndPlaybackState()
        promoteToForegroundOrNotify()

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (token == playbackSessionToken && _stateFlow.value.isPlaying && _stateFlow.value.isTtsMode) {
                        ttsAyahIndex++
                        speakCurrentTtsAyah(surah, token)
                    }
                }
            }
            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    if (token == playbackSessionToken && _stateFlow.value.isPlaying && _stateFlow.value.isTtsMode) {
                        ttsAyahIndex++
                        speakCurrentTtsAyah(surah, token)
                    }
                }
            }
        })
        tts?.speak(ayahText, TextToSpeech.QUEUE_FLUSH, null, "QURAN_TTS_${surah.id}_$idx")
    }

    private fun pausePlayback() {
        val st = _stateFlow.value
        if (!st.isPlaying && !st.isBuffering) return
        mainHandler.removeCallbacks(progressRunnable)
        if (st.isTtsMode) {
            tts?.stop()
        } else {
            try {
                if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
            } catch (_: Exception) {
            }
        }
        _stateFlow.value = st.copy(isPlaying = false, isBuffering = false)
        updateMediaSessionPlaybackState(false, false, _stateFlow.value.positionMs.toLong())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        }
        updateNotificationOnly()
    }

    private fun resumePlayback() {
        val st = _stateFlow.value
        val surah = st.surah ?: return
        requestAudioFocus()
        if (st.isTtsMode) {
            _stateFlow.value = st.copy(isPlaying = true)
            speakCurrentTtsAyah(surah, playbackSessionToken)
        } else if (mediaPlayer != null) {
            try {
                mediaPlayer?.start()
                _stateFlow.value = st.copy(isPlaying = true, isBuffering = false)
                updateMetadataAndPlaybackState()
                promoteToForegroundOrNotify()
                mainHandler.removeCallbacks(progressRunnable)
                mainHandler.post(progressRunnable)
            } catch (_: Exception) {
                playSurah(surah, st.reciter)
            }
        } else {
            playSurah(surah, st.reciter)
        }
    }

    private fun seekToPosition(posMs: Int) {
        val st = _stateFlow.value
        if (st.isTtsMode) return
        try {
            mediaPlayer?.let { mp ->
                if (mp.duration > 0) {
                    val safePos = posMs.coerceIn(0, mp.duration)
                    mp.seekTo(safePos)
                    _stateFlow.value = st.copy(positionMs = safePos)
                    updateMediaSessionPlaybackState(st.isPlaying, st.isBuffering, safePos.toLong())
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun playNextSurah() {
        val current = _stateFlow.value
        val currentId = current.surah?.id ?: 0
        val nextId = if (currentId < 114) currentId + 1 else 1
        QuranRepository.surahList.find { it.id == nextId }?.let {
            playSurah(it, current.reciter)
        }
    }

    private fun playPreviousSurah() {
        val current = _stateFlow.value
        val currentId = current.surah?.id ?: 2
        val prevId = if (currentId > 1) currentId - 1 else 114
        QuranRepository.surahList.find { it.id == prevId }?.let {
            playSurah(it, current.reciter)
        }
    }

    private fun stopEverythingAndShutdown() {
        releaseMediaOnly()
        abandonAudioFocus()
        mediaSession.isActive = false
        _stateFlow.value = QuranAudioState(
            reciter = _stateFlow.value.reciter,
            isRepeatOne = isRepeatOne
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun releaseMediaOnly() {
        mainHandler.removeCallbacks(progressRunnable)
        streamJob?.cancel()
        streamJob = null
        activeDataSource?.markCompleted()
        activeDataSource = null
        pendingTtsSurah = null
        ttsAyahs = emptyList()
        ttsAyahIndex = 0
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
    }

    private fun updateMetadataAndPlaybackState() {
        val st = _stateFlow.value
        val surah = st.surah ?: return
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "সুরা ${surah.id}: ${surah.nameBangla} (${surah.nameArabic})")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, st.statusText.ifBlank { st.reciter.nameBn })
            .putLong(MediaMetadata.METADATA_KEY_DURATION, st.durationMs.toLong())
            .build()
        mediaSession.setMetadata(metadata)
        updateMediaSessionPlaybackState(st.isPlaying, st.isBuffering, st.positionMs.toLong())
    }

    private fun updateMediaSessionPlaybackState(isPlaying: Boolean, isBuffering: Boolean, positionMs: Long) {
        val stateCode = when {
            isBuffering -> PlaybackState.STATE_BUFFERING
            isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_STOP

        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(stateCode, positionMs, if (isPlaying) 1.0f else 0.0f)
                .build()
        )
    }

    private fun promoteToForegroundOrNotify() {
        val st = _stateFlow.value
        if (st.surah == null) return
        val notification = buildPlaybackNotification(this, st, mediaSession.sessionToken)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            updateNotificationOnly()
        }
    }

    private fun updateNotificationOnly() {
        val st = _stateFlow.value
        if (st.surah == null) return
        val notification = buildPlaybackNotification(this, st, mediaSession.sessionToken)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    private fun hasInternetConnection(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = true
            if (status == TextToSpeech.SUCCESS) {
                var result = tts?.setLanguage(Locale("ar"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts?.setLanguage(Locale("ar", "SA")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                }
                ttsArabicAvailable = (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED)
            } else {
                ttsArabicAvailable = false
            }

            pendingTtsSurah?.let { surah ->
                pendingTtsSurah = null
                val st = _stateFlow.value
                if (st.isTtsMode && st.surah?.id == surah.id) {
                    if (ttsArabicAvailable && ttsAyahs.isNotEmpty()) {
                        speakCurrentTtsAyah(surah, playbackSessionToken)
                    } else if (!ttsArabicAvailable) {
                        _stateFlow.value = st.copy(
                            isPlaying = false,
                            isTtsMode = false,
                            statusText = "অফলাইন আরবি ভয়েস ফোনে পাওয়া যায়নি",
                            missingOfflinePromptSurahId = surah.id
                        )
                        updateNotificationOnly()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        releaseMediaOnly()
        abandonAudioFocus()
        if (noisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {
            }
            noisyReceiverRegistered = false
        }
        try {
            mediaSession.release()
        } catch (_: Exception) {
        }
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Progressive disk-backed MediaDataSource that starts playback immediately once the
     * initial ~24 KB arrives while continuing to cache the stream sequentially, and handles
     * out-of-order end-of-file metadata probes via fast single-chunk HTTP Range requests.
     */
    private class ProgressiveStreamDataSource(
        private val partialFile: File,
        private val contentLength: Long,
        private val streamUrl: String,
        private val httpClient: OkHttpClient
    ) : MediaDataSource() {

        private val bytesWritten = AtomicLong(0L)
        private val completed = AtomicBoolean(false)
        private val lock = Object()
        private var readerRaf: RandomAccessFile? = null

        fun onBytesWritten(total: Long) {
            synchronized(lock) {
                bytesWritten.set(total)
                lock.notifyAll()
            }
        }

        fun markCompleted() {
            synchronized(lock) {
                completed.set(true)
                lock.notifyAll()
            }
        }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position < 0 || size <= 0) return 0
            if (contentLength > 0 && position >= contentLength) return -1

            val maxEnd = if (contentLength > 0) min(position + size, contentLength) else position + size
            val needed = (maxEnd - position).toInt().coerceAtMost(size)
            if (needed <= 0) return -1

            // Wait briefly if position is right at the sequential download frontier
            if (bytesWritten.get() < maxEnd && !completed.get()) {
                if (contentLength > 0 && position > bytesWritten.get() + 256L * 1024L) {
                    // Far jump (e.g., EOF ID3v1 / VBR tag probe or user scrub before download caught up)
                    val ranged = fetchRangeChunk(position, needed)
                    if (ranged != null) {
                        System.arraycopy(ranged, 0, buffer, offset, ranged.size)
                        return ranged.size
                    }
                    return 0
                }
                val deadline = System.currentTimeMillis() + 6000L
                synchronized(lock) {
                    while (bytesWritten.get() < maxEnd && !completed.get()) {
                        val remain = deadline - System.currentTimeMillis()
                        if (remain <= 0) break
                        try {
                            lock.wait(min(remain, 50L))
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }

            val availTotal = bytesWritten.get()
            if (position >= availTotal) {
                return if (completed.get()) -1 else 0
            }

            return synchronized(this) {
                if (readerRaf == null && partialFile.exists()) {
                    readerRaf = RandomAccessFile(partialFile, "r")
                }
                val raf = readerRaf ?: return -1
                raf.seek(position)
                val toRead = min(needed.toLong(), availTotal - position).toInt()
                val read = raf.read(buffer, offset, toRead)
                if (read < 0 && completed.get()) -1 else read.coerceAtLeast(0)
            }
        }

        private fun fetchRangeChunk(position: Long, size: Int): ByteArray? {
            return try {
                val end = if (contentLength > 0) min(position + size - 1, contentLength - 1) else position + size - 1
                val req = Request.Builder()
                    .url(streamUrl)
                    .header("Range", "bytes=$position-$end")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.code == 206 || resp.isSuccessful) {
                        val bytes = resp.body?.bytes() ?: return null
                        if (bytes.size > size) bytes.copyOf(size) else bytes
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

        override fun getSize(): Long = if (contentLength > 0) contentLength else -1L

        override fun close() {
            synchronized(this) {
                try {
                    readerRaf?.close()
                } catch (_: Exception) {
                }
                readerRaf = null
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "quran_audio_playback"
        const val NOTIFICATION_ID = 7011

        const val ACTION_PLAY_SURAH = "com.hafij.islamicalarm.audio.PLAY_SURAH"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.hafij.islamicalarm.audio.TOGGLE_PLAY_PAUSE"
        const val ACTION_PAUSE = "com.hafij.islamicalarm.audio.PAUSE"
        const val ACTION_RESUME = "com.hafij.islamicalarm.audio.RESUME"
        const val ACTION_NEXT = "com.hafij.islamicalarm.audio.NEXT"
        const val ACTION_PREV = "com.hafij.islamicalarm.audio.PREV"
        const val ACTION_STOP = "com.hafij.islamicalarm.audio.STOP"
        const val ACTION_SEEK = "com.hafij.islamicalarm.audio.SEEK"
        const val ACTION_SET_REPEAT = "com.hafij.islamicalarm.audio.SET_REPEAT"
        const val ACTION_CHANGE_RECITER = "com.hafij.islamicalarm.audio.CHANGE_RECITER"
        const val ACTION_CLEAR_MISSING_PROMPT = "com.hafij.islamicalarm.audio.CLEAR_MISSING_PROMPT"

        const val EXTRA_SURAH_ID = "extra_surah_id"
        const val EXTRA_RECITER_ID = "extra_reciter_id"
        const val EXTRA_SEEK_POS_MS = "extra_seek_pos_ms"
        const val EXTRA_REPEAT_ONE = "extra_repeat_one"

        private val _stateFlow = MutableStateFlow(QuranAudioState())
        val stateFlow: StateFlow<QuranAudioState> = _stateFlow.asStateFlow()

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        fun resolvedStreamCacheFile(context: Context, reciterId: String, surahId: Int): File {
            val cacheDir = File(context.cacheDir, "quran_stream").apply { if (!exists()) mkdirs() }
            return File(cacheDir, "${reciterId}_${surahId}.mp3")
        }

        fun dispatchAction(
            context: Context,
            action: String,
            promoteForeground: Boolean = false,
            configureIntent: (Intent.() -> Unit)? = null
        ) {
            val intent = Intent(context, QuranAudioService::class.java).apply {
                this.action = action
                configureIntent?.invoke(this)
            }
            if (promoteForeground) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun buildPlaybackNotification(
            context: Context,
            state: QuranAudioState,
            sessionToken: MediaSession.Token? = null
        ): Notification {
            val surah = state.surah
            val title = if (surah != null) {
                "সুরা ${surah.id}: ${surah.nameBangla} (${surah.nameArabic})"
            } else {
                "কুরআন তিলাওয়াত"
            }
            val subtitle = state.statusText.ifBlank { state.reciter.nameBn }

            val openIntent = PendingIntent.getActivity(
                context,
                100,
                Intent(context, AudioQuranActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevPi = PendingIntent.getService(
                context,
                101,
                Intent(context, QuranAudioService::class.java).setAction(ACTION_PREV),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val playPausePi = PendingIntent.getService(
                context,
                102,
                Intent(context, QuranAudioService::class.java).setAction(ACTION_TOGGLE_PLAY_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextPi = PendingIntent.getService(
                context,
                103,
                Intent(context, QuranAudioService::class.java).setAction(ACTION_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stopPi = PendingIntent.getService(
                context,
                104,
                Intent(context, QuranAudioService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevAction = Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                "আগের সুরা",
                prevPi
            ).build()

            val isActiveOrLoading = state.isPlaying || state.isBuffering
            val playPauseAction = Notification.Action.Builder(
                if (isActiveOrLoading) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isActiveOrLoading) "বিরতি" else "চালু করুন",
                playPausePi
            ).build()

            val nextAction = Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                "পরের সুরা",
                nextPi
            ).build()

            val mediaStyle = Notification.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
            if (sessionToken != null) {
                mediaStyle.setMediaSession(sessionToken)
            }

            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_quran)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setContentIntent(openIntent)
                .setDeleteIntent(stopPi)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(isActiveOrLoading)
                .setOnlyAlertOnce(true)
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .setStyle(mediaStyle)
                .build()
        }
    }
}
