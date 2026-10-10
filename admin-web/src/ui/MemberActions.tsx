import { useEffect, useMemo, useState } from 'react'
import {
  DISPOSITION,
  personLabel,
  rolesApi,
  type Membership,
  type MembershipBrief,
  type RequestView,
  type TransferPreview,
} from '../lib/roles'
import { Button } from './Button'
import { TextArea } from './Field'
import { Chain, Drawer, MembershipPicker, Select } from './Org'

/** Shift a membership (or only its downline) under a new upline. */
export function TransferDrawer({
  m,
  upline = [],
  onClose,
  onDone,
}: {
  m: Membership
  upline?: Membership[]
  onClose: () => void
  onDone: (v: RequestView) => void
}) {
  const [to, setTo] = useState<MembershipBrief | null>(null)
  const [childrenOnly, setChildrenOnly] = useState(false)
  const [note, setNote] = useState('')
  const [preview, setPreview] = useState<TransferPreview | null>(null)
  const [busy, setBusy] = useState<'preview' | 'submit' | null>(null)
  const [error, setError] = useState('')
  const exclude = useMemo(() => [m.id, ...(m.parent_id && !childrenOnly ? [m.parent_id] : [])], [m, childrenOnly])

  useEffect(() => setPreview(null), [to, childrenOnly])

  const runPreview = async () => {
    if (!to) return
    setBusy('preview')
    setError('')
    const r = await rolesApi.transferPreview(m.id, { new_parent_id: to.id, children_only: childrenOnly })
    setBusy(null)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setPreview(r.data)
  }

  const submit = async () => {
    if (!to) return
    setBusy('submit')
    setError('')
    const r = await rolesApi.transfer(m.id, { new_parent_id: to.id, children_only: childrenOnly, note: note.trim() })
    setBusy(null)
    if (!r.ok) {
      setError(r.message)
      return
    }
    onDone(r.data)
  }

  const blocked = preview ? preview.blockers.length > 0 : true

  return (
    <Drawer title="Shift (change upline)" onClose={onClose}>
      {error ? <div className="err-banner">{error}</div> : null}
      <p style={{ margin: 0 }}>
        <strong>{personLabel(m)}</strong> · {m.role.title} · {m.code}
      </p>
      {upline.length ? (
        <div>
          <div className="muted" style={{ fontSize: 12, marginBottom: 6 }}>
            Current upline
          </div>
          <Chain upline={upline} />
        </div>
      ) : null}
      <label className="perm-check">
        <input type="checkbox" checked={childrenOnly} onChange={(e) => setChildrenOnly(e.target.checked)} />
        <span>
          <strong>Move only their downline</strong>
          <div className="muted">The member stays where they are; everyone directly under them moves to the new upline.</div>
        </span>
      </label>
      <MembershipPicker
        label="New upline"
        value={to}
        onChange={setTo}
        exclude={exclude}
        hint="Must hold a role allowed to have this role under it, and must not be inside the subtree being moved."
      />
      <TextArea label="Note" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} placeholder="Why — shown to approvers and kept in the audit trail." />
      {preview ? (
        <div className={preview.blockers.length ? 'err-banner' : 'ok-banner'}>
          {preview.blockers.length ? (
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {preview.blockers.map((b) => (
                <li key={b}>{b}</li>
              ))}
            </ul>
          ) : (
            <>
              Moving <strong>{preview.moving}</strong> membership{preview.moving === 1 ? '' : 's'} ({preview.direct_moving} direct) to{' '}
              <strong>{preview.to ? personLabel(preview.to) : 'the top'}</strong>. Limit per transfer: {preview.max_allowed.toLocaleString()}.
            </>
          )}
        </div>
      ) : null}
      <div className="form-actions">
        {!preview || preview.blockers.length ? (
          <Button variant="secondary" loading={busy === 'preview'} disabled={!to} onClick={() => void runPreview()}>
            Check
          </Button>
        ) : null}
        <Button loading={busy === 'submit'} disabled={!to || blocked} onClick={() => void submit()}>
          Send shift
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
    </Drawer>
  )
}

/** Remove a person from a role; the subject is notified when the request is approved. */
export function RevokeDrawer({ m, onClose, onDone }: { m: Membership; onClose: () => void; onDone: (v: RequestView) => void }) {
  const [disposition, setDisposition] = useState<number>(DISPOSITION.reparent)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async () => {
    if (!reason.trim()) {
      setError('Add a reason — the person and approvers will see it.')
      return
    }
    setBusy(true)
    const r = await rolesApi.revoke(m.id, { disposition, reason: reason.trim() })
    setBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    onDone(r.data)
  }
  return (
    <Drawer title="Revoke role" onClose={onClose}>
      {error ? <div className="err-banner">{error}</div> : null}
      <p style={{ margin: 0 }}>
        <strong>{personLabel(m)}</strong> · {m.role.title} · {m.code}
        {m.direct_children ? ` · ${m.direct_children} direct downline` : ''}
      </p>
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        When this is approved, the person is notified that their role was removed.
      </p>
      <Select label="What happens to their downline" value={disposition} onChange={(e) => setDisposition(Number(e.target.value))}>
        <option value={DISPOSITION.reparent}>Move them up to this member’s upline</option>
        <option value={DISPOSITION.keep}>Keep them where they are (under a revoked node)</option>
        <option value={DISPOSITION.suspend}>Suspend everyone below as well</option>
      </Select>
      <TextArea label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required />
      <div className="form-actions">
        <Button variant="danger" loading={busy} onClick={() => void submit()}>
          Revoke
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
    </Drawer>
  )
}
