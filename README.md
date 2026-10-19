# Nova — IA própria

Aplicativo de chat estilo ChatGPT, com modelo local (Ollama) e interface original/moderna.

## Estrutura
- `server/` — backend Express (proxy para Ollama, streaming SSE)
- `src/` — frontend React + Vite + Tailwind
- Modelo customizado `nova` (Ollama) definido em `/media/joao/HD/testeia/nova-model/Modelfile`

## Pré-requisitos
- Node.js 18+
- Ollama (binário em `~/.local/ollama/bin/ollama` ou no PATH)
- Modelo `nova` criado (ver abaixo)

## Rodar
```bash
# 1. iniciar ollama (se não estiver rodando)
export PATH="$HOME/.local/bin:$PATH"
OLLAMA_HOST=127.0.0.1:11434 ollama serve &

# 2. (uma vez) criar modelo
ollama create nova -f /media/joao/HD/testeia/nova-model/Modelfile

# 3. instalar deps
cd /media/joao/HD/testeia/nova
npm install

# 4. dev
NOVA_MODEL=nova OLLAMA_URL=http://127.0.0.1:11434 npm run dev
# frontend: http://localhost:5173
# backend:  http://localhost:3001
```

## Build de produção
```bash
npm run build
NOVA_MODEL=nova OLLAMA_URL=http://127.0.0.1:11434 npm start
```
