package com.brandon.videodownloader

import android.app.Application
import com.brandon.videodownloader.data.AppDatabase
import com.brandon.videodownloader.data.Settings
import com.brandon.videodownloader.download.DownloadController
import com.brandon.videodownloader.download.Notifications
import com.brandon.videodownloader.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Punto de entrada del proceso (antes que cualquier pantalla o worker).
 * Funciona como un contenedor de dependencias "a mano": en un proyecto grande usarías
 * Hilt/Koin, el equivalente al IServiceCollection de .NET.
 */
class App : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { AppDatabase.build(this) }
    val settings by lazy { Settings(this) }
    val engine by lazy { Engine(this, appScope) }
    val downloads by lazy { DownloadController(this, database.downloads(), settings) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        engine.initAsync()
    }
}
