package com.limelight

import android.app.Application
import android.content.Context

class MoonlightApplication : Application() {

    init {
        instance = this
    }

    companion object {
        private var instance: MoonlightApplication? = null

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        // initialize for any

        // Use ApplicationContext.
        // example: SharedPreferences etc...
        val context: Context = MoonlightApplication.applicationContext()
    }
}