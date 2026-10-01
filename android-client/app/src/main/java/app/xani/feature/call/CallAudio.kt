package app.xani.feature.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.xani.core.isolate.runFeature

/**
 * Owns the audio session for the lifetime of one call: focus, routing, the
 * incoming ringtone and the outgoing ringback. Every entry point is isolated so a
 * device without a ringtone or vibrator cannot take the call down with it.
 */
internal class CallAudio(private val context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    private var ringtone: Ringtone? = null
    private var ringback: ToneGenerator? = null
    private var inCall = false

    fun beginCall(speaker: Boolean) = runFeature(Unit) {
        val am = am ?: return@runFeature
        if (!inCall) {
            inCall = true
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .build()
                .also { am.requestAudioFocus(it) }
            am.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        setSpeaker(speaker)
    }

    fun setSpeaker(on: Boolean) = runFeature(Unit) {
        val am = am ?: return@runFeature
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wanted = am.availableCommunicationDevices.firstOrNull {
                it.type == if (on) android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            if (wanted != null && am.setCommunicationDevice(wanted)) return@runFeature
        }
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = on
    }

    fun startRingtone() = runFeature(Unit) {
        if (ringtone != null) return@runFeature
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return@runFeature
        ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
        vibrator()?.let { v ->
            val pattern = longArrayOf(0, 700, 900)
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    fun stopRingtone() = runFeature(Unit) {
        ringtone?.stop()
        ringtone = null
        vibrator()?.cancel()
    }

    fun startRingback() = runFeature(Unit) {
        if (ringback != null) return@runFeature
        ringback = ToneGenerator(AudioManager.STREAM_VOICE_CALL, RINGBACK_VOLUME).also {
            it.startTone(ToneGenerator.TONE_SUP_RINGTONE)
        }
    }

    fun stopRingback() = runFeature(Unit) {
        ringback?.stopTone()
        ringback?.release()
        ringback = null
    }

    fun endCall() = runFeature(Unit) {
        stopRingtone()
        stopRingback()
        val am = am ?: return@runFeature
        if (!inCall) return@runFeature
        inCall = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.clearCommunicationDevice()
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
        am.mode = AudioManager.MODE_NORMAL
        focus?.let { am.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun vibrator(): Vibrator? = runFeature(null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    private companion object {
        const val RINGBACK_VOLUME = 70
    }
}
