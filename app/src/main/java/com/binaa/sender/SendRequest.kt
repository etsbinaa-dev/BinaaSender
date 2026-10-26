package com.binaa.sender

import android.net.Uri

/** طلب إرسال واحد قادم من binaa-center-hub، بعد التحقق من كل حقوله. */
data class SendRequest(
    /** صورة واحدة، أو عدة صور (صفحات كشف)، أو ملف PDF — كلها من binaa hub */
    val fileUrls: List<String>,
    val phoneDigits: String,
    val text: String,
    /** صفحة «فرز وإرسال» التي يعود إليها زر «⬅ بناء» */
    val returnUrl: String = DEFAULT_RETURN_URL,
    /** اسم ملف PDF كما يراه الزبون في واتساب (اختياري) */
    val fileName: String = "",
) {
    companion object {
        private const val MAX_TEXT_LENGTH = 2000
        const val MAX_FILES = 10
        val DEFAULT_RETURN_URL = "https://${BuildConfig.RETURN_HOST}/invoices"

        /** يرجع null مع سبب الرفض إن لم يكن الرابط سليمًا. */
        fun parse(uri: Uri?): Pair<SendRequest?, String?> {
            if (uri == null || uri.scheme != "binaasender" || uri.host != "send") {
                return null to "رابط غير صالح"
            }
            val urls = uri.getQueryParameters("u").orEmpty()
            if (urls.isEmpty() || urls.size > MAX_FILES) return null to "رابط غير صالح"
            if (!urls.all { isAllowedImageUrl(it) }) return null to "رابط الصورة ليس من binaa hub"

            val phone = PhoneNumber.toWhatsAppDigits(uri.getQueryParameter("p").orEmpty())
                ?: return null to "رقم العميل غير صالح"

            val text = uri.getQueryParameter("t").orEmpty().take(MAX_TEXT_LENGTH)
            val r = uri.getQueryParameter("r").orEmpty()
            val returnUrl = if (isAllowedReturnUrl(r)) r else DEFAULT_RETURN_URL
            val name = uri.getQueryParameter("n").orEmpty()
                .replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().take(80)
            return SendRequest(urls, phone, text, returnUrl, name) to null
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
