package com.gameshift.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/** Resolve a package name to a human-readable app label. */
fun getAppName(context: Context, packageName: String): String {
    return try {
        val ai = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(ai).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}

/** Check if a package is installed on the device. */
fun isAppInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

/** Get all installed apps that are valid HOME-category launchers, excluding [excludePackage]. */
fun getHomeLauncherPackages(context: Context, excludePackage: String? = null): List<Pair<String, String>> {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }
    val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
    return resolveInfos
        .map { it.activityInfo.packageName }
        .distinct()
        .filterNot { it == excludePackage }
        .sorted()
        .map { pkg ->
            val name = getAppName(context, pkg)
            name to pkg
        }
}

/** Get the current default HOME launcher package name. */
fun getCurrentDefaultLauncher(context: Context): String? {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }
    val resolveInfo = context.packageManager.resolveActivity(
        intent,
        PackageManager.MATCH_DEFAULT_ONLY
    )
    return resolveInfo?.activityInfo?.packageName
}

/**
 * Validate that a package name is a valid Android package name format.
 * Android package names must match [a-zA-Z0-9._]+
 */
fun isValidPackageName(name: String): Boolean {
    return name.matches(Regex("^[a-zA-Z0-9._]+\$"))
}

/**
 * Validate that a package is actually installed on the device.
 */
fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
