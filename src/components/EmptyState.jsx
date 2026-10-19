import React from 'react'

const SUGGESTIONS = [
  'Explique como funciona uma rede neural, de forma simples.',
  'Escreva um poema curto sobre o oceano.',
  'Crie uma receita de bolo de chocolate.',
  'Traduza "hello, how are you?" para japonês.'
]

export default function EmptyState({ onPick }) {
  return (
    <div className="flex-1 flex flex-col items-center justify-center text-center px-6 py-10">
      <div className="relative mb-6">
        <div className="w-20 h-20 rounded-3xl bg-gradient-to-br from-nova-400 to-nova-700 flex items-center justify-center shadow-glow animate-pulse-slow">
          <span className="font-display font-bold text-white text-4xl">N</span>
        </div>
        <div className="absolute inset-0 rounded-3xl bg-nova-500/30 blur-2xl -z-10" />
      </div>
      <h1 className="font-display text-3xl md:text-4xl font-bold bg-gradient-to-r from-nova-300 via-white to-nova-400 bg-clip-text text-transparent bg-[length:200%_200%] animate-gradient">
        Olá, eu sou a Nova
      </h1>
      <p className="text-ink-400 mt-2 max-w-md">
        Sua assistente de IA local. Rápida, privada e pronta para ajudar.
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-8 max-w-2xl w-full">
        {SUGGESTIONS.map((s) => (
          <button
            key={s}
            onClick={() => onPick(s)}
            className="text-left px-4 py-3 rounded-xl glass border border-ink-700/60 hover:border-nova-500/50
            text-sm text-ink-200 hover:text-ink-100 transition-all hover:shadow-soft"
          >
            {s}
          </button>
        ))}
      </div>
    </div>
  )
}
