/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        tavern: {
          950: '#0f0a06',
          900: '#1a1209',
          800: '#2d1f14',
          700: '#3d2a1a',
          600: '#5c4030',
          500: '#8b6914',
          400: '#c9952c',
          300: '#d4a853',
          200: '#e8c97a',
          100: '#f5e6c8',
          50: '#faf3e3',
        },
      },
      fontFamily: {
        display: ['"Cormorant Garamond"', 'Georgia', 'serif'],
        sans: ['"DM Sans"', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
