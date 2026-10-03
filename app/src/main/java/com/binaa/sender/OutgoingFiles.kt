package com.binaa.sender

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * ملفات الصور المؤقتة التي تُسلَّم لواتساب بزنس. لا تُحذف فور الإرسال لأن
 * واتساب يقرؤها بعد إغلاق تطبيقنا؛ تُحذف عند كل تشغيل أي ملف أقدم من 30 دقيقة.
 */
object OutgoingFiles {
    private const val MAX_BYTES = 15L * 1024 * 1024
    private const val MAX_AGE_MS = 30L * 60 * 1000
    private const val TIMEOUT_MS = 20_000

    private fun dir(context: Context): File =
        File(context.cacheDir, "outgoing").apply { mkdirs() }

    fun cleanupOld(context: Context) {
        val now = System.currentTimeMillis()
        dir(context).listFiles()?.forEach {
            if (now - it.lastModified() > MAX_AGE_MS) it.delete()
        }
    }

    /** يشغَّل على Thread خلفية فقط. يرجع (Uri, mimeType) أو يرمي IOException برسالة عربية. */
    fun download(context: Context, imageUrl: String): Pair<Uri, String> {
        val conn = URL(imageUrl).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false // لا انتقال لأي خادم آخر
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException(
                    if (code == 400 || code == 403) "انتهت صلاحية رابط الصورة، أعد المحاولة من binaa hub"
                    else "تعذر تنزيل الصورة (رمز $code)"
                )
            }
            val mime = conn.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val ext = when (mime) {
                "image/jpeg", "image/jpg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> throw IOException("الملف ليس صورة")
            }
            if (conn.contentLengthLong > MAX_BYTES) throw IOException("الصورة كبيرة جدًا")

            val out = File(dir(context), "invoice-${System.currentTimeMillis()}.$ext")
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) {
                            output.close()
                            out.delete()
                            throw IOException("الصورة كبيرة جدًا")
                        }
                        output.write(buf, 0, n)
                    }
                }
            }
            return uriFor(context, out) to (if (ext == "jpg") "image/jpeg" else "image/$ext")
        } finally {
            conn.disconnect()
        }
    }

    /** لشاشة التجربة فقط: يحفظ صورة مولّدة محليًا. */
    fun saveTestImage(context: Context, bitmap: Bitmap): Pair<Uri, String> {
        val out = File(dir(context), "test-${System.currentTimeMillis()}.png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uriFor(context, out) to "image/png"
    }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
