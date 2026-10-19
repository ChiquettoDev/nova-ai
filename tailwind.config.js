/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        display: ['Space Grotesk', 'Inter', 'system-ui', 'sans-serif']
      },
      colors: {
        nova: {
          50: '#f0f7ff',
          100: '#e0efff',
          200: '#b9dcff',
          300: '#7cc2ff',
          400: '#3fa3ff',
          500: '#0a84ff',
          600: '#0066e6',
          700: '#0052b8',
          800: '#003d8a',
          900: '#00285c'
        },
        ink: {
          950: '#0a0b10',
          900: '#11131a',
          800: '#1a1d27',
          700: '#252938',
          600: '#3a4055',
          500: '#5a6280',
          400: '#8a93b3',
          300: '#b3bdd9',
          200: '#d6dcf0',
          100: '#eef1fa'
        }
      },
      boxShadow: {
        glow: '0 0 60px -10px rgba(10,132,255,0.45)',
        soft: '0 10px 40px -10px rgba(0,0,0,0.35)'
      },
      animation: {
        'fade-in': 'fadeIn 0.4s ease-out',
        'slide-up': 'slideUp 0.4s ease-out',
        'pulse-slow': 'pulse 3s ease-in-out infinite',
        'gradient': 'gradient 8s ease infinite'
      },
      keyframes: {
        fadeIn: { '0%': { opacity: 0 }, '100%': { opacity: 1 } },
        slideUp: { '0%': { opacity: 0, transform: 'translateY(12px)' }, '100%': { opacity: 1, transform: 'translateY(0)' } },
        gradient: {
          '0%,100%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' }
        }
      }
    }
  },
  plugins: [require('@tailwindcss/typography')]
}
