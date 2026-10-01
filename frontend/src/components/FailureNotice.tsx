import type { ReactNode } from 'react'
import { Icon } from './Icon'
import type { IconName } from './Icon'

interface Props {
  icon: IconName
  /** Plain headline: what went wrong, in the shopper's words. */
  title?: string
  /** The specific reason, as reported by the backend. */
  detail: string
  /** What happens next, set off below a perforation. */
  next?: ReactNode
  role?: 'alert' | 'status'
}

/** A void-taped slip under a label or form: what failed, why, and what happens next. */
export function FailureNotice({ icon, title, detail, next, role = 'alert' }: Props) {
  return (
    <div className="notice notice-void" role={role}>
      <Icon name={icon} size={16} />
      <div className="notice-body">
        {title && <strong className="notice-title">{title}</strong>}
        <p>{detail}</p>
        {next && <p className="notice-next">{next}</p>}
      </div>
    </div>
  )
}
