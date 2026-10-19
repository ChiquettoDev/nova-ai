const BASE = ''

export async function health() {
  const r = await fetch(`${BASE}/api/health`)
  return r.json()
}

export async function models() {
  const r = await fetch(`${BASE}/api/models`)
  return r.json()
}

export async function* streamChat(messages, { model, signal, onToken } = {}) {
  const res = await fetch(`${BASE}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messages, model }),
    signal
  })
  if (!res.ok) {
    const txt = await res.text()
    throw new Error(`HTTP ${res.status}: ${txt}`)
  }
  const reader = res.body.getReader()
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
      if (payload === '[DONE]') return
      try {
        const json = JSON.parse(payload)
        if (json.error) throw new Error(json.error)
        if (json.content) {
          if (onToken) onToken(json.content)
          yield json.content
        }
        if (json.done) return
      } catch (e) {
        if (e instanceof SyntaxError) continue
        throw e
      }
    }
  }
}
