package com.binaa.sender

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.io.IOException

/**
 * بلا واجهة: يستقبل الطلب من binaa hub، ينزّل الصورة، يفتح واتساب بزنس، ثم ينتظر.
 * حين يعود واتساب (بعد ضغط «إرسال»، أو بزر الرجوع) يُغلق، فيرجع المستخدم إلى
 * صفحة «فرز وإرسال» في المتصفح ليكمل الفاتورة التالية.
 */
class SendActivity : Activity() {

    private var waitingForWhatsApp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (request, error) = SendRequest.parse(intent?.data)
        if (request == null) {
            fail(error ?: "طلب غير صالح")
            return
        }
        if (!WhatsAppBusiness.isInstalled(this)) {
            fail("واتساب بزنس غير مثبت على هذا الهاتف")
            return
        }

        Toast.makeText(this, "جارٍ تجهيز الفاتورة…", Toast.LENGTH_SHORT).show()
        val appContext = applicationContext
        Thread {
            OutgoingFiles.cleanupOld(appContext)
            val result: Result<Pair<Uri, String>> = try {
                Result.success(OutgoingFiles.download(appContext, request.imageUrl))
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(IOException("تعذر تنزيل الصورة"))
            }
            runOnUiThread {
                result.fold(
                    onSuccess = { (uri, mime) ->
                        try {
                            startActivityForResult(
                                WhatsAppBusiness.buildIntent(uri, mime, request.phoneDigits, request.text),
                                REQUEST_WHATSAPP,
                            )
                            waitingForWhatsApp = true
                            if (ReturnBubble.canShow(this)) {
                                ReturnBubble.show(this, request.returnUrl)
                            } else {
                                Toast.makeText(
                                    this,
                                    "لزر «⬅ بناء»: افتح تطبيق Binaa Sender وفعّل الإذن",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "تعذر فتح واتساب بزنس", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    },
                    onFailure = { fail(it.message ?: "تعذر تنزيل الصورة") }
                )
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // واتساب انتهى (أُرسلت الصورة أو رجع المستخدم): نُغلق فيظهر المتصفح
        if (requestCode == REQUEST_WHATSAPP) finish()
    }

    override fun onResume() {
        super.onResume()
        // احتياط: عند العودة من واتساب لا نبقى شاشة شفافة فوق المتصفح
        if (waitingForWhatsApp) finish()
    }

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private companion object {
        const val REQUEST_WHATSAPP = 1
    }
}
