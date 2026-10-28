package ir.tarazino.util

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

object AppVersion {
    /** مثال: ۰.۵.۰ (۵) — از versionName و versionCode در build.gradle.kts خوانده می‌شود. */
    fun label(ctx: Context): String {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return Fa.digits(p.versionName ?: "") + " (" + Fa.digits(PackageInfoCompat.getLongVersionCode(p).toString()) + ")"
    }
}
