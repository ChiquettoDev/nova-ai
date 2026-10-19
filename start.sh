#!/usr/bin/env bash
set -e
export PATH="$HOME/.local/bin:$PATH"
if ! curl -s http://127.0.0.1:11434/api/tags >/dev/null; then
  OLLAMA_HOST=127.0.0.1:11434 nohup ollama serve >/tmp/opencode/ollama.log 2>&1 &
  sleep 3
fi
cd "$(dirname "$0")"
NOVA_MODEL=nova OLLAMA_URL=http://127.0.0.1:11434 npm run dev
