package com.gameshift.app

import android.app.Activity
import android.hardware.usb.UsbManager
import android.os.Bundle
import com.gameshift.app.service.ControllerMonitorService

/**
 * Transparent activity that receives USB_DEVICE_ATTACHED intents.
 *
 * On Android 12+, manifest-registered broadcast receivers cannot start
 * foreground services from the background. This activity acts as a trampoline:
 * when a USB device is plugged in, this transparent activity is launched,
 * immediately starts the monitoring service, and finishes.
 */
class UsbReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the controller monitor service to re-evaluate controller state
        ControllerMonitorService.start(this)

        // Finish immediately — this activity is invisible
        finish()
    }
}
