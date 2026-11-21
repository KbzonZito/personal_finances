package com.personalfinances.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.personalfinances.AppContainer
import java.time.LocalDate

/**
 * Respaldo automático semanal en una carpeta elegida por el usuario (Storage Access Framework).
 * Conserva los últimos [KEEP] archivos `finanzas-auto-*.json`.
 */
object AutoBackup {
    private const val KEEP = 6
    private const val PREFIX = "finanzas-auto-"
    private const val INTERVAL_MS = 7L * 24 * 3600_000

    fun persistPermission(context: Context, tree: Uri) {
        context.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun folderName(context: Context, tree: Uri): String = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: "Carpeta elegida"

    suspend fun runIfDue(context: Context, c: AppContainer) {
        val s = c.settings.current()
        val uri = s.autoBackupUri ?: return
        if (System.currentTimeMillis() - s.lastAutoBackup < INTERVAL_MS) return
        runNow(context, c, Uri.parse(uri))
    }

    /** Escribe un respaldo ahora. Devuelve el número de movimientos respaldados. */
    suspend fun runNow(context: Context, c: AppContainer, tree: Uri): Int {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = "$PREFIX${LocalDate.now()}.json"
        // Reemplaza el del mismo día si existe.
        children(context, tree).filter { it.second == name }.forEach { DocumentsContract.deleteDocument(resolver, it.first) }
        val file = DocumentsContract.createDocument(resolver, parent, "application/json", name) ?: error("No se pudo crear el archivo")
        val count = c.backup.export(file)
        c.settings.setLastAutoBackup(System.currentTimeMillis())
        children(context, tree).filter { it.second.startsWith(PREFIX) }.sortedByDescending { it.second }.drop(KEEP)
            .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.first) } }
        return count
    }

    private fun children(context: Context, tree: Uri): List<Pair<Uri, String>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val out = mutableListOf<Pair<Uri, String>>()
        context.contentResolver.query(childrenUri, cols, null, null, null)?.use { cur ->
            while (cur.moveToNext()) out += DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0)) to (cur.getString(1) ?: "")
        }
        return out
    }
}
