package com.darkxvenom.itune.backend

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** One HTTP client for the whole app: extractor requests and media playback share it. */
object Net {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (!response.isSuccessful && response.code == 404) {
                    val urlStr = request.url.toString()
                    if (request.url.host.contains("ytimg.com") && urlStr.contains("/maxresdefault.jpg")) {
                        response.close()
                        val hq720Url = urlStr.replace("/maxresdefault.jpg", "/hq720.jpg")
                        val hq720Req = request.newBuilder().url(hq720Url).build()
                        val hq720Resp = chain.proceed(hq720Req)
                        if (hq720Resp.isSuccessful) {
                            return@addInterceptor hq720Resp
                        }
                        hq720Resp.close()
                        val hqReq = request.newBuilder().url(urlStr.replace("/maxresdefault.jpg", "/hqdefault.jpg")).build()
                        return@addInterceptor chain.proceed(hqReq)
                    } else if (urlStr.contains("googleusercontent.com") && urlStr.contains("=w1440-h1440-l90-rj")) {
                        response.close()
                        val fallbackReq = request.newBuilder().url(urlStr.substringBeforeLast("=")).build()
                        return@addInterceptor chain.proceed(fallbackReq)
                    }
                }
                response
            }
            .build()
    }

    @Volatile private var mediaHost: String? = null

    fun noteMediaHost(url: String?) {
        url?.toHttpUrlOrNull()?.host?.let { mediaHost = it }
    }

    fun prewarmMediaConnection() {
        val host = mediaHost ?: return
        val request = okhttp3.Request.Builder().url("https://$host/generate_204").head().header("User-Agent", USER_AGENT).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) = response.close()
        })
    }
}

/** Bridges NewPipeExtractor's HTTP abstraction onto OkHttp. */
class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .addHeader("User-Agent", Net.USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            val text = response.body?.string()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                if (PlayerFallback.handles(request.url())) PlayerFallback.substitute(request.url(), request.dataToSend(), text) else text,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        val instance: NewPipeDownloader by lazy { NewPipeDownloader(Net.client) }
    }
}
