package com.hafij.islamicalarm.restrict

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.hafij.islamicalarm.databinding.ActivitySafeBlockBinding

/** Shown when Safe mode stops an adult search. */
class SafeBlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySafeBlockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySafeBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSafeHome.setOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
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
}
