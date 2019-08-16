package ru.yourok.torrserve.ad

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import ru.yourok.torrserve.ad.model.AdJson
import ru.yourok.torrserve.utils.Http
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread


class Ad(private val iv: ImageView, private val activity: Activity) {
    private val ad_base = "http://tor-serve.surge.sh"
    private val lock = Any()

    init {
        iv.visibility = View.GONE
    }

    fun get() {
        synchronized(lock) {}
        thread {
            synchronized(lock) {
                val jsAd = getJson()
                jsAd?.let { js ->
                    try {
                        if (js.ad_expired != "0") {
                            val formatter = SimpleDateFormat("dd.MM.yyyy")
                            val date = formatter.parse(js.ad_expired) as Date
                            if (date.time < System.currentTimeMillis())
                                return@thread
                        }
                        if (js.ad_link.isNotEmpty()) {
                            loadImages(js.ad_link)
                            FirebaseAnalytics.getInstance(activity).logEvent("view_ad", null)
                            Thread.sleep(5000)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun waitAd() {
        synchronized(lock) {}
    }

    private fun loadImages(lst: List<String>) {
        thread {
            Handler(Looper.getMainLooper()).post {
                iv.visibility = View.VISIBLE
            }
            while (!activity.isFinishing) {
                lst.forEach {
                    var link = it
                    if (!link.startsWith("http"))
                        link = ad_base + link

                    val pcs = Picasso.get()
                            .load(link)
                            .placeholder(iv.drawable)
                            .noFade()

                    Handler(Looper.getMainLooper()).post {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            iv.animate().alpha(0f).setDuration(200)
                                    .withEndAction {
                                        pcs.into(iv, object : Callback {
                                            override fun onSuccess() {
                                                iv.animate().setDuration(200).alpha(1f).start()
                                            }

                                            override fun onError(e: java.lang.Exception?) {
                                            }
                                        })
                                    }
                                    .start()
                        } else {
                            pcs.into(iv)
                        }
                    }
                    Thread.sleep(5000)
                }
            }
        }
    }

    private fun getJson(): AdJson? {
        try {
            val http = Http("$ad_base/ad.json")
            val body = http.read()

            val gson = Gson()
            return gson.fromJson(body, AdJson::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}