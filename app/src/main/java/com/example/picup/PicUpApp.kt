package com.example.picup

import android.app.Application
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
class AppContainer(application: Application) {
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