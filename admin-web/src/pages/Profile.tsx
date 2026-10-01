import { useEffect, useState, type FormEvent } from 'react'
import { get, post, putBytes } from '../lib/api'
import { mediaUrl, useAuth, type User } from '../lib/auth'
import { Button } from '../ui/Button'
import { Field, TextArea } from '../ui/Field'
import { PageLoader } from '../ui/Loader'

type Profile = {
  full_name?: string
  username?: string
  bio?: string
  avatar_url?: string
  location?: string
}

export function ProfilePage() {
  const { session, setUser, mediaBase, refreshMe } = useAuth()
  const [loading, setLoading] = useState(true)
  const [fullName, setFullName] = useState('')
  const [username, setUsername] = useState('')
  const [bio, setBio] = useState('')
  const [avatar, setAvatar] = useState('')
  const [saveBusy, setSaveBusy] = useState(false)
  const [userBusy, setUserBusy] = useState(false)
  const [passBusy, setPassBusy] = useState(false)
  const [avatarBusy, setAvatarBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [userHint, setUserHint] = useState('')
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')

  useEffect(() => {
    let cancelled = false
    void (async () => {
      const r = await get<{ profile: Profile & User }>('/v1/profile/me')
      if (cancelled) return
      if (r.ok) {
        const p = r.data.profile
        setFullName(p.full_name || session?.user.full_name || '')
        setUsername(p.username || session?.user.username || '')
        setBio(p.bio || session?.user.bio || '')
        setAvatar(p.avatar_url || session?.user.avatar_url || '')
      } else if (session) {
        setFullName(session.user.full_name)
        setUsername(session.user.username)
        setBio(session.user.bio)
        setAvatar(session.user.avatar_url)
      }
      setLoading(false)
    })()
    return () => {
      cancelled = true
    }
  }, [session])

  async function saveProfile(e: FormEvent) {
    e.preventDefault()
    setNotice('')
    setError('')
    setSaveBusy(true)
    const r = await post<{ profile: Profile & User }>('/v1/profile/update', {
      full_name: fullName.trim(),
      bio: bio.trim(),
    })
    setSaveBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    const p = r.data.profile
    setUser({
      ...(session?.user as User),
      full_name: p.full_name || fullName,
      bio: p.bio || bio,
      avatar_url: p.avatar_url || avatar,
    })
    setNotice('Profile saved.')
  }

  async function saveUsername(e: FormEvent) {
    e.preventDefault()
    setNotice('')
    setError('')
    setUserHint('')
    const nextUser = username.trim().toLowerCase().replace(/^@/, '')
    if (nextUser === session?.user.username) {
      setUserHint('That is already your username.')
      return
    }
    setUserBusy(true)
    const r = await post<{ profile?: Profile & User; user?: User }>('/v1/profile/username', { username: nextUser })
    setUserBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    await refreshMe()
    setUsername(nextUser)
    setNotice('Username updated.')
  }

  async function changePassword(e: FormEvent) {
    e.preventDefault()
    setNotice('')
    setError('')
    if (next !== confirm) {
      setError('New passwords do not match.')
      return
    }
    setPassBusy(true)
    const r = await post('/v1/auth/password/change', {
      current_password: current,
      new_password: next,
      confirm_password: confirm,
    })
    setPassBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    setCurrent('')
    setNext('')
    setConfirm('')
    setNotice('Password changed.')
  }

  async function onAvatar(file: File | null) {
    if (!file) return
    setNotice('')
    setError('')
    setAvatarBusy(true)
    const r = await putBytes<{ profile?: { avatar_url?: string }; avatar_url?: string }>(
      '/v1/profile/avatar',
      file,
      file.type || 'image/jpeg',
    )
    setAvatarBusy(false)
    if (!r.ok) {
      setError(r.message)
      return
    }
    const url = r.data.profile?.avatar_url || r.data.avatar_url || ''
    if (url) {
      setAvatar(url)
      if (session) setUser({ ...session.user, avatar_url: url })
    }
    setNotice('Photo updated.')
  }

  if (loading) return <PageLoader label="Loading profile…" />

  const face = mediaUrl(mediaBase, avatar)

  return (
    <div className="profile-page">
      <div className="page-head">
        <h1>Profile</h1>
        <p>Your console account — same name and photo as the apps.</p>
      </div>

      {notice ? <div className="ok-banner">{notice}</div> : null}
      {error ? <div className="err-banner">{error}</div> : null}

      <div className="profile-panel">
        <div className="profile-photo">
          <div className="profile-av">
            {face ? (
              <img src={face} alt="" />
            ) : (
              (fullName || username || '?').slice(0, 1).toUpperCase()
            )}
          </div>
          <label>
            <input
              type="file"
              accept="image/*"
              hidden
              disabled={avatarBusy}
              onChange={(e) => void onAvatar(e.target.files?.[0] || null)}
            />
            <Button
              type="button"
              variant="secondary"
              size="sm"
              loading={avatarBusy}
              onClick={(e) => {
                const input = (e.currentTarget.parentElement as HTMLElement)?.querySelector('input')
                input?.click()
              }}
            >
              Change photo
            </Button>
          </label>
        </div>

        <div className="profile-forms">
          <form className="profile-form" onSubmit={(e) => void saveProfile(e)}>
            <Field label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={80} />
            <TextArea label="Bio" value={bio} onChange={(e) => setBio(e.target.value)} maxLength={280} />
            <Button type="submit" size="sm" loading={saveBusy}>
              Save profile
            </Button>
          </form>

          <form className="profile-form" onSubmit={(e) => void saveUsername(e)}>
            <Field
              label="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              hint={userHint || 'Letters, numbers, dots, underscores · 3–24'}
            />
            <Button type="submit" size="sm" loading={userBusy}>
              Update username
            </Button>
          </form>

          <form className="profile-form profile-pass" onSubmit={(e) => void changePassword(e)}>
            <Field
              label="Current password"
              type="password"
              autoComplete="current-password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
            />
            <Field
              label="New password"
              type="password"
              autoComplete="new-password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
            />
            <Field
              label="Confirm"
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
            <Button type="submit" size="sm" loading={passBusy}>
              Change password
            </Button>
          </form>
        </div>
      </div>
    </div>
  )
}
