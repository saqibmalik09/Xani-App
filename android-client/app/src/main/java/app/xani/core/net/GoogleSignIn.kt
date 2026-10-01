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
import app.xani.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.util.UUID

/**
 * Google Sign-In via Credential Manager. Produces an ID token whose `aud` is the
 * **web** client id — the same value the identity service verifies against
 * `auth.google_client_ids` / `GOOGLE_CLIENT_IDS`.
 *
 * Unavailable (button stays hidden / soft-fails) when [BuildConfig.GOOGLE_WEB_CLIENT_ID]
 * is empty, so a build without credentials still compiles and runs.
 */
class CredentialGoogleSignIn(
    private val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim(),
) : GoogleIdTokenProvider {

    override val available: Boolean
        get() = webClientId.isNotEmpty()

    override suspend fun idToken(context: Context): GoogleTokenResult {
        if (!available) {
            return GoogleTokenResult.Failed("Google sign-in isn't available in this build. Use your email.")
        }
        val activity = context.findActivity()
            ?: return GoogleTokenResult.Failed("Google sign-in needs an activity.")
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .setNonce(UUID.randomUUID().toString())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        return try {
            val response = CredentialManager.create(context)
                .getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                if (token.isNullOrBlank()) {
                    GoogleTokenResult.Failed("Google returned an empty token.")
                } else {
                    GoogleTokenResult.Ok(token)
                }
            } else {
                Log.w(TAG, "unexpected credential type: ${credential.type}")
                GoogleTokenResult.Failed("Unexpected Google credential type.")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleTokenResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "no credential", e)
            GoogleTokenResult.Failed(
                "No Google account available, or this package/SHA-1 is not registered in Google Cloud.",
            )
        } catch (t: GoogleIdTokenParsingException) {
            Log.w(TAG, "google id token parse failed", t)
            GoogleTokenResult.Failed("Could not read the Google token.")
        } catch (t: GetCredentialException) {
            Log.w(TAG, "google credential failed", t)
            GoogleTokenResult.Failed(t.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed.")
        } catch (t: Throwable) {
            Log.w(TAG, "google sign-in failed", t)
            GoogleTokenResult.Failed(t.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed.")
        }
    }

    private companion object {
        const val TAG = "GoogleSignIn"
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
