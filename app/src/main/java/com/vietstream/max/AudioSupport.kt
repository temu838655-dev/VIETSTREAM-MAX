package com.vietstream.max

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioCapabilities

/**
 * Reports capabilities actually exposed by the Android device/audio route.
 * Atmos and Android spatialization are device/output capabilities, not software
 * effects that the app can force on.
 */
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
            } catch (_: Exception) {
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
                (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                    .spatializer
            } catch (_: Exception) {
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
