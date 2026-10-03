package com.binaa.sender

/**
 * نفس قاعدة binaa-center-hub (toWhatsAppNumber + isValidMauritanianPhone):
 * 8 أرقام تبدأ بـ 2 أو 3 أو 4، أو نفسها مسبوقة بـ 222. أي شيء آخر يُرفض
 * بدل فتح محادثة رقم خاطئ.
 */
object PhoneNumber {
    fun toWhatsAppDigits(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val full = if (digits.length == 8) "222$digits" else digits
        val valid = full.length == 11 && full.startsWith("222") && full[3] in "234"
        return if (valid) full else null
    }
}
