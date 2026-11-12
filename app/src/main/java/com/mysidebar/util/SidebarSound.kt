package com.mysidebar.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 侧边栏滑出时的极短「卡通冒出」音效：约 130ms 的上滑 + 快速衰减电子音，
 * 程序合成、无音频资源文件、无需任何权限。播放在 IO 协程中完成。
 */
object SidebarSound {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastPlayMs = 0L

    fun play() {
        val now = System.currentTimeMillis()
        if (now - lastPlayMs < 80) return // 防抖，避免连续展开时叠加
        lastPlayMs = now
        scope.launch { playInternal() }
    }

    private fun playInternal() {
        val sampleRate = 44100
        val durationSec = 0.13f
        val n = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = (1f - i.toFloat() / n)
            val attack = min(1f, t / 0.008f) // 8ms 起音包络
            val freq = 520f + 360f * (t / durationSec) // 520Hz -> 880Hz 上滑
            val sample = sin(2f * PI.toFloat() * freq * t) * env * env * attack * 0.35f
            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep((durationSec * 1000 + 60).toLong())
        } finally {
            track.release()
        }
    }
}
