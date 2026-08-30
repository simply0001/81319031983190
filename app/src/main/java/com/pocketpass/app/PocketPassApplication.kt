package com.pocketpass.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.pocketpass.app.data.supabase.AuthenticatedAvatarInterceptor
import okhttp3.OkHttpClient

class PocketPassApplication : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun newImageLoader(context: Context): ImageLoader {
        val imageHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor(
                AuthenticatedAvatarInterceptor(
                    supabaseBaseUrl = BuildConfig.SUPABASE_URL,
                    publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                    accessTokenProvider = {
                        container.authRemoteDataSource
                            ?.currentSessionOrNull()
                            ?.accessToken
                    },
                ),
            )
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { imageHttpClient },
                    ),
                )
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        container
    }
}
