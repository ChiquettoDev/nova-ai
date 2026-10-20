package com.nova.ai

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private lateinit var messagesContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var statusText: TextView
    private lateinit var offlineSwitch: Switch
    private lateinit var settingsButton: Button

    private var offline = false
    private var isStreaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        messagesContainer = findViewById(R.id.messagesContainer)
        scrollView = findViewById(R.id.scrollView)
        inputField = findViewById(R.id.inputField)
        sendButton = findViewById(R.id.sendButton)
        statusText = findViewById(R.id.statusText)
        offlineSwitch = findViewById(R.id.offlineSwitch)
        settingsButton = findViewById(R.id.settingsButton)

        val prefs = getSharedPreferences("nova", Context.MODE_PRIVATE)
        Api.baseUrl = prefs.getString("base_url", "https://nova-ai.onrender.com") ?: "https://nova-ai.onrender.com"
        offline = prefs.getBoolean("offline", false)
        offlineSwitch.isChecked = offline

        statusText.text = if (offline) "Offline" else "Conectando..."
        if (!offline) refreshHealth()

        sendButton.setOnClickListener {
            val t = inputField.text.toString().trim()
            if (t.isEmpty() || isStreaming) return@setOnClickListener
            inputField.setText("")
            if (offline) sendOffline(t) else if (t.contains("imagem", ignoreCase = true)) {
                val prompt = t.replace(Regex("(?i)(crie|gere|desenhe|uma|imagem|de)"), "").trim().ifBlank { t }
                generateImage(prompt)
            } else send(t)
        }

        offlineSwitch.setOnCheckedChangeListener { _, checked ->
            offline = checked
            prefs.edit().putBoolean("offline", checked).apply()
            statusText.text = if (checked) "Offline" else "Conectando..."
            if (!checked) refreshHealth()
        }

        settingsButton.setOnClickListener {
            Toast.makeText(this, "URL: ${Api.baseUrl}", Toast.LENGTH_LONG).show()
        }

        addMessage("assistant", "Olá, eu sou a Nova. Como posso ajudar hoje?")
    }

    private fun refreshHealth() {
        Thread {
            Api.health { ok, _ ->
                runOnUiThread { statusText.text = if (ok) "Nova online" else "Ollama offline" }
            }
        }.start()
    }

    private fun addMessage(role: String, content: String): TextView {
        val tv = TextView(this)
        tv.text = (if (role == "user") "Você: " else "Nova: ") + content
        tv.textSize = 15f
        tv.setTextColor(if (role == "user") android.graphics.Color.parseColor("#10A37F") else android.graphics.Color.WHITE)
        tv.setPadding(0, 8, 0, 8)
        messagesContainer.addView(tv)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        return tv
    }

    private fun send(text: String) {
        addMessage("user", text)
        val assistantTv = addMessage("assistant", "")
        isStreaming = true
        sendButton.isEnabled = false
        Thread {
            val history = listOf(ChatMessage("user", text))
            Api.streamChat(history, "nova",
                onToken = { token ->
                    runOnUiThread { assistantTv.text = "Nova: " + (assistantTv.text.toString().removePrefix("Nova: ") + token) }
                },
                onDone = {
                    runOnUiThread { isStreaming = false; sendButton.isEnabled = true }
                },
                onError = { err ->
                    runOnUiThread { assistantTv.text = "Nova: Erro: $err"; isStreaming = false; sendButton.isEnabled = true }
                }
            )
        }.start()
    }

    private fun sendOffline(text: String) {
        addMessage("user", text)
        addMessage("assistant", "Modo offline: baixe o modelo Gemma 2B para ativar (veja instruções).")
    }

    private fun generateImage(prompt: String) {
        addMessage("user", prompt)
        Api.generateImage(prompt) { url ->
            runOnUiThread { addMessage("assistant", "Imagem gerada: $url") }
        }
    }
}
