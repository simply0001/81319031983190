package com.pocketpass.app.media

import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCurrent
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

class IosImageAttachmentPicker(
    private val onPicked: (String) -> Unit,
    private val onFailed: () -> Unit,
    private val onCancelled: () -> Unit = {},
) {
    private var activeDelegate: PickerDelegate? = null

    fun present(from: UIViewController) {
        if (activeDelegate != null) return
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1L
            preferredAssetRepresentationMode =
                PHPickerConfigurationAssetRepresentationModeCurrent
        }
        val picker = PHPickerViewController(configuration = configuration)
        val delegate = PickerDelegate(
            onPicked = { path ->
                activeDelegate = null
                onPicked(path)
            },
            onFailed = {
                activeDelegate = null
                onFailed()
            },
            onCancelled = { activeDelegate = null; onCancelled() },
        )
        activeDelegate = delegate
        picker.delegate = delegate
        from.topMostPresented().presentViewController(picker, animated = true, completion = null)
    }
}

private fun UIViewController.topMostPresented(): UIViewController {
    var current = this
    while (true) {
        current = current.presentedViewController ?: return current
    }
}

private class PickerDelegate(
    private val onPicked: (String) -> Unit,
    private val onFailed: () -> Unit,
    private val onCancelled: () -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onCancelled()
            return
        }
        val provider = result.itemProvider
        val typeIdentifier = UTTypeImage.identifier
        if (!provider.hasItemConformingToTypeIdentifier(typeIdentifier)) {
            onFailed()
            return
        }
        provider.loadFileRepresentationForTypeIdentifier(typeIdentifier) { url, _ ->
            val copied = url?.let(::copyToTemporary)
            dispatch_async(dispatch_get_main_queue()) {
                if (copied != null) onPicked(copied) else onFailed()
            }
        }
    }
}

private fun copyToTemporary(url: NSURL): String? {
    val sourcePath = url.path ?: return null
    val target = NSTemporaryDirectory().toPath() / "pocketpass-pick-${NSUUID().UUIDString}"
    return try {
        FileSystem.SYSTEM.copy(sourcePath.toPath(), target)
        target.toString()
    } catch (_: okio.IOException) {
        null
    }
}
