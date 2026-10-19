import React from 'react'

export default function Sidebar({ conversations, activeId, onSelect, onNew, onDelete, open, onClose }) {
  return (
    <>
      {open && (
        <div className="fixed inset-0 bg-black/60 z-30 md:hidden" onClick={onClose} />
      )}
      <aside
        className={`fixed md:static z-40 top-0 left-0 h-full w-72 flex flex-col
        bg-ink-900/80 glass border-r border-ink-700/60
        transform transition-transform duration-300
        ${open ? 'translate-x-0' : '-translate-x-full md:translate-x-0'}`}
      >
        <div className="p-4 flex items-center gap-3 border-b border-ink-700/60">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-nova-400 to-nova-700 flex items-center justify-center shadow-glow">
            <span className="font-display font-bold text-white text-lg">N</span>
          </div>
          <div>
            <div className="font-display font-semibold text-ink-100 leading-tight">Nova</div>
            <div className="text-xs text-ink-400">sua IA</div>
          </div>
          <button onClick={onClose} className="ml-auto md:hidden text-ink-400 hover:text-ink-100">
            ✕
          </button>
        </div>

        <div className="p-3">
          <button
            onClick={onNew}
            className="w-full flex items-center gap-2 px-3 py-2.5 rounded-xl
            bg-gradient-to-r from-nova-600 to-nova-500 text-white font-medium
            hover:from-nova-500 hover:to-nova-400 transition-all shadow-soft
            hover:shadow-glow"
          >
            <span className="text-lg leading-none">+</span>
            Nova conversa
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-2 pb-3 space-y-1">
          {conversations.length === 0 && (
            <div className="text-center text-ink-500 text-sm mt-8 px-4">
              Nenhuma conversa ainda.<br />Comece uma nova!
            </div>
          )}
          {conversations.map((c) => (
            <div
              key={c.id}
              onClick={() => onSelect(c.id)}
              className={`group flex items-center gap-2 px-3 py-2.5 rounded-xl cursor-pointer transition-all
                ${activeId === c.id
                  ? 'bg-ink-700/80 text-ink-100 border border-ink-600/60'
                  : 'text-ink-300 hover:bg-ink-800/60 hover:text-ink-100'}`}
            >
              <span className="text-sm truncate flex-1">{c.title || 'Nova conversa'}</span>
              <button
                onClick={(e) => { e.stopPropagation(); onDelete(c.id) }}
                className="opacity-0 group-hover:opacity-100 text-ink-500 hover:text-red-400 transition text-xs px-1"
                title="Excluir"
              >
                🗑
              </button>
            </div>
          ))}
        </div>

        <div className="p-3 border-t border-ink-700/60 text-xs text-ink-500 flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse-slow" />
          Ollama · local
        </div>
      </aside>
    </>
  )
}
