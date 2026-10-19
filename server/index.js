import express from 'express'
import cors from 'cors'
import fs from 'node:fs'
import path from 'node:path'

const app = express()
const PORT = process.env.PORT || 3001

const PROVIDER = (process.env.LLM_PROVIDER || 'ollama').toLowerCase() // ollama | openai
const OLLAMA_URL = process.env.OLLAMA_URL || 'http://127.0.0.1:11434'
const OAI_BASE = process.env.LLM_BASE_URL || 'https://api.groq.com/openai/v1'
const OAI_KEY = process.env.LLM_API_KEY || ''
const DEFAULT_MODEL = process.env.NOVA_MODEL || (PROVIDER === 'ollama' ? 'nova' : 'llama-3.3-70b-versatile')

app.use(cors())
app.use(express.json({ limit: '10mb' }))

app.get('/api/health', async (_req, res) => {
  try {
    if (PROVIDER === 'ollama') {
      const r = await fetch(`${OLLAMA_URL}/api/tags`)
      const data = await r.json()
      return res.json({ ok: true, provider: 'ollama', models: (data.models || []).map((m) => m.name) })
    }
    // openai-compatible: just report configured
    return res.json({ ok: !!OAI_KEY, provider: 'openai', base: OAI_BASE, model: DEFAULT_MODEL, models: [DEFAULT_MODEL] })
  } catch (e) {
    res.status(503).json({ ok: false, error: String(e) })
  }
})

app.get('/api/models', async (_req, res) => {
  try {
    if (PROVIDER === 'ollama') {
      const r = await fetch(`${OLLAMA_URL}/api/tags`)
      const data = await r.json()
      return res.json({ models: (data.models || []).map((m) => m.name) })
    }
    return res.json({ models: [DEFAULT_MODEL] })
  } catch (e) {
    res.status(503).json({ models: [], error: String(e) })
  }
})

app.post('/api/chat', async (req, res) => {
  const { messages = [], model, options = {} } = req.body || {}
  const chosen = model || DEFAULT_MODEL

  res.setHeader('Content-Type', 'text/event-stream')
  res.setHeader('Cache-Control', 'no-cache')
  res.setHeader('Connection', 'keep-alive')
  res.flushHeaders?.()

  try {
    if (PROVIDER === 'ollama') {
      const upstream = await fetch(`${OLLAMA_URL}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: chosen,
          messages,
          stream: true,
          options: {
            temperature: options.temperature ?? 0.7,
            top_p: options.top_p ?? 0.9,
            top_k: options.top_k ?? 40,
            repeat_penalty: options.repeat_penalty ?? 1.1
          }
        })
      })
      if (!upstream.ok || !upstream.body) {
        const txt = await upstream.text().catch(() => '')
        res.write(`data: ${JSON.stringify({ error: `Ollama ${upstream.status}: ${txt}` })}\n\n`)
        res.end(); return
      }
      const reader = upstream.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed) continue
          try {
            const json = JSON.parse(trimmed)
            const content = json?.message?.content || ''
            if (content) res.write(`data: ${JSON.stringify({ content })}\n\n`)
            if (json?.done) res.write(`data: ${JSON.stringify({ done: true, model: chosen })}\n\n`)
          } catch {}
        }
      }
      if (buffer.trim()) {
        try {
          const json = JSON.parse(buffer.trim())
          const content = json?.message?.content || ''
          if (content) res.write(`data: ${JSON.stringify({ content })}\n\n`)
          if (json?.done) res.write(`data: ${JSON.stringify({ done: true, model: chosen })}\n\n`)
        } catch {}
      }
      res.write('data: [DONE]\n\n'); res.end(); return
    }

    // OpenAI-compatible streaming
    const upstream = await fetch(`${OAI_BASE}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${OAI_KEY}`
      },
      body: JSON.stringify({
        model: chosen,
        messages,
        stream: true,
        temperature: options.temperature ?? 0.7,
        top_p: options.top_p ?? 0.9
      })
    })
    if (!upstream.ok || !upstream.body) {
      const txt = await upstream.text().catch(() => '')
      res.write(`data: ${JSON.stringify({ error: `LLM ${upstream.status}: ${txt}` })}\n\n`)
      res.end(); return
    }
    const reader = upstream.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        const t = line.trim()
        if (!t.startsWith('data:')) continue
        const payload = t.slice(5).trim()
        if (payload === '[DONE]') { res.write('data: [DONE]\n\n'); res.end(); return }
        try {
          const json = JSON.parse(payload)
          const content = json?.choices?.[0]?.delta?.content || ''
          if (content) res.write(`data: ${JSON.stringify({ content })}\n\n`)
        } catch {}
      }
    }
    res.write('data: [DONE]\n\n'); res.end()
  } catch (e) {
    res.write(`data: ${JSON.stringify({ error: String(e) })}\n\n`)
    res.end()
  }
})

app.get('/api/image', (req, res) => {
  const prompt = String(req.query.prompt || '').trim()
  if (!prompt) return res.status(400).json({ error: 'prompt required' })
  const encoded = encodeURIComponent(prompt)
  const url = `https://image.pollinations.ai/prompt/${encoded}?width=768&height=768&nologo=true`
  res.json({ url, prompt })
})

app.post('/api/feedback', (req, res) => {
  const { prompt, response, correction, rating } = req.body || {}
  const dir = path.join(process.cwd(), 'data')
  fs.mkdirSync(dir, { recursive: true })
  const line = JSON.stringify({ ts: Date.now(), prompt, response, correction, rating }) + '\n'
  fs.appendFileSync(path.join(dir, 'feedback.jsonl'), line)
  res.json({ ok: true })
})

// Serve built frontend in production
const distPath = path.join(process.cwd(), 'dist')
if (fs.existsSync(distPath)) {
  app.use(express.static(distPath))
  app.get('*', (_req, res) => res.sendFile(path.join(distPath, 'index.html')))
}

app.listen(PORT, () => {
  console.log(`Nova server listening on :${PORT} (provider: ${PROVIDER}, model: ${DEFAULT_MODEL})`)
})
