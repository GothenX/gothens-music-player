package com.gothen.gmp.audio;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * 音频来源分派：按扩展名选择解码器，尽量避免依赖 AudioSystem 的第三方 SPI
 * （Fabric 的类加载环境下第三方 SPI 可能不可见）。
 * <ul>
 *   <li>FLAC：自实现流式解码（支持 24bit）</li>
 *   <li>MP3：JLayer 直接解码</li>
 *   <li>WAV 等：JDK 内置 AudioSystem</li>
 * </ul>
 */
public final class AudioSources {
    private AudioSources() {
    }

    public static AudioInputStream open(File file) throws IOException {
        String name = file.getName().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".flac")) {
                FlacPcmStream fs = new FlacPcmStream(file);
                return new AudioInputStream(fs, fs.getFormat(), fs.getTotalSamples());
            }
            if (name.endsWith(".mp3")) {
                Mp3PcmStream ms = new Mp3PcmStream(file);
                return new AudioInputStream(ms, ms.getFormat(), -1);
            }
            return AudioSystem.getAudioInputStream(file);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("无法解码音频: " + file.getName() + " (" + e.getMessage() + ")", e);
        }
    }
}
