package com.nova.ai

data class ChatMessage(
    val role: String,
    val content: String,
    val streaming: Boolean = false
)
