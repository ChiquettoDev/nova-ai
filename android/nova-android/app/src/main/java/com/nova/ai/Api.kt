package com.nova.ai

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object Api {
    var baseUrl: String = "http://10.0.2.2:3001"

    private val client = OkHttpClient.Builder()
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    fun health(callback: (Boolean, List<String>) -> Unit) {
        val req = Request.Builder().url("$baseUrl/api/health").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { callback(false, emptyList()) }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val models = mutableListOf<String>()
                try {
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("models")
                    if (arr != null) for (i in 0 until arr.length()) models.add(arr.getString(i))
                } catch (_: Exception) {}
                callback(response.isSuccessful && body.contains("\"ok\":true"), models)
            }
        })
    }

    fun streamChat(messages: List<ChatMessage>, model: String, onToken: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        val arr = JSONArray()
        messages.forEach { m ->
            val o = JSONObject()
            o.put("role", m.role)
            o.put("content", m.content)
            arr.put(o)
        }
        val bodyJson = JSONObject()
        bodyJson.put("messages", arr)
        bodyJson.put("model", model)
        val reqBody = bodyJson.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/api/chat").post(reqBody).build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { onError(e.message ?: "erro") }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onError("HTTP ${response.code}")
                    return
                }
                val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val t = line!!.trim()
                        if (!t.startsWith("data:")) continue
                        val payload = t.substring(5).trim()
                        if (payload == "[DONE]") { onDone(); return }
                        try {
                            val json = JSONObject(payload)
                            if (json.has("error")) { onError(json.getString("error")); return }
                            if (json.has("content")) onToken(json.getString("content"))
                            if (json.optBoolean("done", false)) { onDone(); return }
                        } catch (_: Exception) {}
                    }
                    onDone()
                } catch (e: Exception) {
                    onError(e.message ?: "erro")
                } finally {
                    reader.close()
                }
            }
        })
    }

    fun generateImage(prompt: String, callback: (String?) -> Unit) {
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        val url = "https://image.pollinations.ai/prompt/$encoded?width=512&height=512&nologo=true"
        callback(url)
    }
}
