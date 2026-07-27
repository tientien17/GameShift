package com.gameshift.app.detector

import android.hardware.input.InputManager
import android.view.InputDevice

/**
 * Encapsulates game controller detection logic.
 *
 * A "game controller" is any input device that advertises
 * [InputDevice.SOURCE_GAMEPAD] or [InputDevice.SOURCE_JOYSTICK].
 */
object ControllerDetector {

    /**
     * Check whether a given input device is a game controller.
     */
    fun isGameController(deviceId: Int): Boolean {
        val device = InputDevice.getDevice(deviceId) ?: return false
        return isGameController(device)
    }

    /**
     * Check whether a given input device is a game controller.
     */
    fun isGameController(device: InputDevice): Boolean {
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
                (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    /**
     * Heuristic check — some generic USB HID gamepads don't advertise
     * SOURCE_GAMEPAD but still have gamepad-like axes and keys.
     * This is a more lenient check.
     */
    fun isLikelyGameController(deviceId: Int): Boolean {
        val device = InputDevice.getDevice(deviceId) ?: return false
        val sources = device.sources

        // Primary check: advertises gamepad or joystick source
        if ((sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
            (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
        ) {
            // Double-check: exclude keyboards and mice that might have overlap
            val hasGameKeys = device.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC
            val hasAxes = device.getMotionRanges().size > 2
            return hasAxes || !hasGameKeys
        }

        // Secondary check: has gamepad-like key codes (A/B/X/Y buttons etc.)
        // This would require checking key maps — skip for now, stick to sources.
        return false
    }

    fun isAnyControllerConnected(): Boolean {
        val deviceIds = InputDevice.getDeviceIds()
        return deviceIds.any { isGameController(it) }
    }

    fun isAnyControllerLikelyConnected(): Boolean {
        val deviceIds = InputDevice.getDeviceIds()
        return deviceIds.any { isLikelyGameController(it) }
    }

    fun countConnectedControllers(): Int {
        val deviceIds = InputDevice.getDeviceIds()
        return deviceIds.count { isGameController(it) }
    }

    /**
     * Get IDs of all currently connected game controllers.
     */
    fun getConnectedControllerIds(): List<Int> {
        val deviceIds = InputDevice.getDeviceIds()
        return deviceIds.filter { isGameController(it) }
    }

    /**
     * Get a human-readable description of connected controllers.
     */
    fun getConnectedControllerNames(): List<String> {
        return getConnectedControllerIds().mapNotNull { id ->
            InputDevice.getDevice(id)?.name
        }
    }
}
