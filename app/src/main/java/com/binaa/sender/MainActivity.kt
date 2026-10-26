package com.binaa.sender

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * شاشة الحالة والتجربة فقط. الاستخدام اليومي لا يمر من هنا: binaa hub يفتح
 * SendActivity مباشرة.
 */
class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var bubbleStatus: TextView
    private lateinit var bubbleButton: Button
    private lateinit var pickerStatus: TextView
    private lateinit var pickerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Binaa Sender"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "يعمل تلقائيًا من صفحة «فرز وإرسال» في binaa hub: يفتح محادثة العميل في واتساب بزنس وصورة الفاتورة مرفقة، وأنت تضغط «إرسال» فقط."
            setPadding(0, pad / 2, 0, pad)
        })

        statusText = TextView(this).apply { textSize = 16f }
        root.addView(statusText)

        // زر «⬅ بناء» فوق واتساب يحتاج هذا الإذن مرة واحدة
        bubbleStatus = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad / 2, 0, 0)
        }
        root.addView(bubbleStatus)
        bubbleButton = Button(this).apply {
            text = "تفعيل زر «⬅ بناء» فوق واتساب"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }
        }
        root.addView(bubbleButton)

        // واتساب بزنس يفتح «Envoyer à…» بدل محادثة العميل: الخدمة تختارها
        pickerStatus = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad / 2, 0, 0)
        }
        root.addView(pickerStatus)
        pickerButton = Button(this).apply {
            text = "تفعيل اختيار محادثة العميل تلقائيًا"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(pickerButton)

        root.addView(TextView(this).apply {
            text = "تجربة: أدخل رقمك (8 أرقام) لفتح محادثته بصورة تجريبية"
            setPadding(0, pad, 0, pad / 4)
        })
        val phoneInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "مثال: 22123456"
            gravity = Gravity.START
        }
        root.addView(phoneInput)
        root.addView(Button(this).apply {
            text = "فتح محادثة تجريبية"
            setOnClickListener { runTest(phoneInput.text.toString()) }
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        statusText.text = if (WhatsAppBusiness.isInstalled(this)) {
            "واتساب بزنس: مثبت ✅"
        } else {
            "واتساب بزنس: غير مثبت ❌"
        }
        val bubbleOk = ReturnBubble.canShow(this)
        bubbleStatus.text = if (bubbleOk) {
            "زر «⬅ بناء» للرجوع بعد الإرسال: مفعّل ✅"
        } else {
            "زر «⬅ بناء» للرجوع بعد الإرسال: غير مفعّل ❌"
        }
        bubbleButton.visibility = if (bubbleOk) View.GONE else View.VISIBLE
        val pickerOk = ChatPickerService.isEnabled(this)
        pickerStatus.text = if (pickerOk) {
            "اختيار محادثة العميل تلقائيًا: مفعّل ✅"
        } else {
            "اختيار محادثة العميل تلقائيًا: غير مفعّل ❌ (الإعدادات ← تسهيل الاستخدام ← Binaa Sender)"
        }
        pickerButton.visibility = if (pickerOk) View.GONE else View.VISIBLE
    }

    private fun runTest(rawPhone: String) {
        val phone = PhoneNumber.toWhatsAppDigits(rawPhone)
        if (phone == null) {
            Toast.makeText(this, "رقم غير صالح", Toast.LENGTH_LONG).show()
            return
        }
        if (!WhatsAppBusiness.isInstalled(this)) {
            Toast.makeText(this, "واتساب بزنس غير مثبت", Toast.LENGTH_LONG).show()
            return
        }
        OutgoingFiles.cleanupOld(this)
        val (uri, mime) = OutgoingFiles.saveTestImage(this, buildTestImage())
        try {
            PendingChat.set(this, phone)
            startActivity(WhatsAppBusiness.buildIntent(uri, mime, phone, "رسالة تجريبية من Binaa Sender"))
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر فتح واتساب بزنس", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildTestImage(): Bitmap {
        val bmp = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 56f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("فاتورة تجريبية", 400f, 230f, paint)
        paint.textSize = 32f
        canvas.drawText("Binaa Sender", 400f, 300f, paint)
        return bmp
    }
}
