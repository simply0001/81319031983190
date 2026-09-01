package com.pocketpass.app.widget

import com.pocketpass.app.logPlatformInfo
import com.pocketpass.app.logPlatformWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Lets the Swift side register how widgets get refreshed: WidgetKit has no
 * Objective-C surface, so `WidgetCenter.shared.reloadAllTimelines()` can only
 * be called from Swift, which the AppDelegate wires in at launch.
 */
object IosWidgetReload {
    var handler: (() -> Unit)? = null
}

/**
 * Writes the snapshot into the App Group container the widget extension reads.
 * The group identifier is discovered from the embedded provisioning profile
 * so that a re-signing tool rewriting entitlements keeps app and widget in
 * agreement; without a container (entitlement missing) publishing is a no-op
 * and the widget keeps showing its placeholder.
 */
class IosWidgetSnapshotSink(
    private val groupIdentifier: String = discoverAppGroupIdentifier(),
) : WidgetSnapshotSink {
    private val fileSystem = FileSystem.SYSTEM
    private var warnedMissingContainer = false

    override suspend fun publish(snapshot: WidgetSnapshot, portraitSourcePath: String?) {
        val directory = widgetDirectory()
        if (directory == null) {
            if (!warnedMissingContainer) {
                warnedMissingContainer = true
                logPlatformWarning(TAG, "No App Group container for $groupIdentifier; widgets stay idle")
            }
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                fileSystem.createDirectories(directory)
                val portrait = directory / WidgetSnapshot.PORTRAIT_FILE_NAME
                val source = portraitSourcePath?.toPath()?.takeIf { fileSystem.exists(it) }
                if (source != null) {
                    val temporary = directory / "${WidgetSnapshot.PORTRAIT_FILE_NAME}.tmp"
                    fileSystem.copy(source, temporary)
                    fileSystem.atomicMove(temporary, portrait)
                } else {
                    fileSystem.delete(portrait, mustExist = false)
                }
                val temporary = directory / "${WidgetSnapshot.SNAPSHOT_FILE_NAME}.tmp"
                fileSystem.write(temporary) { writeUtf8(snapshot.encode()) }
                fileSystem.atomicMove(temporary, directory / WidgetSnapshot.SNAPSHOT_FILE_NAME)
            }.onFailure { error ->
                logPlatformWarning(TAG, "Widget snapshot could not be written: $error")
            }
        }
        dispatch_async(dispatch_get_main_queue()) {
            IosWidgetReload.handler?.invoke()
        }
    }

    private fun widgetDirectory(): Path? {
        val container = NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(groupIdentifier)
            ?.path
            ?: return null
        return container.toPath() / "widget"
    }

    companion object {
        private const val TAG = "PocketPassWidgets"
        const val DEFAULT_GROUP_IDENTIFIER = "group.xyz.pocketpass"

        /**
         * Reads the first application-group entitlement out of the embedded
         * provisioning profile (the entitlements plist sits in the CMS blob as
         * plain XML), falling back to the identifier the project declares.
         */
        fun discoverAppGroupIdentifier(): String {
            val path = NSBundle.mainBundle.pathForResource("embedded", "mobileprovision")
                ?: return DEFAULT_GROUP_IDENTIFIER
            val data = NSData.dataWithContentsOfFile(path) ?: return DEFAULT_GROUP_IDENTIFIER
            val text = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                ?: latin1(data)
                ?: return DEFAULT_GROUP_IDENTIFIER
            val keyIndex = text.indexOf("<key>com.apple.security.application-groups</key>")
            if (keyIndex < 0) return DEFAULT_GROUP_IDENTIFIER
            val match = Regex("<string>([^<]+)</string>").find(text, keyIndex)
                ?: return DEFAULT_GROUP_IDENTIFIER
            val discovered = match.groupValues[1].trim()
            if (discovered != DEFAULT_GROUP_IDENTIFIER) {
                logPlatformInfo(TAG, "Using re-signed App Group $discovered")
            }
            return discovered.ifBlank { DEFAULT_GROUP_IDENTIFIER }
        }

        private fun latin1(data: NSData): String? =
            NSString.create(data = data, encoding = 5uL /* NSISOLatin1StringEncoding */)?.toString()
    }
}
