package com.binaa.sender

import android.net.Uri

/** طلب إرسال واحد قادم من binaa-center-hub، بعد التحقق من كل حقوله. */
data class SendRequest(
    val imageUrl: String,
    val phoneDigits: String,
    val text: String
) {
    companion object {
        private const val MAX_TEXT_LENGTH = 2000

        /** يرجع null مع سبب الرفض إن لم يكن الرابط سليمًا. */
        fun parse(uri: Uri?): Pair<SendRequest?, String?> {
            if (uri == null || uri.scheme != "binaasender" || uri.host != "send") {
                return null to "رابط غير صالح"
            }
            val imageUrl = uri.getQueryParameter("u").orEmpty()
            if (!isAllowedImageUrl(imageUrl)) return null to "رابط الصورة ليس من binaa hub"

            val phone = PhoneNumber.toWhatsAppDigits(uri.getQueryParameter("p").orEmpty())
                ?: return null to "رقم العميل غير صالح"

            val text = uri.getQueryParameter("t").orEmpty().take(MAX_TEXT_LENGTH)
            return SendRequest(imageUrl, phone, text) to null
        }

        /** لا تنزيل إلا من خادم صور binaa-center-hub، عبر رابط موقَّع، وبـ https فقط. */
        fun isAllowedImageUrl(raw: String): Boolean {
            val u = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
            return u.scheme == "https" &&
                u.host == BuildConfig.ALLOWED_HOST &&
                u.port == -1 &&
                u.userInfo == null &&
                (u.path ?: "").startsWith(BuildConfig.ALLOWED_PATH_PREFIX) &&
                !(u.path ?: "").contains("..")
        }
    }
}
