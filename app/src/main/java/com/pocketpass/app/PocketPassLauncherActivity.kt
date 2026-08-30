package com.pocketpass.app

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Display

class PocketPassLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetIntent = Intent(intent).apply {
            setClass(this@PocketPassLauncherActivity, MainActivity::class.java)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            .toBundle()

        runCatching {
            startActivity(targetIntent, options)
        }.recoverCatching {
            startActivity(targetIntent)
        }
        finish()
    }
}
