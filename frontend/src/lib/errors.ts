import { ApiError, NetworkError } from '../api/client'

/** Turns any error into one sentence a person can act on. */
export function messageOf(error: unknown): string {
  if (error instanceof ApiError || error instanceof NetworkError) return error.message
  return 'Something went wrong. Try again.'
}
