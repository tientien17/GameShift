package com.gameshift.app.shizuku

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * Shizuku UserService that executes shell commands with shell/root privilege.
 *
 * This replaces the previous `Shizuku.newProcess()` reflection approach
 * (deprecated in Shizuku v13, scheduled for removal).
 *
 * The service runs in a separate process with the identity (UID) of
 * shell (2000) or root (0) depending on Shizuku's backend.
 *
 * Lifecycle:
 * - Start: `Shizuku.bindUserService(args, connection)` from the main process
 * - Destroy: call `destroy()` — service calls System.exit(0)
 */
class ShizukuShellService : IUserShellService.Stub() {

    companion object {
        private const val TAG = "ShizukuShellService"
    }

    override fun exec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = reader.readText()
            val errorOutput = errorReader.readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                output.trim()
            } else {
                val msg = errorOutput.ifBlank { output }
                "ERROR[$exitCode]: $msg"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed", e)
            "ERROR: ${e.message}"
        }
    }

    override fun destroy() {
        Log.d(TAG, "Destroy requested, exiting")
        exitProcess(0)
    }
}
