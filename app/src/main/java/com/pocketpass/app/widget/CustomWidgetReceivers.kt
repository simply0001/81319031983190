package com.pocketpass.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

abstract class CustomWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CustomWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        runCatching {
            WidgetBindingStore(WidgetSnapshotStore.directory(context.applicationContext)).unbind(appWidgetIds.toList())
        }
    }
}

class MiniWidgetReceiver : CustomWidgetReceiver()

class SmallWidgetReceiver : CustomWidgetReceiver()

class WideWidgetReceiver : CustomWidgetReceiver()

class TallWidgetReceiver : CustomWidgetReceiver()

object WidgetReceivers {
    fun receiverFor(size: WidgetSize): Class<out CustomWidgetReceiver> = when (size) {
        WidgetSize.Mini -> MiniWidgetReceiver::class.java
        WidgetSize.Small -> SmallWidgetReceiver::class.java
        WidgetSize.Wide -> WideWidgetReceiver::class.java
        WidgetSize.Tall -> TallWidgetReceiver::class.java
    }

    fun sizeFor(providerClassName: String?): WidgetSize? = when (providerClassName) {
        MiniWidgetReceiver::class.java.name -> WidgetSize.Mini
        SmallWidgetReceiver::class.java.name -> WidgetSize.Small
        WideWidgetReceiver::class.java.name -> WidgetSize.Wide
        TallWidgetReceiver::class.java.name -> WidgetSize.Tall
        else -> null
    }
}
