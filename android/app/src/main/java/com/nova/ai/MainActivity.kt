package com.nova.ai

import android.content.Context
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nova", Context.MODE_PRIVATE)
        Api.baseUrl = prefs.getString("base_url", "https://nova-ai.onrender.com") ?: "https://nova-ai.onrender.com"
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val f = java.io.File(getExternalFilesDir(null), "crash.log")
                f.appendText("Thread: ${thread.name}\n${android.util.Log.getStackTraceString(throwable)}\n---\n")
            } catch (_: Exception) {}
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF212121)) {
                    ChatScreen()
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showSettings by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(context.getSharedPreferences("nova", Context.MODE_PRIVATE).getBoolean("offline", false)) }

    LaunchedEffect(vm.messages.size, vm.messages.lastOrNull()?.content) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF212121))) {
        // Header estilo ChatGPT
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF212121)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Nova", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (offline) "Offline" else vm.status, color = Color(0xFFB4B4B4), fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = offline,
                    onCheckedChange = {
                        offline = it
                        context.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putBoolean("offline", it).apply()
                        if (!it) vm.refreshHealth()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF10A37F))
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showSettings = true }) {
                Text("⚙", color = Color(0xFFB4B4B4), fontSize = 18.sp)
            }
            IconButton(onClick = { showMap = true }) {
                Text("🗺", color = Color(0xFFB4B4B4), fontSize = 18.sp)
            }
        }
        if (offline && vm.downloadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { vm.downloadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF10A37F),
                trackColor = Color(0xFF2F2F2F)
            )
        }
        HorizontalDivider(color = Color(0xFF2F2F2F))

        if (showSettings) {
            var url by remember { mutableStateOf(Api.baseUrl) }
            AlertDialog(
                onDismissRequest = { showSettings = false },
                containerColor = Color(0xFF2F2F2F),
                title = { Text("Servidor", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL do backend") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10A37F),
                            unfocusedBorderColor = Color(0xFF565656),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        Api.baseUrl = url.trim().trimEnd('/')
                        context.getSharedPreferences("nova", Context.MODE_PRIVATE).edit().putString("base_url", Api.baseUrl).apply()
                        vm.refreshHealth()
                        showSettings = false
                    }) { Text("Salvar", color = Color(0xFF10A37F)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSettings = false }) { Text("Cancelar", color = Color(0xFFB4B4B4)) }
                }
            )
        }

        if (showMap) {
            AlertDialog(
                onDismissRequest = { showMap = false },
                containerColor = Color(0xFF2F2F2F),
                title = { Text("Mapa", color = Color.White) },
                text = {
                    AndroidView(factory = { ctx ->
                        try {
                            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osm", Context.MODE_PRIVATE))
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                controller.setZoom(12.0)
                                controller.setCenter(GeoPoint(-23.55, -46.63))
                            }
                        } catch (e: Exception) {
                            android.widget.TextView(ctx).apply { text = "Mapa indisponível: ${e.message}" }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(300.dp))
                },
                confirmButton = { TextButton(onClick = { showMap = false }) { Text("Fechar", color = Color(0xFF10A37F)) } }
            )
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (vm.messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nova", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Como posso ajudar hoje?", color = Color(0xFFB4B4B4), fontSize = 16.sp)
                        Spacer(Modifier.height(32.dp))
                        listOf(
                            "Explique uma rede neural",
                            "Escreva um poema sobre o mar",
                            "Crie uma imagem de um gato astronauta"
                        ).forEach { s ->
                            Surface(
                                onClick = { vm.send(s) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2F2F2F)
                            ) {
                                Text(s, color = Color(0xFFD9D9D9), modifier = Modifier.padding(14.dp), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            items(vm.messages) { msg ->
                MessageBubble(msg)
            }
        }

        // Input bar estilo ChatGPT
        Column(modifier = Modifier.background(Color(0xFF212121)).padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2F2F2F), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = vm.input,
                    onValueChange = { vm.input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enviar uma mensagem", color = Color(0xFF8E8E8E)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 4
                )
                IconButton(onClick = {
                    val t = vm.input
                    vm.input = ""
                    if (offline) {
                        vm.sendOffline(t, context)
                    } else if (t.contains("imagem", ignoreCase = true) || t.contains("desenhe", ignoreCase = true)) {
                        val prompt = t.replace(Regex("(?i)(crie|gere|desenhe|uma|imagem|de)"), "").trim().ifBlank { t }
                        vm.generateImage(prompt)
                    } else {
                        vm.send(t)
                    }
                }, enabled = !vm.isStreaming && vm.input.isNotBlank()) {
                    Text("➤", color = if (vm.input.isNotBlank()) Color(0xFF10A37F) else Color(0xFF565656), fontSize = 18.sp)
                }
            }
            Text("Nova pode cometer erros. Verifique informações importantes.", color = Color(0xFF6B6B6B), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier.size(28.dp).background(Color(0xFF10A37F), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) { Text("N", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            val imgRegex = Regex("!\\[.*?\\]\\((.*?)\\)")
            val match = imgRegex.find(msg.content)
            if (match != null) {
                val url = match.groupValues[1]
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF2F2F2F), RoundedCornerShape(8.dp)))
                if (msg.content.replace(match.value, "").trim().isNotEmpty())
                    Text(msg.content.replace(match.value, "").trim(), color = Color(0xFFD9D9D9), fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            } else {
                Text(msg.content.ifBlank { if (msg.streaming) "" else "" }, color = Color(0xFFD9D9D9), fontSize = 15.sp, lineHeight = 22.sp)
            }
            if (msg.streaming) Text("▋", color = Color(0xFF10A37F))
        }
        if (isUser) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(28.dp).background(Color(0xFF565656), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) { Text("V", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}
