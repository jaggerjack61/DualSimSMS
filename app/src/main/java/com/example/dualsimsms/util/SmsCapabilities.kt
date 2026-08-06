package com.example.dualsimsms.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat

object SmsCapabilities {

    /** Default-handler intent constants used on Android 9 and earlier. */
    private const val ACTION_CHANGE_DEFAULT = "android.provider.Telephony.ACTION_CHANGE_DEFAULT"
    private const val EXTRA_PACKAGE_NAME = "package"

    val requiredPermissions: List<String> = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CONTACTS)
    }

    fun hasSmsPermissions(context: Context): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun isDefaultSmsHandler(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true &&
                roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }

    fun requestSmsRole(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_SMS) }
                ?.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(ACTION_CHANGE_DEFAULT).putExtra(EXTRA_PACKAGE_NAME, context.packageName)
        }
}
