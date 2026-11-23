package de.jellydroid.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    /** Vorinstallierte System-App ohne Update: lässt sich nicht deinstallieren. */
    val isSystem: Boolean = false,
)

class AppRepository(private val context: Context) {

    /** Alle startbaren Apps, TV-Apps zuerst erkannt, alphabetisch sortiert. */
    fun loadApps(): List<AppEntry> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in query(pm, category)) {
                val ai = ri.activityInfo
                if (ai.packageName == context.packageName) continue
                if (ai.packageName in HIDDEN_PACKAGES || result.containsKey(ai.packageName)) continue
                val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
                val icon = ai.loadIcon(pm)
                result[ai.packageName] = AppEntry(
                    packageName = ai.packageName,
                    activityName = ai.name,
                    label = ai.loadLabel(pm).toString(),
                    banner = banner?.let { runCatching { it.toBitmap(320, 180).asImageBitmap() }.getOrNull() },
                    icon = runCatching { icon.toBitmap(128, 128).asImageBitmap() }.getOrNull(),
                    isSystem = (ai.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (ai.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                )
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    /** Gespeicherte Reihenfolge der Favoriten (Paketnamen), null = noch nie angepasst. */
    fun savedFavorites(): List<String>? =
        prefs.getString("order", null)?.split(',')?.filter { it.isNotBlank() }

    fun saveFavorites(order: List<String>) {
        prefs.edit().putString("order", order.joinToString(",")).apply()
    }

    /** Favoriten in gespeicherter Reihenfolge; ohne Anpassung die bekannten Lieblings-Apps. */
    fun favorites(apps: List<AppEntry>, order: List<String>?, count: Int = 7): List<AppEntry> {
        val byPkg = apps.associateBy { it.packageName }
        if (order != null) return order.mapNotNull { byPkg[it] }
        val preferred = FAVORITE_PACKAGES.mapNotNull { byPkg[it] }
        return (preferred + apps.filter { it !in preferred }).take(count)
    }

    fun launch(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "${app.label} lässt sich nicht starten", Toast.LENGTH_SHORT).show()
        }
    }

    /** Öffnet die Deinstallations-Abfrage von Android (dort muss noch einmal bestätigt werden). */
    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "${app.label} lässt sich nicht deinstallieren", Toast.LENGTH_SHORT).show()
        }
    }

    private fun query(pm: PackageManager, category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
    }

    companion object {
        /** Apps, die nicht im Raster erscheinen sollen (Paketnamen). */
        val HIDDEN_PACKAGES = setOf<String>()

        val FAVORITE_PACKAGES = listOf(
            "com.edde746.plezy",
            "com.liskovsoft.smarttubetv.beta",
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
        )
    }
}
