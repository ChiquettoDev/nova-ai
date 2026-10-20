package com.nova.ai

import android.content.Context
import android.webkit.JavascriptInterface
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class NovaBridge(private val context: Context) {
    @JavascriptInterface
    fun health(): String {
        return try {
            val req = Request.Builder().url("${Api.baseUrl}/api/health").build()
            val res = OkHttpClient().newCall(req).execute()
            res.body?.string() ?: "{}"
        } catch (e: Exception) {
            "{\"ok\":false,\"error\":\"${e.message}\"}"
        }
    }

    @JavascriptInterface
    fun chat(messagesJson: String, model: String): String {
        return try {
            val arr = JSONArray(messagesJson)
            val bodyJson = JSONObject()
            bodyJson.put("messages", arr)
            bodyJson.put("model", model)
            bodyJson.put("stream", false)
            val reqBody = bodyJson.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("${Api.baseUrl}/api/chat").post(reqBody).build()
            val res = OkHttpClient().newCall(req).execute()
            val body = res.body?.string() ?: ""
            // parse SSE or JSON
            if (body.contains("data:")) {
                val sb = StringBuilder()
                body.lines().forEach { line ->
                    val t = line.trim()
                    if (t.startsWith("data:")) {
                        val payload = t.substring(5).trim()
                        if (payload == "[DONE]") return@forEach
                        try {
                            val json = JSONObject(payload)
                            sb.append(json.optString("content", ""))
                        } catch (_: Exception) {}
                    }
                }
                JSONObject().put("content", sb.toString()).toString()
            } else {
                body
            }
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    @JavascriptInterface
    fun generateImage(prompt: String): String {
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        return "https://image.pollinations.ai/prompt/$encoded?width=768&height=768&nologo=true"
    }

    @JavascriptInterface
    fun getBaseUrl(): String = Api.baseUrl

    @JavascriptInterface
    fun setBaseUrl(url: String) {
        Api.baseUrl = url.trim().trimEnd('/')
        context.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putString("base_url", Api.baseUrl).apply()
    }
}
