import { useRef, useState, type ChangeEvent } from 'react'
import { api, photoUrl } from '../api/client'
import { messageOf } from '../lib/errors'
import { PhotoError, shrinkPhoto } from '../lib/photo'

interface Props {
  /** For the label and the input's id, e.g. "listing-photo". */
  id: string
  label: string
  hint: string
  photoId: string | null
  onChange: (photoId: string | null) => void
}

/** Choose, change or remove one photo. It's uploaded as soon as it's chosen; the form keeps its id. */
export function PhotoPicker({ id, label, hint, photoId, onChange }: Props) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const choose = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''   // so choosing the same file again still counts as a change
    if (!file) return
    setError(null)
    setBusy(true)
    try {
      onChange((await api.uploadPhoto(await shrinkPhoto(file))).id)
    } catch (err) {
      setError(err instanceof PhotoError ? err.message : messageOf(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="photo-picker">
        <div className="photo shot">
          {photoId ? <img src={photoUrl(photoId, 'thumb')} alt="The photo you chose" /> : <span>No photo yet</span>}
        </div>
        <div className="stack" style={{ ['--gap' as string]: '8px', alignItems: 'flex-start' }}>
          {/* Phones offer the camera as well as the gallery for a plain image input. */}
          <input ref={input} id={id} className="sr" type="file" accept="image/*" onChange={choose} disabled={busy}
            tabIndex={-1} aria-describedby={`${id}-hint`} />
          <div className="wrap-row" style={{ ['--gap' as string]: '8px' }}>
            <button type="button" className="btn sm" disabled={busy} onClick={() => input.current?.click()}>
              {busy ? 'Uploading…' : photoId ? 'Change photo' : 'Add a photo'}
            </button>
            {photoId && !busy && <button type="button" className="btn sm" onClick={() => { setError(null); onChange(null) }}>Remove</button>}
          </div>
          {error
            ? <span id={`${id}-hint`} role="alert" className="err">{error}</span>
            : <span id={`${id}-hint`} className="hint">{hint}</span>}
        </div>
      </div>
    </div>
  )
}
