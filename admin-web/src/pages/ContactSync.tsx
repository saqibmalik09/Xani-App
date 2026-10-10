import { Fragment, useCallback, useEffect, useRef, useState } from 'react'
import { get } from '../lib/api'
import { Button } from '../ui/Button'
import { Card } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Contact = {
  number: string
  matched_user_id?: string
  matched_username?: string
  matched_full_name?: string
}

type Row = {
  id: string
  username: string
  full_name: string
  avatar_url: string
  enabled: boolean
  gallery_allowed: boolean
  contacts_count: number
  matched_count: number
  contacts: Contact[]
  last_synced_at?: string
  created_at: string
}

type Page = {
  items: Row[]
  next_cursor?: string
}

const when = (iso?: string) => (iso ? new Date(iso).toLocaleString() : '—')

/**
 * Find-friends status for every account that reported any contact or gallery
 * state: did they opt in, how many contacts they synced and how many matched,
 * and is their gallery permission granted.
 */
export function ContactSyncPage() {
  const [rows, setRows] = useState<Row[]>([])
  const [cursor, setCursor] = useState<string | undefined>(undefined)
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const [openId, setOpenId] = useState<string | null>(null)
  const reqId = useRef(0)

  const load = useCallback(async (q: string, after?: string) => {
    const mine = ++reqId.current
    if (after) setMore(true)
    else setLoading(true)

    const params = new URLSearchParams()
    if (q.trim()) params.set('q', q.trim())
    if (after) params.set('after_id', after)
    params.set('limit', '40')

    const r = await get<Page>(`/v1/admin/contact-sync?${params.toString()}`)
    if (mine !== reqId.current) return

    setLoading(false)
    setMore(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setError('')
    setRows((prev) => (after ? [...prev, ...r.data.items] : r.data.items))
    setCursor(r.data.next_cursor)
  }, [])

  useEffect(() => {
    const t = setTimeout(() => void load(query), query ? 300 : 0)
    return () => clearTimeout(t)
  }, [query, load])

  if (loading && rows.length === 0) {
    return error ? (
      <div className="err-banner">{error}</div>
    ) : (
      <PageLoader label="Loading contact sync…" />
    )
  }

  return (
    <div className="rates-page contact-sync-page">
      <div className="page-head">
        <h1>Contact sync</h1>
        <p>
          Who turned on find friends, how many contacts they synced, how many matched, and whether
          gallery permission is allowed.
        </p>
      </div>

      {error ? <div className="err-banner">{error}</div> : null}

      <Card title="Accounts">
        <div className="contact-sync-search">
          <input
            className="contact-sync-search-input"
            placeholder="Search by username"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search by username"
          />
        </div>

        <div className="table-wrap contact-sync-table-wrap">
          <table className="data-table contact-sync-table">
            <thead>
              <tr>
                <th>User</th>
                <th className="contact-sync-center">Find friends</th>
                <th className="num">Contacts</th>
                <th className="num">Matched</th>
                <th className="contact-sync-center">Gallery</th>
                <th>Last synced</th>
                <th className="contact-sync-center">Details</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const open = openId === row.id
                const contacts = row.contacts ?? []
                return (
                  <Fragment key={row.id}>
                    <tr>
                      <td>
                        <div className="contact-sync-user">
                          <strong>{row.full_name || `@${row.username}`}</strong>
                          <span className="muted">
                            @{row.username} · {row.id}
                          </span>
                        </div>
                      </td>
                      <td className="contact-sync-center">
                        <span className={`status-pill ${row.enabled ? 'on' : 'off'}`}>
                          {row.enabled ? 'On' : 'Off'}
                        </span>
                      </td>
                      <td className="num">{row.contacts_count.toLocaleString()}</td>
                      <td className="num">{row.matched_count.toLocaleString()}</td>
                      <td className="contact-sync-center">
                        <span className={`status-pill ${row.gallery_allowed ? 'on' : 'off'}`}>
                          {row.gallery_allowed ? 'Allowed' : 'No'}
                        </span>
                      </td>
                      <td className="muted nowrap">{when(row.last_synced_at)}</td>
                      <td className="contact-sync-center">
                        <Button
                          size="sm"
                          variant={open ? 'primary' : 'secondary'}
                          onClick={() => setOpenId(open ? null : row.id)}
                        >
                          {open ? 'Hide' : 'View'}
                        </Button>
                      </td>
                    </tr>
                    {open ? (
                      <tr className="contact-sync-detail-row">
                        <td colSpan={7}>
                          {contacts.length ? (
                            <div className="contact-sync-detail">
                              {contacts.map((contact, index) => (
                                <div
                                  key={`${contact.number}-${index}`}
                                  className="contact-sync-detail-item"
                                >
                                  <strong>{contact.number}</strong>
                                  {contact.matched_username ? (
                                    <span className="muted">
                                      {contact.matched_full_name || `@${contact.matched_username}`}
                                      {' · '}@{contact.matched_username}
                                      {contact.matched_user_id
                                        ? ` · ${contact.matched_user_id}`
                                        : ''}
                                    </span>
                                  ) : (
                                    <span className="muted">Not matched</span>
                                  )}
                                </div>
                              ))}
                            </div>
                          ) : (
                            <p className="muted contact-sync-detail-empty">
                              {row.contacts_count > 0
                                ? `${row.contacts_count.toLocaleString()} contacts synced · ${row.matched_count.toLocaleString()} matched`
                                : 'No contact details for this account.'}
                            </p>
                          )}
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                )
              })}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={7} className="muted" style={{ textAlign: 'center', padding: 24 }}>
                    No accounts have reported contact or gallery status yet.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>

        {cursor ? (
          <div className="contact-sync-more">
            <Button size="sm" variant="secondary" loading={more} onClick={() => void load(query, cursor)}>
              Load more
            </Button>
          </div>
        ) : null}
      </Card>
    </div>
  )
}
