import { useState } from 'react'
import { gap } from '../lib/style'

/** A fresh set of recovery codes, with ways to keep them: this is the only time they're shown. */
export function RecoveryCodeList({ codes, username }: { codes: string[]; username: string }) {
  const [copied, setCopied] = useState(false)
  const text = `ThriftBite recovery codes for ${username}\nEach code works once.\n\n${codes.join('\n')}\n`

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
    } catch {
      setCopied(false)   // no clipboard access: the codes are on screen to write down
    }
  }

  return (
    <div className="stack" style={gap(12)}>
      <ul className="codes" aria-label="Recovery codes">
        {codes.map((code) => <li key={code} className="num">{code}</li>)}
      </ul>
      <div className="wrap-row" style={{ alignItems: 'center' }}>
        <button type="button" className="btn sm" onClick={copy}>{copied ? 'Copied' : 'Copy codes'}</button>
        <a className="btn sm" download={`thriftbite-recovery-codes-${username}.txt`}
          href={`data:text/plain;charset=utf-8,${encodeURIComponent(text)}`}>Save as a text file</a>
      </div>
    </div>
  )
}
