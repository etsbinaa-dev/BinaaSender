package com.binaa.sender

import android.net.Uri

/** طلب إرسال واحد قادم من binaa-center-hub، بعد التحقق من كل حقوله. */
data class SendRequest(
    val imageUrl: String,
    val phoneDigits: String,
    val text: String,
    /** صفحة «فرز وإرسال» التي يعود إليها زر «⬅ بناء» */
    val returnUrl: String = DEFAULT_RETURN_URL,
) {
    companion object {
        private const val MAX_TEXT_LENGTH = 2000
        val DEFAULT_RETURN_URL = "https://${BuildConfig.RETURN_HOST}/invoices"

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
            val r = uri.getQueryParameter("r").orEmpty()
            val returnUrl = if (isAllowedReturnUrl(r)) r else DEFAULT_RETURN_URL
            return SendRequest(imageUrl, phone, text, returnUrl) to null
        }

        /** الرجوع إلى binaa hub فقط (لا يفتح الزر أي موقع آخر). */
        fun isAllowedReturnUrl(raw: String): Boolean {
            if (raw.isEmpty()) return false
            val u = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
            return u.scheme == "https" &&
                u.host == BuildConfig.RETURN_HOST &&
                u.port == -1 &&
                u.userInfo == null
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
