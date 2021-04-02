package com.axzae.homeassistant

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.preference.PreferenceManager
import androidx.multidex.MultiDex
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

open class AppController : Application() {
    private var mFirebaseRemoteConfig: FirebaseRemoteConfig? = null
    override fun attachBaseContext(context: Context) {
        super.attachBaseContext(context)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
        if (BuildConfig.DEBUG) {
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else 300.toLong())
                .build()
            mFirebaseRemoteConfig!!.setConfigSettingsAsync(configSettings)
        }
        fetchFirebaseConfig()

        //String android_id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        //Crashlytics.log(Log.DEBUG, "YouQi", "");
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
    }

    private fun fetchFirebaseConfig() {
        val cacheExpiration: Long = 3600 // 1 hour in seconds.
        // If in developer mode cacheExpiration is set to 0 so each fetch will retrieve values from the server.
    }

    val sharedPref: SharedPreferences?
        get() {
            if (Companion.sharedPref == null) {
                Companion.sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            }
            return Companion.sharedPref
        }

    companion object {
        @get:Synchronized var instance: AppController? = null
            private set
        private var sharedPref: SharedPreferences? = null
    }
}