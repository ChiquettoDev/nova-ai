package com.nova.ai

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        val footer = findViewById<LinearLayout>(R.id.inputField).parent as LinearLayout
        ViewCompat.setOnApplyWindowInsetsListener(footer) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, nav.bottom + 8)
            insets
        }

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

        addMessage("assistant", "Ola, eu sou a Nova. Como posso ajudar hoje?")
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
        tv.text = (if (role == "user") "Voce: " else "Nova: ") + content
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
        val assistantTv = addMessage("assistant", "Preparando modelo offline...")
        isStreaming = true
        sendButton.isEnabled = false
        Thread {
            try {
                val modelFile = java.io.File((getExternalFilesDir(null) ?: filesDir), "gemma-2b-it-gpu-int4.bin")
                if (!modelFile.exists() || modelFile.length() < 1000000000) {
                    runOnUiThread { assistantTv.text = "Nova: Baixando modelo (1.5GB)..." }
                    val url = java.net.URL("https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-gpu-int4/float32/1/gemma-2b-it-gpu-int4.bin")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connect()
                    conn.inputStream.use { input ->
                        modelFile.outputStream().use { out -> input.copyTo(out) }
                    }
                }
                runOnUiThread { assistantTv.text = "Nova: Carregando modelo..." }
                val options = com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(512)
                    .build()
                val llm = com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(this@MainActivity, options)
                val wiki = assets.open("wikipedia.json").bufferedReader().readText()
                val prompt = buildOfflinePrompt(text, wiki)
                val response = llm.generateResponse(prompt)
                runOnUiThread { assistantTv.text = "Nova: $response"; isStreaming = false; sendButton.isEnabled = true }
            } catch (e: Exception) {
                runOnUiThread { assistantTv.text = "Nova: Modo offline indisponivel: ${e.message}"; isStreaming = false; sendButton.isEnabled = true }
            }
        }.start()
    }

    private fun buildOfflinePrompt(question: String, wiki: String): String {
        val sb = StringBuilder("Voce e a Nova, assistente de IA offline. Responda em portugues, de forma clara e util.\n")
        try {
            val arr = org.json.JSONArray(wiki)
            val keywords = question.lowercase().split(Regex("\\W+")).filter { it.length > 3 }
            val relevant = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val title = o.optString("title")
                val content = o.optString("content")
                if (keywords.any { title.lowercase().contains(it) || content.lowercase().contains(it) }) {
                    relevant.add("[$title] $content")
                }
            }
            if (relevant.isNotEmpty()) {
                sb.append("\nConhecimento offline (Wikipedia):\n")
                relevant.take(3).forEach { sb.append("- $it\n") }
            }
        } catch (_: Exception) {}
        sb.append("\nPergunta: $question\nResposta:")
        return sb.toString()
    }

    private fun generateImage(prompt: String) {
        addMessage("user", prompt)
        Api.generateImage(prompt) { url ->
            runOnUiThread { addMessage("assistant", "Imagem gerada: $url") }
        }
    }
}
