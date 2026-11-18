package com.kichikan.ak1

import android.app.Application
import com.kichikan.ak1.data.AppInitializer
import com.kichikan.ak1.data.AppRepository

/** Process composition root: load persisted state and finish initialization before UI startup. */
class AK1Application : Application() {
    internal lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val appRepository = AppRepository(applicationContext)
        repository = appRepository
        AppInitializer(appRepository, appRepository).initialize()
    }
}
