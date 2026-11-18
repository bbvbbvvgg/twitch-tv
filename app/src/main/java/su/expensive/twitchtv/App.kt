package su.expensive.twitchtv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import su.expensive.twitchtv.data.Account
import su.expensive.twitchtv.data.Net
import su.expensive.twitchtv.data.Prefs

class App : Application(), ImageLoaderFactory {
    lateinit var prefs: Prefs; private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Net.init(this)
        prefs = Prefs(this)
        Account.init(this)
    }

    /** Small, bounded image caches: TVs often have 1–2 GB RAM. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Net.http }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(40L * 1024 * 1024).build() }
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()

    companion object { lateinit var instance: App; private set }
}
