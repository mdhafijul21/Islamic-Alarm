package com.hafij.islamicalarm

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var navigated = false
    private val goToMainRunnable = Runnable { openMain() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val root = findViewById<View>(R.id.splashRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                sysBars.top + 32,
                v.paddingRight,
                sysBars.bottom + 32
            )
            insets
        }
        root.setOnClickListener { openMain() }

        handler.postDelayed(goToMainRunnable, 1600L)
    }

    private fun openMain() {
        if (navigated || isFinishing) return
        navigated = true
        handler.removeCallbacks(goToMainRunnable)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(goToMainRunnable)
        super.onDestroy()
    }
}
