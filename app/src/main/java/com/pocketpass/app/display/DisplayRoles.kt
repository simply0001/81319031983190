package com.pocketpass.app.display

import android.os.Build

object DisplayRoles {
    private val BOTTOM_PRIMARY_DEVICES = setOf("RGDS")

    val defaultDisplayIsBottomPanel: Boolean by lazy {
        isBottomPrimaryDevice(Build.MODEL, Build.DEVICE, Build.PRODUCT)
    }

    fun isBottomPrimaryDevice(vararg deviceNames: String?): Boolean =
        deviceNames.any { name ->
            val normalized = name.orEmpty().filter(Char::isLetterOrDigit).uppercase()
            BOTTOM_PRIMARY_DEVICES.any(normalized::contains)
        }
}
