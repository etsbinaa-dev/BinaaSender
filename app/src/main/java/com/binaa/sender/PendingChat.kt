package com.binaa.sender

import android.content.Context

/**
 * المحادثة التي ننتظر أن يختارها ChatPickerService في قائمة «Envoyer à…»
 * (رقم العميل + وقت الطلب). تُحفظ في SharedPreferences لأن الخدمة قد تعمل بعد
 * أن تُغلق SendActivity.
 */
object PendingChat {
    private const val PREFS = "pending_chat"
    /** بعد هذا الوقت لا نلمس واتساب (ربما فتحه المستخدم لشيء آخر) */
    private const val VALID_MS = 30_000L

    fun set(context: Context, phoneDigits: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("phone", phoneDigits)
            .putLong("at", System.currentTimeMillis())
            .apply()
    }

    /** رقم العميل إن كان الطلب حديثًا، وإلا null */
    fun get(context: Context): String? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong("at", 0)
        if (System.currentTimeMillis() - at > VALID_MS) return null
        return p.getString("phone", null)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
