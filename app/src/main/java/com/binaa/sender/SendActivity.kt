package com.binaa.sender

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.io.IOException

/**
 * بلا واجهة: يستقبل الطلب من binaa hub، ينزّل الصورة، يفتح واتساب بزنس ويُظهر
 * زر «⬅ بناء» العائم للرجوع، ثم يُغلق فورًا.
 */
class SendActivity : Activity() {

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

        // بلا رسالة «جارٍ التجهيز»: رسائل Toast تظهر باسم التطبيق وأيقونته فتبطئ الإحساس
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
                            // واتساب لا يرجع وحده بعد الإرسال (جُرّب)، فلا ننتظره: نفتحه
                            // ونُغلق فورًا بلا حركة، وزر «⬅ بناء» هو طريق الرجوع
                            startActivity(
                                WhatsAppBusiness.buildIntent(uri, mime, request.phoneDigits, request.text)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            if (ReturnBubble.canShow(this)) {
                                ReturnBubble.show(this, request.returnUrl)
                            } else {
                                Toast.makeText(
                                    this,
                                    "لزر «⬅ بناء»: افتح تطبيق Binaa Sender وفعّل الإذن",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            finish()
                            overridePendingTransition(0, 0)
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

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
        overridePendingTransition(0, 0)
    }
}
