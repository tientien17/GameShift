package com.gameshift.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.gameshift.app.util.Prefs

class GameShiftApp : Application() {

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "gameshift_controller_monitor"
        const val NOTIFICATION_ID = 1001

        lateinit var instance: GameShiftApp
            private set
    }
}
