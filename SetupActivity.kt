package com.example.gifkeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.gifkeyboard.R

/**
 * Android deliberately does not allow an app to silently enable itself as an
 * input method or silently switch the user onto it — that would be a serious
 * security hole (a malicious app could start capturing keystrokes). So this
 * screen does what every third-party keyboard app does: open the system
 * settings screen and let the user flip it on themselves, then switch input
 * methods via the standard system picker.
 */
class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        findViewById<Button>(R.id.enableButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }
}
