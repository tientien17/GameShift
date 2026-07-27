package com.gameshift.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gameshift.app.service.ControllerMonitorService

/**
 * Restarts the controller monitoring service after a system reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = GameShiftApp.instance.prefs
            if (prefs.autoStartEnabled) {
                ControllerMonitorService.start(context)
            }
        }
    }
}
