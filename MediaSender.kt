package com.example.gifkeyboard.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.example.gifkeyboard.data.MediaItem
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Owns the actual "send a clip" decision tree:
 *
 *  1. Download the MP4/WebM to a cache file (commitContent needs a content:// Uri,
 *     and we also need a real local file to share as a fallback).
 *  2. If the focused field's EditorInfo advertises a MIME type we can satisfy via
 *     InputConnectionCompat.commitContent (Android 7.1+ "rich content" support,
 *     e.g. Gboard-style sticker insertion in Messages/Gmail/many chat apps), push
 *     it directly into the conversation — this is the only path that actually
 *     "sends" the clip the same way Gboard does.
 *  3. Otherwise, fall back to a standard ACTION_SEND share-sheet Intent so the
 *     user can still get the clip into the field via the OS share UI, and we
 *     surface a small toast/snackbar explaining why ("this app can't receive
 *     clips directly").
 *
 * This honestly mirrors what Gboard itself does — there is no secret API that
 * lets a keyboard force-embed playable video into an arbitrary EditText that
 * hasn't opted in to rich content.
 */
object MediaSender {

    private const val TAG = "MediaSender"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    private val httpClient by lazy { OkHttpClient() }

    sealed class SendResult {
        object SentInline : SendResult()
        object FellBackToShareSheet : SendResult()
        data class Failed(val reason: String) : SendResult()
    }

    /**
     * @param inputConnection the IME's current InputConnection (null if no field is focused)
     * @param editorInfo the focused field's EditorInfo, used to check supported MIME types
     */
    fun send(
        context: Context,
        item: MediaItem,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?
    ): SendResult {
        val file = try {
            downloadToCache(context, item)
        } catch (e: IOException) {
            Log.w(TAG, "Download failed for ${item.id}: ${e.message}")
            return SendResult.Failed("Couldn't download clip")
        }
        return deliver(context, item, file, inputConnection, editorInfo)
    }

    /** Network-only step. Safe to call from Dispatchers.IO. */
    fun downloadOnly(context: Context, item: MediaItem): Result<File> = runCatching {
        downloadToCache(context, item)
    }

    /**
     * Commit/share step. Touches InputConnection — callers MUST invoke this on
     * the main thread (the same thread the IME's InputConnection was obtained on).
     */
    fun deliver(
        context: Context,
        item: MediaItem,
        file: File,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?
    ): SendResult {
        val mimeType = guessMimeType(file.name)
        val supportsRichContent = inputConnection != null && editorInfo != null &&
            supportsCommitContent(editorInfo, mimeType)

        if (supportsRichContent) {
            val committed = commitContentClip(context, inputConnection!!, editorInfo!!, file, mimeType, item)
            if (committed) return SendResult.SentInline
            // commitContent can still fail at the OS/app level even when advertised — fall through
        }

        return if (shareViaIntent(context, file, mimeType)) {
            SendResult.FellBackToShareSheet
        } else {
            SendResult.Failed("No app available to receive the clip")
        }
    }

    private fun supportsCommitContent(editorInfo: EditorInfo, mimeType: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false // commitContent needs 25+
        val supportedMimeTypes = EditorInfo.getContentMimeTypes(editorInfo)
        return supportedMimeTypes.any { supported ->
            android.content.ClipDescription.compareMimeTypes(mimeType, supported)
        }
    }

    private fun commitContentClip(
        context: Context,
        ic: InputConnection,
        editorInfo: EditorInfo,
        file: File,
        mimeType: String,
        item: MediaItem
    ): Boolean {
        val authority = context.packageName + AUTHORITY_SUFFIX
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        context.grantUriPermission(
            editorInfo.packageName,
            contentUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val description = android.content.ClipDescription(item.title.ifBlank { "clip" }, arrayOf(mimeType))
        val inputContentInfo = InputContentInfoCompat(contentUri, description, null)

        return InputConnectionCompat.commitContent(
            ic, editorInfo, inputContentInfo,
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null
        )
    }

    private fun shareViaIntent(context: Context, file: File, mimeType: String): Boolean {
        return try {
            val authority = context.packageName + AUTHORITY_SUFFIX
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // required when launched from a Service context
            }
            val chooser = Intent.createChooser(intent, "Send clip via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Share fallback failed", e)
            false
        }
    }

    @Throws(IOException::class)
    private fun downloadToCache(context: Context, item: MediaItem): File {
        val cacheDir = File(context.cacheDir, "clips").apply { mkdirs() }
        val extension = if (item.videoUrl.endsWith(".webm")) "webm" else "mp4"
        val outFile = File(cacheDir, "${item.id}.$extension")

        if (outFile.exists() && outFile.length() > 0) return outFile // already cached

        val request = Request.Builder().url(item.videoUrl).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response body")
            outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        return outFile
    }

    private fun guessMimeType(fileName: String): String = when {
        fileName.endsWith(".webm") -> "video/webm"
        fileName.endsWith(".gif") -> "image/gif"
        else -> "video/mp4"
    }
}
