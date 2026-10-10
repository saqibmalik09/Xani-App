package app.xani.core.net

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.util.UUID
import kotlinx.coroutines.delay

/**
 * Google Sign-In via Credential Manager. Produces an ID token whose `aud` is the
 * **web** client id — the same value the identity service verifies against
 * `auth.google_client_ids` / `GOOGLE_CLIENT_IDS`.
 *
 * The client id comes from [MediaConfig.googleWebClientId] — the dashboard's
 * value, read fresh on every tap, so rotating the Google project does not need
 * an app release. Unavailable (button stays hidden / soft-fails) while it is
 * empty, so a deployment without credentials still runs.
 *
 * Play Services / Credential Manager often isn't fully bound on the first tap
 * after a cold start, which surfaces as [NoCredentialException]. We retry with
 * a short backoff so the user does not see a developer SHA-1 message that goes
 * away on the next tap. The picker always lists every Google account on the
 * device (no authorized-only filter): this app supports multiple accounts per
 * device and must never hide accounts that have not signed in here yet.
 */
class CredentialGoogleSignIn(
    private val clientId: () -> String = { MediaConfig.googleWebClientId },
) : GoogleIdTokenProvider {

    override val available: Boolean
        get() = clientId().isNotEmpty()

    override suspend fun idToken(context: Context): GoogleTokenResult {
        // Re-read the dashboard's client id first: a key rotated a minute ago has
        // to work on this tap, not after the config cache window.
        MediaConfig.freshen()
        val webClientId = clientId()
        if (webClientId.isEmpty()) {
            return GoogleTokenResult.Failed("Google sign-in isn't set up yet. Use your email.")
        }
        val activity = context.findActivity()
            ?: return GoogleTokenResult.Failed("Google sign-in needs an activity.")
        val cm = CredentialManager.create(context)

        // Always ask for every Google account on the device. Filtering to
        // previously-authorized accounts would pin the picker to the one or
        // two accounts that ever signed in here and hide the rest — this app
        // supports several accounts per device, so the user must see them all.
        // Retries cover a cold Credential Manager after process start.
        var lastMessage = MSG_TRY_AGAIN
        repeat(COLD_RETRIES) { attempt ->
            when (val got = request(cm, activity, webClientId, authorizedOnly = false)) {
                is Attempt.Ok -> return GoogleTokenResult.Ok(got.token)
                Attempt.Cancelled -> return GoogleTokenResult.Cancelled
                is Attempt.HardFail -> return GoogleTokenResult.Failed(got.message)
                is Attempt.NoCredential -> {
                    lastMessage = got.message
                    // Google reports an unregistered package/signing key the same
                    // way it reports "no account on the device", so log what this
                    // build actually is: the two values a Google Android OAuth
                    // client is keyed on. `adb logcat -s GoogleSignIn` then says
                    // exactly what to register, instead of leaving a dead button.
                    Log.w(
                        TAG,
                        "no credential (attempt ${attempt + 1}/$COLD_RETRIES) " +
                            "package=${context.packageName} sha1=${SigningIdentity.sha1(context)} " +
                            "audience=$webClientId",
                    )
                    if (attempt < COLD_RETRIES - 1) delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        return GoogleTokenResult.Failed(lastMessage)
    }

    private suspend fun request(
        cm: CredentialManager,
        activity: Activity,
        webClientId: String,
        authorizedOnly: Boolean,
    ): Attempt {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .setNonce(UUID.randomUUID().toString())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        return try {
            val response = cm.getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                if (token.isNullOrBlank()) {
                    Attempt.HardFail("Google returned an empty token.")
                } else {
                    Attempt.Ok(token)
                }
            } else {
                Log.w(TAG, "unexpected credential type: ${credential.type}")
                Attempt.HardFail("Unexpected Google credential type.")
            }
        } catch (_: GetCredentialCancellationException) {
            Attempt.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "no credential authorizedOnly=$authorizedOnly", e)
            Attempt.NoCredential(
                if (authorizedOnly) MSG_TRY_AGAIN
                else MSG_NO_ACCOUNT,
            )
        } catch (t: GoogleIdTokenParsingException) {
            Log.w(TAG, "google id token parse failed", t)
            Attempt.HardFail("Could not read the Google token. Please try again.")
        } catch (t: GetCredentialException) {
            Log.w(TAG, "google credential failed", t)
            val raw = t.message.orEmpty()
            // Provider/config races look like permanent failures but often clear on retry.
            if (looksTransient(raw)) {
                Attempt.NoCredential(MSG_TRY_AGAIN)
            } else {
                Attempt.HardFail(friendlyCredentialMessage(raw))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "google sign-in failed", t)
            Attempt.HardFail(MSG_TRY_AGAIN)
        }
    }

    private sealed class Attempt {
        data class Ok(val token: String) : Attempt()
        data object Cancelled : Attempt()
        /** Permanent for this tap (bad token, cancel-like hard errors). */
        data class HardFail(val message: String) : Attempt()
        /** Worth another attempt (cold Play Services / no account yet). */
        data class NoCredential(val message: String) : Attempt()
    }

    private companion object {
        const val TAG = "GoogleSignIn"
        const val COLD_RETRIES = 3
        const val RETRY_DELAY_MS = 400L

        const val MSG_TRY_AGAIN =
            "Google sign-in isn't ready yet. Please try again in a moment."
        // Google gives the same answer for "no account on this device" and "this
        // package and signing key are not registered in the Google project", so
        // the message has to hold both without reading like a developer error.
        const val MSG_NO_ACCOUNT =
            "Google couldn't sign you in on this device. Check that a Google account is added in " +
                "Settings, or use your email instead."

        fun looksTransient(raw: String): Boolean {
            val m = raw.lowercase()
            return m.contains("not available") ||
                m.contains("temporarily") ||
                m.contains("timeout") ||
                m.contains("connection") ||
                m.contains("interrupted") ||
                m.contains("provider")
        }

        fun friendlyCredentialMessage(raw: String): String {
            val m = raw.lowercase()
            return when {
                m.contains("sha") || m.contains("package") || m.contains("oauth") || m.contains("client") ->
                    MSG_TRY_AGAIN
                raw.isNotBlank() && raw.length <= 120 && !m.contains("exception") -> raw
                else -> MSG_TRY_AGAIN
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
