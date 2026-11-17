package com.example.picup

import android.app.Application
import android.content.Context
import com.example.picup.data.PathTemplate
import java.io.File
import com.example.picup.data.DisplayNames
import com.example.picup.data.HistoryRepository
import com.example.picup.data.ImageProcessor
import com.example.picup.data.SettingsRepository
import com.example.picup.data.TokenStore
import com.example.picup.data.KeystoreTokenStore
import com.example.picup.upload.UploadManager

/**
 * Process-wide singletons.
 *
 * Deliberately not a DI framework: there are five objects, all of them cheap,
 * and the graph is built once in [Application.onCreate].
 */
class AppContainer(private val application: Application) {
    val context: Context get() = application.applicationContext
    val cacheDir: File get() = application.cacheDir

    /** Writes bytes into a cache sub-folder; the upload pipeline reads the file back by Uri. */
    fun writeCacheFile(folder: String, fileName: String, bytes: ByteArray): File {
        val dir = File(cacheDir, folder).apply { mkdirs() }
        val safe = PathTemplate.sanitizePath(fileName).replace('/', '-').ifBlank { "clipboard.txt" }
        val file = File(dir, safe)
        file.writeBytes(bytes)
        return file
    }

    val settings = SettingsRepository(application)
    val tokens: TokenStore = KeystoreTokenStore(application)
    val history = HistoryRepository(application)
    val images = ImageProcessor(application)
    val displayNames = DisplayNames(application)
    val uploads = UploadManager(application, settings, tokens, history, images)
}

class PicUpApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}