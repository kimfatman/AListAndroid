package io.alist.app.alist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREF_NAME = "alist_service_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"

        fun setServiceEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        }

        fun isServiceEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_ENABLED, true)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (isServiceEnabled(context)) {
                Log.d(TAG, "Boot completed, starting AList service...")
                AListForegroundService.start(context)
            }
        }
    }
}
