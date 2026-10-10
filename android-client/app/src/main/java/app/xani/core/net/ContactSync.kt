package app.xani.core.net

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import app.xani.core.isolate.runFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Find friends: reads the address book in the background and sends the numbers
 * to the server in small slices, so matching can suggest people the user
 * already knows. The one gate is the OS contacts permission — the real consent
 * — so sync runs whether or not the in-app Find friends switch is on; that
 * switch only decides whether the person is shown suggestions. The phone keeps
 * nothing readable; it only tracks which numbers it already sent so a re-sync
 * uploads just the new ones.
 *
 * Nothing here runs on the UI thread, and nothing uploads until the OS grants
 * contacts access — a user who never grants it is never touched, and the work
 * never competes with what they are looking at.
 */
object ContactSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncLock = Mutex()
    private lateinit var prefs: SharedPreferences
    private var appCtx: Context? = null
    private var attached = false

    @Volatile private var lastUser: Long = 0L

    private val _state = MutableStateFlow<ContactSyncState?>(null)

    /** The caller's find-friends state, as last seen from the server. */
    val state: StateFlow<ContactSyncState?> = _state.asStateFlow()

    fun attach(context: Context) {
        if (attached) return
        attached = true
        val app = context.applicationContext
        appCtx = app
        if (!::prefs.isInitialized) prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        scope.launch {
            Xani.session.state.collect { s ->
                when (s) {
                    is AuthState.SignedIn -> {
                        if (s.user.id != lastUser) {
                            lastUser = s.user.id
                            _state.value = null
                        }
                        bootstrap()
                    }
                    is AuthState.SignedOut -> {
                        lastUser = 0L
                        _state.value = null
                    }
                    else -> Unit
                }
            }
        }
    }

    /** True while contacts access is granted at the OS level. */
    fun hasContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** True while gallery (images) access is granted — a status we report only. */
    fun hasGalleryPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Turns the Find friends suggestions on or off from the settings screen.
     * This is the suggestions flag only — contact sync is gated on the OS
     * permission, not this switch — but enabling also nudges a sync so a user
     * who just opted in and already granted contacts is matched promptly.
     */
    fun setEnabled(enabled: Boolean, onDone: (ContactSyncState?) -> Unit = {}) {
        scope.launch {
            val res = Xani.contacts.setEnabled(enabled)
            if (res.ok) {
                _state.value = res.data
                if (enabled) requestSyncSoon(0L)
            }
            withContext(Dispatchers.Main) { onDone(res.data) }
        }
    }

    /** Reports the current gallery permission to the server if it changed. */
    fun reportGallery() {
        val app = appCtx ?: return
        scope.launch { reportGalleryNow(app) }
    }

    /** Re-reads contacts and uploads anything new (the manual "sync" button). */
    fun syncNow() = requestSyncSoon(0L)

    // ── internals ────────────────────────────────────────────────────────────

    private suspend fun bootstrap() {
        val app = appCtx ?: return
        reportGalleryNow(app)
        val res = Xani.contacts.state()
        if (res.ok) _state.value = res.data
        if (hasContactsPermission(app)) {
            // Not instant: let the session settle so the first sync never
            // competes with sign-in traffic or the first frame. Runs whether or
            // not Find friends is on — the OS permission is the consent.
            requestSyncSoon(INITIAL_DELAY_MS)
        }
    }

    private fun requestSyncSoon(delayMs: Long) {
        val app = appCtx ?: return
        scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (!hasContactsPermission(app)) return@launch
            runSync(app)
        }
    }

    private suspend fun reportGalleryNow(app: Context) = runFeature(Unit) {
        val allowed = hasGalleryPermission(app)
        val last = prefs.getInt(keyGallery(), -1)
        val now = if (allowed) 1 else 0
        if (last == now) return@runFeature
        val res = Xani.contacts.setGallery(allowed)
        if (res.ok) {
            prefs.edit().putInt(keyGallery(), now).apply()
            res.data?.let { _state.value = it }
        }
        Unit
    }

    /**
     * One pass: read the phonebook, drop numbers already sent, upload the rest
     * in small batches with a pause between them so the radio is never pinned.
     * Guarded by a mutex so overlapping triggers do not double-upload.
     */
    private suspend fun runSync(app: Context) {
        if (syncLock.isLocked) return
        syncLock.withLock {
            runFeature(Unit) {
                if (!hasContactsPermission(app)) return@runFeature
                val numbers = readContacts(app)
                if (numbers.isEmpty()) return@runFeature
                val sent = prefs.getStringSet(keySent(), emptySet()) ?: emptySet()
                val fresh = numbers.filterNot { sent.contains(it) }
                if (fresh.isEmpty()) return@runFeature

                val uploaded = HashSet(sent)
                var i = 0
                while (i < fresh.size) {
                    val batch = fresh.subList(i, minOf(i + BATCH, fresh.size))
                    val res = Xani.contacts.upload(batch)
                    if (res.error) break // network blip: keep the marker, retry next pass
                    uploaded.addAll(batch)
                    prefs.edit().putStringSet(keySent(), uploaded).apply()
                    res.data?.let { d ->
                        _state.value = (_state.value ?: ContactSyncState()).copy(
                            contactsCount = d.contactsCount,
                            matchedCount = d.matchedCount,
                        )
                    }
                    i += BATCH
                    if (i < fresh.size) delay(BATCH_PAUSE_MS)
                }
                Unit
            }
        }
    }

    /** Reads distinct phone numbers from the device. No names leave this method. */
    private fun readContacts(app: Context): List<String> = runFeature(emptyList()) {
        val out = LinkedHashSet<String>()
        val cursor = app.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        ) ?: return@runFeature emptyList()
        cursor.use { c ->
            val col = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (col < 0) return@runFeature emptyList()
            while (c.moveToNext() && out.size < MAX_CONTACTS) {
                val raw = c.getString(col)?.trim().orEmpty()
                if (raw.length in 5..32) out.add(raw)
            }
        }
        out.toList()
    }

    private fun keySent() = "sent_$lastUser"
    private fun keyGallery() = "gallery_$lastUser"

    private const val PREFS = "contact_sync"
    private const val BATCH = 200
    private const val BATCH_PAUSE_MS = 1_500L
    private const val INITIAL_DELAY_MS = 20_000L
    private const val MAX_CONTACTS = 20_000
}
