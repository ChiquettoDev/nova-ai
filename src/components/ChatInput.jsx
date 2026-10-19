import React, { useState, useRef, useEffect } from 'react'

export default function ChatInput({ onSend, disabled, placeholder }) {
  const [value, setValue] = useState('')
  const ref = useRef(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 200) + 'px'
  }, [value])

  const submit = () => {
    const v = value.trim()
    if (!v || disabled) return
    onSend(v)
    setValue('')
  }

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="input-glow rounded-2xl border border-ink-700/70 bg-ink-900/70 glass transition-all">
      <textarea
        ref={ref}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={onKeyDown}
        placeholder={placeholder || 'Pergunte qualquer coisa à Nova...'}
        rows={1}
        disabled={disabled}
        className="w-full resize-none bg-transparent outline-none px-4 pt-3 pb-2 text-ink-100 placeholder-ink-500 text-sm md:text-base max-h-[200px]"
      />
      <div className="flex items-center justify-between px-3 pb-2.5">
        <span className="text-xs text-ink-500">Enter para enviar · Shift+Enter para quebra</span>
        <button
          onClick={submit}
          disabled={disabled || !value.trim()}
          className="px-4 py-1.5 rounded-xl text-sm font-medium transition-all
            disabled:opacity-40 disabled:cursor-not-allowed
            bg-gradient-to-r from-nova-600 to-nova-500 text-white hover:from-nova-500 hover:to-nova-400 shadow-soft"
        >
          {disabled ? '...' : 'Enviar'}
        </button>
      </div>
    </div>
  )
}
