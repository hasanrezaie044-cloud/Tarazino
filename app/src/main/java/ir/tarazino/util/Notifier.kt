package ir.tarazino.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** اعلان‌های محلی (بدون AI و بدون اینترنت). هر اعلان با کلید روز یک‌بار ارسال می‌شود. */
object Notifier {
    const val CH_DUE = "due"; const val CH_BUDGET = "budget"; const val CH_SAVED = "saved"

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DUE, "سررسیدها", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BUDGET, "بودجه", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_SAVED, "ثبت تراکنش", NotificationManager.IMPORTANCE_LOW))
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** dedupeKey خالی = بدون کنترل تکرار. */
    fun show(ctx: Context, channel: String, title: String, text: String, dedupeKey: String) {
        if (!canNotify(ctx)) return
        if (dedupeKey.isNotEmpty()) { if (AppPrefs.alreadySent(dedupeKey)) return; AppPrefs.markSent(dedupeKey) }
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build()
        ctx.getSystemService(NotificationManager::class.java).notify((dedupeKey + title + text).hashCode(), n)
    }
}
