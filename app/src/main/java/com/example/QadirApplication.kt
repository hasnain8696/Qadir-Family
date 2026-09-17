package com.example

import android.app.Application
import android.util.Log
import com.example.data.FirebaseService

class QadirApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("QadirApplication", "Initializing QadirApplication and Firebase Services...")
        // Initialize Firebase service early in the application lifecycle
        FirebaseService.initialize(this)
    }
}
