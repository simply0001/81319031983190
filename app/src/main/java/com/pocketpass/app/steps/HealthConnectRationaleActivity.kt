package com.pocketpass.app.steps

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class HealthConnectRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.WHITE)
            addView(TextView(context).apply {
                text = "PocketPass Step Rewards"
                textSize = 24f
                setTextColor(Color.rgb(29, 89, 107))
            })
            addView(TextView(context).apply {
                text = "If you turn on Step Rewards and allow Health Connect access, " +
                    "PocketPass reads today's step records to calculate your daily total. " +
                    "Manually entered and unverified records do not earn tokens. " +
                    "Raw records and their source apps stay on your device; only the daily " +
                    "step total is sent to PocketPass for rewards. If Health Connect is " +
                    "unavailable or you don't allow access, the phone's step sensor is used " +
                    "when available and permitted. You can turn Step Rewards off in App Settings or revoke " +
                    "Health Connect access in Android settings."
                textSize = 16f
                setTextColor(Color.DKGRAY)
                setPadding(0, padding / 2, 0, padding / 2)
            })
            addView(Button(context).apply {
                text = "Full privacy policy"
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                }
            })
            addView(Button(context).apply {
                text = "Close"
                setOnClickListener { finish() }
            })
        }
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private companion object {
        const val PRIVACY_URL = "https://pocketpass.xyz/privacy"
    }
}
