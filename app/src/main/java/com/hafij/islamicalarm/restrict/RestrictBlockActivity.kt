package com.hafij.islamicalarm.restrict

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.hafij.islamicalarm.databinding.ActivityRestrictBlockBinding
import java.util.Locale

/** Full-screen "break time" screen shown when a blocked app is opened. */
class RestrictBlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRestrictBlockBinding
    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { RestrictStore(applicationContext) }
    private var pkg: String = ""

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestrictBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        binding.btnRestrictHome.setOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
        refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: pkg
        refresh()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun refresh() {
        val end = store.blockEndFor(pkg, System.currentTimeMillis())
        if (end <= 0L) {
            // The break is over: close, the app behind works as before.
            finish()
            return
        }
        binding.tvRestrictApp.text = appLabel() + " এখন বন্ধ"
        binding.tvRestrictRemaining.text = formatRemaining(end - System.currentTimeMillis())
    }

    private fun appLabel(): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun formatRemaining(millis: Long): String {
        val total = if (millis < 0L) 0L else millis / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ENGLISH, "%02d:%02d", m, s)
        }
    }

    private fun goHome() {
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(home)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "restrict_blocked_package"
    }
}
