package com.gothen.gmp.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.InputStream;
import java.io.IOException;

/**
 * 音频格式处理工具。
 * <p>目标：把任意可被 AudioSystem 读到的音频（含 24bit FLAC）统一转成
 * 16bit、有符号、小端 PWM 流，供 SourceDataLine 直接播放。
 * <p>优先使用 Java Sound 自带格式转换器；若不支持（例如部分 24bit FLAC），
 * 则退回自实现的逐帧转换器 {@link Pcm16Converter}。
 */
public final class PcmSupport {
    private PcmSupport() {
    }

    /** 统一目标格式：16bit signed little-endian PCM，保持原声道数与采样率。 */
    public static AudioFormat targetFormat(AudioFormat src) {
        int ch = Math.max(1, src.getChannels());
        float rate = src.getSampleRate() > 0f ? src.getSampleRate() : 44100f;
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                rate, 16, ch, ch * 2, rate, false);
    }

    /** 把输入流转成可播放的 PCM 流。 */
    public static AudioInputStream toPlayablePcm(AudioInputStream in) throws IOException {
        AudioFormat src = in.getFormat();
        AudioFormat dst = targetFormat(src);

        // 已是目标格式，直接用。
        if (src.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
                && src.getSampleSizeInBits() == 16
                && !src.isBigEndian()
                && src.getChannels() == dst.getChannels()
                && Math.abs(src.getSampleRate() - dst.getSampleRate()) < 0.01f) {
            return in;
        }

        // 优先用系统转换器。
        try {
            if (AudioSystem.isConversionSupported(dst, src)) {
                AudioInputStream converted = AudioSystem.getAudioInputStream(dst, in);
                if (converted != null) {
                    return converted;
                }
            }
        } catch (Throwable ignored) {
            // 落到手动转换。
        }

        // 手动逐帧转换（覆盖 8/16/24/32bit，signed，大小端）。
        long frameLen = in.getFrameLength();
        InputStream conv = new Pcm16Converter(in, src);
        return new AudioInputStream(conv, dst, frameLen);
    }
}
