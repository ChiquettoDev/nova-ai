import React, { useState, useEffect, useRef, useCallback } from 'react'
import Sidebar from './components/Sidebar.jsx'
import ChatMessage from './components/ChatMessage.jsx'
import ChatInput from './components/ChatInput.jsx'
import EmptyState from './components/EmptyState.jsx'
import { health, models } from './lib/api.js'
import { streamChat } from './lib/api.js'

const STORAGE_KEY = 'nova.conversations.v1'

function uid() { return Math.random().toString(36).slice(2) + Date.now().toString(36) }

function loadConversations() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch { return [] }
}

function saveConversations(list) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(list)) } catch {}
}

function titleFrom(messages) {
  const first = messages.find((m) => m.role === 'user')
  if (!first) return 'Nova conversa'
  const t = first.content.trim().replace(/\s+/g, ' ')
  return t.length > 40 ? t.slice(0, 40) + '…' : t
}

export default function App() {
  const [conversations, setConversations] = useState(loadConversations)
  const [activeId, setActiveId] = useState(null)
  const [streaming, setStreaming] = useState(false)
  const [error, setError] = useState(null)
  const [ollamaOk, setOllamaOk] = useState(null)
  const [availableModels, setAvailableModels] = useState([])
  const [selectedModel, setSelectedModel] = useState('nova')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const abortRef = useRef(null)
  const scrollRef = useRef(null)

  const active = conversations.find((c) => c.id === activeId) || null
  const messages = active ? active.messages : []

  useEffect(() => { saveConversations(conversations) }, [conversations])

  useEffect(() => {
    health().then((h) => {
      setOllamaOk(!!h.ok)
      if (h.models?.length) setAvailableModels(h.models)
    }).catch(() => setOllamaOk(false))
    models().then((m) => {
      if (m.models?.length) {
        setAvailableModels(m.models)
        if (m.models.includes('nova')) setSelectedModel('nova')
        else setSelectedModel(m.models[0])
      }
    }).catch(() => {})
  }, [])

  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages, streaming])

  const newConversation = useCallback(() => {
    const c = { id: uid(), title: 'Nova conversa', messages: [], createdAt: Date.now() }
    setConversations((prev) => [c, ...prev])
    setActiveId(c.id)
    setSidebarOpen(false)
  }, [])

  const deleteConversation = useCallback((id) => {
    setConversations((prev) => prev.filter((c) => c.id !== id))
    if (activeId === id) setActiveId(null)
  }, [activeId])

  const updateActive = useCallback((updater) => {
    setConversations((prev) => prev.map((c) => {
      if (c.id !== activeId) return c
      const next = typeof updater === 'function' ? updater(c) : updater
      return { ...next, title: titleFrom(next.messages) }
    }))
  }, [activeId])

  const send = useCallback(async (text) => {
    if (streaming) return
    const lower = text.toLowerCase()
    if (lower.includes('imagem') || lower.includes('desenhe') || lower.includes('gere uma') || lower.includes('crie uma imagem')) {
      const prompt = text.replace(/crie|gere|desenhe|uma|imagem|de/gi, '').trim() || text
      // add user msg
      let convId = activeId
      if (!convId) {
        const c = { id: uid(), title: 'Nova conversa', messages: [], createdAt: Date.now() }
        setConversations((prev) => [c, ...prev])
        convId = c.id
        setActiveId(c.id)
      }
      setConversations((prev) => prev.map((c) => c.id === convId ? { ...c, messages: [...c.messages, { role: 'user', content: text }] } : c))
      try {
        const r = await fetch(`/api/image?prompt=${encodeURIComponent(prompt)}`)
        const data = await r.json()
        setConversations((prev) => prev.map((c) => c.id === convId ? { ...c, messages: [...c.messages, { role: 'assistant', content: `![imagem](${data.url})\n\n*Prompt: ${prompt}*` }] } : c))
      } catch (e) { setError(String(e)) }
      return
    }
    setError(null)
    let convId = activeId
    if (!convId) {
      const c = { id: uid(), title: 'Nova conversa', messages: [], createdAt: Date.now() }
      setConversations((prev) => [c, ...prev])
      convId = c.id
      setActiveId(c.id)
    }
    const userMsg = { role: 'user', content: text }
    setConversations((prev) => prev.map((c) => {
      if (c.id !== convId) return c
      const msgs = [...c.messages, userMsg]
      return { ...c, messages: msgs, title: titleFrom(msgs) }
    }))

    const currentMessages = [...(conversations.find((c) => c.id === convId)?.messages || []), userMsg]
    // ensure active state messages
    const baseMsgs = currentMessages

    setStreaming(true)
    const assistantMsg = { role: 'assistant', content: '' }
    setConversations((prev) => prev.map((c) => {
      if (c.id !== convId) return c
      return { ...c, messages: [...c.messages, assistantMsg] }
    }))

    const controller = new AbortController()
    abortRef.current = controller

    const history = baseMsgs.map((m) => ({ role: m.role, content: m.content }))
    try {
      let acc = ''
      for await (const token of streamChat(history, { model: selectedModel, signal: controller.signal })) {
        acc += token
        setConversations((prev) => prev.map((c) => {
          if (c.id !== convId) return c
          const msgs = c.messages.map((m, i) =>
            i === c.messages.length - 1 && m.role === 'assistant'
              ? { ...m, content: acc }
              : m
          )
          return { ...c, messages: msgs }
        }))
      }
    } catch (e) {
      if (e.name !== 'AbortError') setError(String(e.message || e))
      // remove empty assistant if aborted/errored with no content? keep
    } finally {
      setStreaming(false)
      abortRef.current = null
    }
  }, [activeId, conversations, selectedModel, streaming])

  const stop = () => abortRef.current?.abort()

  return (
    <div className="h-full flex overflow-hidden">
      <Sidebar
        conversations={conversations}
        activeId={activeId}
        onSelect={(id) => { setActiveId(id); setSidebarOpen(false) }}
        onNew={newConversation}
        onDelete={deleteConversation}
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      <main className="flex-1 flex flex-col min-w-0">
        {/* top bar */}
        <header className="flex items-center gap-3 px-4 md:px-6 py-3 border-b border-ink-700/60 glass">
          <button
            onClick={() => setSidebarOpen(true)}
            className="md:hidden text-ink-300 hover:text-ink-100 text-xl"
          >
            ☰
          </button>
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full" style={{ background: ollamaOk ? '#34d399' : '#f87171' }} />
            <span className="text-sm text-ink-300">
              {ollamaOk === null ? 'Conectando...' : ollamaOk ? 'Nova online' : 'Ollama offline'}
            </span>
          </div>
          <div className="ml-auto flex items-center gap-2">
            {availableModels.length > 0 && (
              <select
                value={selectedModel}
                onChange={(e) => setSelectedModel(e.target.value)}
                className="bg-ink-800/80 border border-ink-700/60 text-ink-200 text-xs rounded-lg px-2 py-1.5 outline-none focus:border-nova-500"
              >
                {availableModels.map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
            )}
          </div>
        </header>

        {/* messages */}
        <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 md:px-8 py-6">
          <div className="max-w-3xl mx-auto space-y-6">
            {messages.length === 0 ? (
              <EmptyState onPick={send} />
            ) : (
              messages.map((m, i) => (
                <ChatMessage
                  key={i}
                  role={m.role}
                  content={m.content}
                  streaming={streaming && i === messages.length - 1 && m.role === 'assistant'}
                />
              ))
            )}
            {error && (
              <div className="rounded-xl border border-red-500/40 bg-red-500/10 text-red-300 text-sm px-4 py-3">
                Erro: {error}
              </div>
            )}
          </div>
        </div>

        {/* input */}
        <div className="px-4 md:px-8 py-4 border-t border-ink-700/60 glass">
          <div className="max-w-3xl mx-auto">
            {streaming && (
              <div className="flex justify-end mb-2">
                <button
                  onClick={stop}
                  className="text-xs px-3 py-1 rounded-lg bg-ink-800 border border-ink-700 text-ink-300 hover:text-ink-100 transition"
                >
                  ⏹ Parar
                </button>
              </div>
            )}
            <ChatInput onSend={send} disabled={streaming} />
            <p className="text-center text-[11px] text-ink-500 mt-2">
              Nova pode cometer erros. Verifique informações importantes.
            </p>
          </div>
        </div>
      </main>
    </div>
  )
}
