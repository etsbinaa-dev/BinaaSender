package com.binaa.sender

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * زر عائم «⬅ بناء» يظهر فوق واتساب بزنس بعد فتح محادثة الفاتورة. واتساب لا
 * يرجع وحده إلى التطبيق الذي فتحه، فبعد ضغط «إرسال» يضغط المستخدم هذا الزر
 * فيعود إلى صفحة «فرز وإرسال» ليكمل الفاتورة التالية.
 *
 * يحتاج إذن «الظهور فوق التطبيقات الأخرى» مرة واحدة (من شاشة Binaa Sender).
 * يمكن سحبه لأي مكان، ويختفي وحده بعد 15 دقيقة.
 */
object ReturnBubble {
    private const val AUTO_HIDE_MS = 15 * 60 * 1000L

    private var view: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide() }

    // آخر موضع اختاره المستخدم بالسحب (يبقى للفواتير التالية)
    private var lastX = 0
    private var lastY: Int? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context, returnUrl: String) {
        val app = context.applicationContext
        if (!canShow(app)) return
        hide()

        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = app.resources.displayMetrics.density
        val pad = (14 * density).toInt()

        val bubble = TextView(app).apply {
            text = "⬅ بناء"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(pad, pad / 2, pad, pad / 2)
            background = GradientDrawable().apply {
                cornerRadius = 40 * density
                setColor(0xE6047857.toInt()) // أخضر بِناء، شبه معتم
            }
            elevation = 8 * density
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // أعلى يسار الشاشة افتراضيًا: بعيد عن زر «إرسال» وخانة الكتابة في واتساب
            gravity = Gravity.TOP or Gravity.START
            x = lastX
            y = lastY ?: (app.resources.displayMetrics.heightPixels * 0.18).toInt()
        }

        // سحب لتحريكه، ونقرة (حركة صغيرة) للرجوع
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        val touchSlop = 10 * density
        bubble.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downX).toInt()
                    params.y = startY + (e.rawY - downY).toInt()
                    runCatching { wm.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    lastX = params.x; lastY = params.y
                    if (abs(e.rawX - downX) < touchSlop && abs(e.rawY - downY) < touchSlop) {
                        goBack(app, returnUrl)
                    }
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(bubble, params) }.onSuccess {
            view = bubble
            handler.postDelayed(autoHide, AUTO_HIDE_MS)
        }
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        val v = view ?: return
        view = null
        runCatching {
            (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        }
    }

    private fun goBack(context: Context, returnUrl: String) {
        hide()
        try {
            // رابط الـ Hub نفسه: إن كان مثبتًا كتطبيق (PWA) يُفتح فيه، وإلا في المتصفح.
            // صفحة «فرز وإرسال» تستأنف الإرسال المتتابع من حيث توقف.
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(returnUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح binaa hub", Toast.LENGTH_LONG).show()
        }
    }
}
