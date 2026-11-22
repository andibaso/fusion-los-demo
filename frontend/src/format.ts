import { useEffect, useState } from 'react'

export const money = (amount: number, currency = 'SGD') =>
  `${currency} ${amount.toLocaleString('en-SG', { maximumFractionDigits: 2 })}`

export const time = (iso?: string) => (iso ? new Date(iso).toLocaleTimeString('en-SG') : '')

function formatSeconds(seconds: number) {
  if (seconds < 60) return `${seconds}s`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`
  return `${Math.floor(seconds / 86400)}d ${Math.floor((seconds % 86400) / 3600)}h`
}

/** How long ago, for example "2m 5s". */
export function since(iso?: string, now = Date.now()) {
  if (!iso) return ''
  return formatSeconds(Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000)))
}

/** "in 42s", or "due" once the moment has passed. */
export function countdown(iso?: string, now = Date.now()) {
  if (!iso) return ''
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000)
  if (Math.abs(seconds) < 1) return 'now'
  return seconds < 0 ? `${formatSeconds(-seconds)} ago` : `in ${formatSeconds(seconds)}`
}

/** Re-renders the caller every second so countdowns tick. */
export function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(timer)
  }, [intervalMs])
  return now
}

/** ISO 8601 duration to a short label: PT48H is "2d", PT45S is "45s", PT10M is "10m". */
export function duration(iso: string) {
  const match = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$/.exec(iso)
  if (!match) return iso
  const seconds = Number(match[1] ?? 0) * 3600 + Number(match[2] ?? 0) * 60 + Number(match[3] ?? 0)
  if (seconds % 86400 === 0) return `${seconds / 86400}d`
  if (seconds % 3600 === 0) return `${seconds / 3600}h`
  if (seconds % 60 === 0) return `${seconds / 60}m`
  return `${seconds}s`
}
