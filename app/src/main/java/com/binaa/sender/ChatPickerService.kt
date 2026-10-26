package com.binaa.sender

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * واتساب بزنس صار يتجاهل رقم المحادثة ("jid") ويفتح قائمة «Envoyer à…». هذه
 * الخدمة تختار محادثة العميل في تلك القائمة بدل المستخدم:
 * بحث ← كتابة رقمه ← اختيار النتيجة إن كانت واحدة فقط.
 *
 * لا ترسل شيئًا أبدًا: المستخدم هو من يضغط زر الإرسال. وإن لم تجد نتيجة واحدة
 * بالضبط (رقم غير موجود، أو أكثر من محادثة) تتوقف ويختار المستخدم بنفسه.
 * تعمل فقط خلال 30 ثانية من ضغط «إرسال» في «بناء»، وفقط داخل واتساب بزنس.
 */
class ChatPickerService : AccessibilityService() {

    private enum class Step { SEARCH, TYPE, PICK }

    private var step = Step.SEARCH
    private var phone: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != WhatsAppBusiness.PACKAGE) return
        val pending = PendingChat.get(this)
        if (pending == null) {
            phone = null
            return
        }
        if (pending != phone) {
            phone = pending
            step = Step.SEARCH
        }
        // الأحداث كثيرة ومتلاحقة: نعمل مرة واحدة بعد أن تهدأ الشاشة قليلًا
        if (!scheduled) {
            scheduled = true
            handler.postDelayed({
                scheduled = false
                runCatching { advance() }
            }, 250)
        }
    }

    private fun advance() {
        val number = phone ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName != WhatsAppBusiness.PACKAGE) return
        // نعمل فقط في قائمة اختيار المحادثة (فيها عنوان «Envoyer à…»/«Send to…»)
        if (step == Step.SEARCH && !isPicker(root)) return

        when (step) {
            Step.SEARCH -> {
                val search = findSearchButton(root) ?: return
                if (clickNode(search)) step = Step.TYPE
            }
            Step.TYPE -> {
                val input = findEditText(root) ?: return
                val args = Bundle().apply {
                    // البحث بآخر 8 أرقام: يطابق «+222 22 10 80 99» و«22108099»
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        number.takeLast(8),
                    )
                }
                if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) step = Step.PICK
            }
            Step.PICK -> {
                val rows = findResultRows(root)
                when {
                    rows.size == 1 -> {
                        if (clickNode(rows[0])) done(null)
                    }
                    rows.size > 1 -> done("أكثر من محادثة بهذا الرقم — اختر بنفسك")
                    // لا نتيجة بعد: ربما لم تظهر النتائج بعد، ننتظر الحدث التالي
                }
            }
        }
    }

    private fun done(message: String?) {
        PendingChat.clear(this)
        phone = null
        step = Step.SEARCH
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun isPicker(root: AccessibilityNodeInfo): Boolean =
        PICKER_TITLES.any { root.findAccessibilityNodeInfosByText(it).isNotEmpty() }

    private fun findSearchButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in SEARCH_IDS) {
            root.findAccessibilityNodeInfosByViewId("${WhatsAppBusiness.PACKAGE}:id/$id").firstOrNull()
                ?.let { return it }
        }
        return findFirst(root) { n ->
            val d = n.contentDescription?.toString()?.trim() ?: return@findFirst false
            SEARCH_LABELS.any { d.equals(it, ignoreCase = true) }
        }
    }

    private fun findEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findFirst(root) { it.isEditable }

    /** صفوف النتائج: اسم المحادثة في كل صف */
    private fun findResultRows(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        for (id in ROW_IDS) {
            val rows = root.findAccessibilityNodeInfosByViewId("${WhatsAppBusiness.PACKAGE}:id/$id")
            if (rows.isNotEmpty()) return rows
        }
        return emptyList()
    }

    /** يضغط العنصر أو أقرب أب قابل للضغط */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    private fun findFirst(
        node: AccessibilityNodeInfo,
        match: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (match(node)) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findFirst(c, match)?.let { return it }
        }
        return null
    }

    override fun onInterrupt() {}

    companion object {
        private val PICKER_TITLES = listOf("Envoyer à", "Send to", "إرسال إلى")
        private val SEARCH_IDS = listOf("menuitem_search", "search", "action_search")
        private val SEARCH_LABELS = listOf("Rechercher", "Recherche", "Search", "بحث")
        private val ROW_IDS = listOf("contactpicker_row_name", "contact_row_name", "conversations_row_contact_name")

        /** هل فعّل المستخدم الخدمة من إعدادات تسهيل الاستخدام؟ */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any {
                it.equals("${context.packageName}/${ChatPickerService::class.java.name}", ignoreCase = true) ||
                    it.equals("${context.packageName}/.ChatPickerService", ignoreCase = true)
            }
        }
    }
}
