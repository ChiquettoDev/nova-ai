import React from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

export default function ChatMessage({ role, content, streaming }) {
  const isUser = role === 'user'
  return (
    <div className={`flex gap-3 md:gap-4 animate-fade-in ${isUser ? 'flex-row-reverse' : ''}`}>
      <div className={`shrink-0 w-9 h-9 rounded-xl flex items-center justify-center font-display font-bold text-sm shadow-soft
        ${isUser
          ? 'bg-ink-700 text-ink-200'
          : 'bg-gradient-to-br from-nova-400 to-nova-700 text-white shadow-glow'}`}>
        {isUser ? 'V' : 'N'}
      </div>
      <div className={`max-w-[85%] md:max-w-[75%] rounded-2xl px-4 py-3 leading-relaxed
        ${isUser
          ? 'bg-nova-600/20 border border-nova-500/30 text-ink-100'
          : 'glass border border-ink-700/60 text-ink-100'}`}>
        {isUser ? (
          <div className="whitespace-pre-wrap">{content}</div>
        ) : (
          <div className="prose prose-invert prose-sm max-w-none prose-nova">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{content || (streaming ? '...' : '')}</ReactMarkdown>
            {streaming && <span className="inline-block w-2 h-4 bg-nova-400 ml-1 align-middle animate-pulse" />}
          </div>
        )}
      </div>
    </div>
  )
}
