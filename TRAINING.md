# Como dar inteligência e treinar a Nova

## 1. Memória (RAG) — já disponível
O backend salva feedback em `data/feedback.jsonl` via `POST /api/feedback`.
Para memória de longo prazo, salve conversas e inclua as mais relevantes no
prompt do modelo (context window). Exemplo de chamada:
```json
{ "prompt": "pergunta", "response": "resposta da Nova", "correction": "resposta correta", "rating": 5 }
```

## 2. Fine-tuning incremental (LoRA)
1. Acumule dataset em `data/feedback.jsonl` (pergunta → resposta correta).
2. Converta para formato Alpaca/ShareGPT.
3. Treine um adapter LoRA com Unsloth ou llama.cpp:
   ```bash
   # exemplo (GPU recomendada)
   python train_lora.py --base llama3.2:3b --data data/feedback.jsonl --out nova-lora
   ```
4. Importe no Ollama:
   ```bash
   ollama create nova-v2 -f Modelfile  # com adapter
   ```
5. Aponte `NOVA_MODEL=nova-v2`.

## 3. Criação de imagens
- Web/Android usam Pollinations (grátis, sem chave) via `/api/image?prompt=...`.
- Para qualidade maior, instale Stable Diffusion (Automatic1111/ComfyUI) local
  e troque a URL em `server/index.js` / `Api.kt`.

## 4. Aprendizado contínuo (online)
- Coletar feedback do usuário (👍/👎) → `/api/feedback`.
- Re-treinar periodicamente (ex: semanal) com novos dados.
- Versionar modelos: nova, nova-v2, ... via Ollama.
