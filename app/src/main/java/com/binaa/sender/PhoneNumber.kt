package com.binaa.sender

/**
 * يحوّل الرقم القادم من binaa-center-hub إلى صيغة واتساب (أرقام فقط مع مفتاح الدولة):
 * - موريتاني: 8 أرقام تبدأ بـ 2 أو 3 أو 4، أو نفسها مسبوقة بـ 222.
 * - دولي: مفتاح الدولة + الرقم (10 إلى 15 رقمًا، لا يبدأ بصفر)، مثل 33612345678.
 *   الـHub يتحقق من صحته لأي دولة قبل إرساله.
 * أي شيء آخر يُرفض بدل فتح محادثة رقم خاطئ.
 */
object PhoneNumber {
    fun toWhatsAppDigits(raw: String): String? {
        val digits = raw.filter { it.isDigit() }.removePrefix("00")
        if (digits.length == 8) {
            return if (digits[0] in "234") "222$digits" else null
        }
        if (digits.startsWith("222")) {
            return if (digits.length == 11 && digits[3] in "234") digits else null
        }
        val validInternational = digits.length in 10..15 && digits[0] != '0'
        return if (validInternational) digits else null
    }
}
