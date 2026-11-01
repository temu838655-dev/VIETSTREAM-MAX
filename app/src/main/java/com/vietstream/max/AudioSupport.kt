package com.vietstream.max

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Virtualizer
import android.os.Build
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.common.util.UnstableApi

/**
 * Reports capabilities actually exposed by the Android device/audio route.
 * Atmos and Android spatialization are device/output capabilities, not software
 * effects that the app can force on.
 */
@UnstableApi
data class AudioSupport(
    val dolbyAtmos: Boolean,
    val spatializerAvailable: Boolean,
    val spatializerEnabled: Boolean
) {
    val spatialReady: Boolean
        get() = spatializerAvailable && spatializerEnabled

    companion object {
        fun detect(context: Context): AudioSupport {
            val atmos = try {
                AudioCapabilities.getCapabilities(context)
                    .supportsEncoding(C.ENCODING_E_AC3_JOC)
            } catch (_: Throwable) {
                false
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return AudioSupport(
                    dolbyAtmos = atmos,
                    spatializerAvailable = false,
                    spatializerEnabled = false
                )
            }

            val spatializer = try {
                (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                    ?.spatializer
            } catch (_: Throwable) {
                null
            }

            return AudioSupport(
                dolbyAtmos = atmos,
                spatializerAvailable = spatializer?.isAvailable == true &&
                    spatializer.immersiveAudioLevel != android.media.Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE,
                spatializerEnabled = spatializer?.isEnabled == true
            )
        }
    }
}


/**
 * Optional Android system Virtualizer attached to Media3's playback audio session.
 * This is a device-provided virtual surround effect, not a Dolby Atmos encoder.
 */
@UnstableApi
class AudioVirtualizerController {
    private var effect: Virtualizer? = null

    val isActive: Boolean
        get() = try { effect?.enabled == true } catch (_: Throwable) { false }

    fun setEnabled(audioSessionId: Int, enabled: Boolean): Boolean {
        release()
        if (!enabled || audioSessionId <= 0) return false

        return try {
            val virtualizer = Virtualizer(0, audioSessionId)
            if (!virtualizer.hasControl()) {
                virtualizer.release()
                return false
            }
            if (virtualizer.strengthSupported) {
                virtualizer.setStrength(650.toShort())
            }
            virtualizer.enabled = true
            effect = virtualizer
            true
        } catch (_: Throwable) {
            release()
            false
        }
    }

    fun release() {
        try {
            effect?.enabled = false
        } catch (_: Throwable) {
        }
        try {
            effect?.release()
        } catch (_: Throwable) {
        }
        effect = null
    }
}
