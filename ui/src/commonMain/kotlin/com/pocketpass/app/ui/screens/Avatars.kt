package com.pocketpass.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.components.rememberPocketAssetBytes

@Composable
fun DynamicAvatar(
    avatar: AvatarReference?,
    fallbackResource: PocketAsset?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bundled = when (avatar) {
        is AvatarReference.Remote -> null
        is AvatarReference.Bundled -> avatarResourceForKey(avatar.key) ?: fallbackResource
        null -> fallbackResource
    }
    val remoteUrl = (avatar as? AvatarReference.Remote)?.url
    if (remoteUrl == null && bundled == null) return
    val fallbackPainter = fallbackResource
        ?.let { rememberPocketAssetBytes(it) }
        ?.let { bytes -> rememberAsyncImagePainter(bytes) }
    AsyncImage(
        model = remoteUrl ?: bundled?.let { rememberPocketAssetBytes(it) },
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        fallback = fallbackPainter,
        error = fallbackPainter,
    )
}

fun avatarResourceForKey(key: String): PocketAsset? = when (key) {
    "home_avatar_petah" -> Assets.HomeAvatarPetah
    "home_avatar_matt" -> Assets.HomeAvatarMatt
    "friends_avatar_matt" -> Assets.FriendsAvatarMatt
    "messages_avatar_spob" -> Assets.MessagesAvatarSpob
    "messages_avatar_sans" -> Assets.MessagesAvatarSans
    else -> null
}
