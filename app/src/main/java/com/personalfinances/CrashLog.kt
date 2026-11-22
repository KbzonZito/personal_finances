package com.personalfinances

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda los cierres inesperados en un archivo local (nunca se envía nada solo).
 * El usuario puede verlo y compartirlo desde Ajustes → Diagnóstico.
 */
object CrashLog {
    private const val FILE = "crash.log"
    private const val MAX_CHARS = 20_000

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, thread: String, error: Throwable) {
        val sw = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val entry = buildString {
            append("=== ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            append(" · v").append(version).append(" · Android ").append(Build.VERSION.RELEASE)
            append(" (API ").append(Build.VERSION.SDK_INT).append(") · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append(" · hilo ").append(thread).append('\n').append(sw).append('\n')
        }
        val file = File(context.filesDir, FILE)
        val all = (if (file.exists()) file.readText() else "") + entry
        file.writeText(all.takeLast(MAX_CHARS))
    }

    fun read(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) { File(context.filesDir, FILE).delete() }
}
