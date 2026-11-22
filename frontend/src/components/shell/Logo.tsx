import { useId } from 'react'

// FusionLOS mark in the Fusion house style: indigo to violet tile, white geometry at two
// opacities. Stages on one line ending in a completed loan. Also the favicon.
export function LogoMark({ className = 'size-7 shrink-0' }: { className?: string }) {
  const id = useId()
  return (
    <svg className={className} viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
          <stop stopColor="#6366F1" />
          <stop offset="1" stopColor="#7C3AED" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="8" fill={`url(#${id})`} />
      <line x1="7" y1="16" x2="20" y2="16" stroke="white" strokeWidth="2" strokeOpacity="0.6" strokeLinecap="round" />
      <circle cx="7.5" cy="16" r="2.2" fill="white" fillOpacity="0.6" />
      <circle cx="14" cy="16" r="2.2" fill="white" fillOpacity="0.6" />
      <circle cx="22" cy="16" r="5.5" fill="white" fillOpacity="0.95" />
      <path d="M19.6 16.1l1.7 1.7 3.2-3.4" stroke="#6366F1" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
