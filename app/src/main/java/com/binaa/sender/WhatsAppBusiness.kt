package com.binaa.sender

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * يفتح محادثة رقم محدد في واتساب بزنس والصورة مرفقة والنص تحتها. المستخدم
 * هو من يضغط «إرسال» دائمًا — لا إرسال آلي إطلاقًا.
 *
 * ملاحظة: الحقل الإضافي "jid" غير موثّق رسميًا من واتساب لكنه مستخدم على
 * نطاق واسع؛ إن تجاهله واتساب في تحديث ما، ستظهر قائمة اختيار المحادثة بدل
 * فتح محادثة العميل مباشرة، والصورة تبقى مرفقة.
 */
object WhatsAppBusiness {
    const val PACKAGE = "com.whatsapp.w4b"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun buildIntent(imageUri: Uri, mimeType: String, phoneDigits: String, text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            setPackage(PACKAGE)
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, imageUri)
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            putExtra("jid", "$phoneDigits@s.whatsapp.net")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
