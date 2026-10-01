import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { rolesApi, when, type AuditEvent } from '../lib/roles'
import { Button } from '../ui/Button'
import { Card, Field } from '../ui/Field'
import { PageLoader } from '../ui/Loader'
import { Select } from '../ui/Org'

const ENTITY: Record<number, string> = { 1: 'Role', 2: 'Membership', 3: 'Request', 4: 'Delegation' }

function entityLink(e: AuditEvent) {
  switch (e.entity_type) {
    case 1:
      return <Link to={`/roles/${e.entity_id}`}>Role #{e.entity_id}</Link>
    case 2:
      return <Link to={`/members/${e.entity_id}`}>Membership {e.entity_id}</Link>
    case 3:
      return <Link to={`/approvals/${e.entity_id}`}>Request {e.entity_id}</Link>
    default:
      return (
        <span>
          {ENTITY[e.entity_type] || 'Entity'} {e.entity_id}
        </span>
      )
  }
}

/** Human line for an audit action slug like "membership.transfer" → "Membership · transfer". */
function actionLabel(action: string): string {
  const [head, ...rest] = action.split('.')
  const verb = rest.join('.').replace(/_/g, ' ')
  return `${head.charAt(0).toUpperCase()}${head.slice(1)} · ${verb || action}`
}

export function AuditPage() {
  const [params, setParams] = useSearchParams()
  const entityType = Number(params.get('entity_type') || 0)
  const entityId = params.get('entity_id') || ''
  const subjectId = params.get('subject_id') || ''
  const actorId = params.get('actor_id') || ''
  const [items, setItems] = useState<AuditEvent[]>([])
  const [next, setNext] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const setParam = (k: string, v: string) => {
    const p = new URLSearchParams(params)
    if (v && v !== '0') p.set(k, v)
    else p.delete(k)
    setParams(p, { replace: true })
  }

  const load = useCallback(
    async (cursor: string, append: boolean) => {
      setLoading(true)
      const r = await rolesApi.audit({ entity_type: entityType, entity_id: entityId, subject_id: subjectId, actor_id: actorId, cursor })
      setLoading(false)
      if (!r.ok) {
        setError(r.message)
        return
      }
      setError('')
      setItems((prev) => (append ? [...prev, ...(r.data.items || [])] : r.data.items || []))
      setNext(r.data.next_cursor || '')
    },
    [entityType, entityId, subjectId, actorId],
  )

  useEffect(() => {
    const t = setTimeout(() => void load('', false), 200)
    return () => clearTimeout(t)
  }, [load])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div className="page-head">
        <h1>Audit trail</h1>
        <p>Append-only record of every role, membership and request change: who did what, to whom, and the before/after.</p>
      </div>
      {error ? <div className="err-banner">{error}</div> : null}
      <Card>
        <div className="org-toolbar">
          <Select label="Entity" value={entityType} onChange={(e) => setParam('entity_type', e.target.value)}>
            <option value={0}>Anything</option>
            {Object.entries(ENTITY).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </Select>
          <Field label="Entity id" value={entityId} onChange={(e) => setParam('entity_id', e.target.value.trim())} placeholder="Role id / membership id / request id" />
          <Field label="Subject user id" value={subjectId} onChange={(e) => setParam('subject_id', e.target.value.trim())} placeholder="Person the change was about" />
          <Field label="Actor user id" value={actorId} onChange={(e) => setParam('actor_id', e.target.value.trim())} placeholder="Person who made the change" />
        </div>
        {loading && items.length === 0 ? (
          <PageLoader label="Loading audit trail…" />
        ) : items.length === 0 ? (
          <p className="muted" style={{ marginTop: 16 }}>
            Nothing recorded for those filters.
          </p>
        ) : (
          <div className="table-wrap" style={{ marginTop: 16 }}>
            <table className="table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Action</th>
                  <th>Entity</th>
                  <th>Subject</th>
                  <th>Actor</th>
                  <th>Request</th>
                  <th>Change</th>
                </tr>
              </thead>
              <tbody>
                {items.map((e) => (
                  <tr key={e.id}>
                    <td style={{ whiteSpace: 'nowrap' }}>{when(e.created_at)}</td>
                    <td>{actionLabel(e.action)}</td>
                    <td>{entityLink(e)}</td>
                    <td>{e.subject_user_id ? <Link to={`/users/${e.subject_user_id}`}>{e.subject_user_id}</Link> : <span className="muted">—</span>}</td>
                    <td>{e.actor_user_id ? <Link to={`/users/${e.actor_user_id}`}>{e.actor_user_id}</Link> : <span className="muted">system</span>}</td>
                    <td>{e.request_id ? <Link to={`/approvals/${e.request_id}`}>{e.request_id}</Link> : <span className="muted">—</span>}</td>
                    <td>
                      {e.before || e.after ? (
                        <details>
                          <summary className="muted" style={{ cursor: 'pointer' }}>
                            diff
                          </summary>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 8, minWidth: 360 }}>
                            <pre style={{ fontSize: 11, maxHeight: 220, overflow: 'auto', margin: 0 }}>{e.before ? JSON.stringify(e.before, null, 2) : '—'}</pre>
                            <pre style={{ fontSize: 11, maxHeight: 220, overflow: 'auto', margin: 0 }}>{e.after ? JSON.stringify(e.after, null, 2) : '—'}</pre>
                          </div>
                        </details>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {next ? (
          <div className="form-actions" style={{ marginTop: 12 }}>
            <Button variant="secondary" loading={loading} onClick={() => void load(next, true)}>
              Load more
            </Button>
          </div>
        ) : null}
      </Card>
    </div>
  )
}
