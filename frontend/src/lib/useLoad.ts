import { useCallback, useEffect, useState } from 'react'

export interface Loaded<T> {
  /** The latest data, kept while a reload is running so the screen doesn't flash empty. */
  data: T | undefined
  error: unknown
  loading: boolean
  reload: () => void
}

interface Result<T> {
  load: () => Promise<T>
  version: number
  data?: T
  error?: unknown
}

/**
 * Runs `load` when the screen opens (and whenever `load` changes, so wrap it
 * in useCallback), and again on reload(). Answers that arrive after the
 * screen moved on are dropped.
 */
export function useLoad<T>(load: () => Promise<T>): Loaded<T> {
  const [version, setVersion] = useState(0)
  const [result, setResult] = useState<Result<T> | null>(null)

  useEffect(() => {
    let live = true
    load().then(
      (data) => { if (live) setResult({ load, version, data }) },
      (error: unknown) => { if (live) setResult({ load, version, error }) },
    )
    return () => { live = false }
  }, [load, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  const fresh = result !== null && result.load === load && result.version === version
  return {
    data: result?.data,
    error: fresh ? result.error : undefined,
    loading: !fresh,
    reload,
  }
}

/** The current time, ticking every `everyMs`, for "closes in 1 h 20 m" labels. */
export function useNow(everyMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), everyMs)
    return () => window.clearInterval(id)
  }, [everyMs])
  return now
}
