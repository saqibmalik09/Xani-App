package app.xani.core.net

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import app.xani.core.isolate.runFeature
import java.security.MessageDigest

/**
 * This build's own signing fingerprint, in the colon-hex SHA-1 form a Google
 * OAuth Android client is keyed on (together with the package name).
 *
 * Google answers an unregistered package/key pair the same way it answers "no
 * Google account on this device", so a sign-in failure is otherwise impossible
 * to tell apart from a user problem. Logging the real pair turns it into a
 * one-line fix: register exactly these two values. Not secret — it is derived
 * from the certificate shipped inside the APK.
 */
internal object SigningIdentity {
    @Volatile
    private var cached: String = ""

    fun sha1(context: Context): String {
        cached.takeIf { it.isNotEmpty() }?.let { return it }
        val out = runFeature("unknown") { read(context.applicationContext) }
        cached = out
        return out
    }

    private fun read(ctx: Context): String {
        val pm = ctx.packageManager
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.let {
                if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val cert = signatures?.firstOrNull() ?: return "unknown"
        val digest = MessageDigest.getInstance("SHA-1").digest(cert.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
