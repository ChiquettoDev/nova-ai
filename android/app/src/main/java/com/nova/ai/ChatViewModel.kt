package com.nova.ai

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ChatViewModel : ViewModel() {
    val messages = mutableStateListOf<ChatMessage>()
    var input by mutableStateOf("")
    var isStreaming by mutableStateOf(false)
    var status by mutableStateOf("Conectando...")
    var online by mutableStateOf(false)
    var model by mutableStateOf("nova")
    var availableModels = mutableStateListOf<String>()
    var pendingImage by mutableStateOf<String?>(null)
    var offlineStatus by mutableStateOf("Modelo não baixado")

    private var llm: LlmInference? = null
    private var wikiData: String = ""

    init {
        refreshHealth()
    }

    fun refreshHealth() {
        Api.health { ok, models ->
            online = ok
            status = if (ok) "Nova online" else "Ollama offline"
            if (models.isNotEmpty()) {
                availableModels.clear()
                availableModels.addAll(models)
                if (models.contains("nova")) model = "nova" else model = models.first()
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || isStreaming) return
        messages.add(ChatMessage("user", text))
        messages.add(ChatMessage("assistant", "", streaming = true))
        isStreaming = true
        val history = messages.dropLast(1).map { ChatMessage(it.role, it.content) }
        Api.streamChat(history, model,
            onToken = { token ->
                viewModelScope.launch(Dispatchers.Main) {
                    val last = messages.lastIndex
                    messages[last] = messages[last].copy(content = messages[last].content + token, streaming = true)
                }
            },
            onDone = {
                viewModelScope.launch(Dispatchers.Main) {
                    val last = messages.lastIndex
                    messages[last] = messages[last].copy(streaming = false)
                    isStreaming = false
                }
            },
            onError = { err ->
                viewModelScope.launch(Dispatchers.Main) {
                    val last = messages.lastIndex
                    messages[last] = messages[last].copy(content = "Erro: $err", streaming = false)
                    isStreaming = false
                }
            }
        )
    }

    fun generateImage(prompt: String) {
        Api.generateImage(prompt) { url ->
            viewModelScope.launch(Dispatchers.Main) {
                pendingImage = url
                messages.add(ChatMessage("assistant", "Imagem gerada:\n![img]($url)"))
            }
        }
    }

    fun sendOffline(text: String, context: Context) {
        if (text.isBlank() || isStreaming) return
        messages.add(ChatMessage("user", text))
        messages.add(ChatMessage("assistant", "", streaming = true))
        isStreaming = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelFile = ensureModel(context)
                if (llm == null) {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(512)
                        .build()
                    llm = LlmInference.createFromOptions(context, options)
                }
                if (wikiData.isEmpty()) wikiData = loadWiki(context)
                val prompt = buildOfflinePrompt(text, wikiData)
                val response = llm!!.generateResponse(prompt)
                withContext(Dispatchers.Main) {
                    val last = messages.lastIndex
                    messages[last] = messages[last].copy(content = response, streaming = false)
                    isStreaming = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val last = messages.lastIndex
                    messages[last] = messages[last].copy(content = "Modo offline indisponível: ${e.message}\n\nBaixe o modelo Gemma 2B (~1.5GB) e tente novamente.", streaming = false)
                    isStreaming = false
                }
            }
        }
    }

    private fun ensureModel(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "gemma-2b-it-gpu-int4.bin")
        if (file.exists() && file.length() > 1_000_000_000) return file
        offlineStatus = "Baixando modelo..."
        val url = URL("https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-gpu-int4/float32/1/gemma-2b-it-gpu-int4.bin")
        val conn = url.openConnection() as HttpURLConnection
        conn.connect()
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                input.copyTo(out)
            }
        }
        offlineStatus = "Modelo pronto"
        return file
    }

    private fun loadWiki(context: Context): String {
        return try {
            context.assets.open("wikipedia.json").bufferedReader().readText()
        } catch (_: Exception) { "" }
    }

    private fun buildOfflinePrompt(question: String, wiki: String): String {
        val sb = StringBuilder()
        sb.append("Você é a Nova, assistente de IA offline. Responda em português, de forma clara e útil.\n")
        if (wiki.isNotEmpty()) {
            // simple keyword match
            try {
                val arr = JSONArray(wiki)
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
        }
        sb.append("\nPergunta: $question\nResposta:")
        return sb.toString()
    }
}
