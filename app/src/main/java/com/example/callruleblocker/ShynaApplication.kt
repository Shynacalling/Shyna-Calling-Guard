package com.example.callruleblocker

import android.app.Application
import android.util.Log
import com.cloudinary.android.MediaManager
import com.example.callruleblocker.data.CloudinaryConfig

class ShynaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Log.d("ShynaApplication", "Application onCreate started")
        
        // Initialize Cloudinary for Media Uploads
        val config = mapOf(
            "cloud_name" to CloudinaryConfig.CLOUD_NAME,
            "secure" to true
        )
        
        try {
            // Check if already initialized to prevent IllegalStateException in multi-process apps
            // Though we wrap in Throwable catch to be extra safe
            MediaManager.init(this, config)
            Log.d("ShynaApplication", "Cloudinary Initialized Successfully")
        } catch (t: Throwable) {
            Log.e("ShynaApplication", "Cloudinary Init Error or already initialized: ${t.message}")
        }
    }
}
