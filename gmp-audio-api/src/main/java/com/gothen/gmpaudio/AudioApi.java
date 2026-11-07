package com.gothen.gmpaudio;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Gothen's Audio API：供其它模组播放任意 16bit PCM 的接口。
 * <p>实现方式：复用游戏已经初始化的 OpenAL 设备与上下文，只创建自己的
 * source / buffer，用**整段静态缓冲**播放（该环境不支持流式队列）。
 * <p><b>调用线程</b>：必须是同一个后台线程；不要在主线程写大块数据。
 * <p>本类不含任何解码逻辑，解码由调用方负责（约定输入为小端 16bit PCM）。
 */
public final class AudioApi {

    /** 一段 PCM 的最大字节数（约 8 分钟 44.1kHz 立体声）。 */
    public static final int MAX_PCM_BYTES = 128 << 20;

    private static final Object LOCK = new Object();

    private static int source = 0;
    private static int buffer = 0;
    private static int format = AL10.AL_FORMAT_STEREO16;
    private static int sampleRate = 44100;
    private static int bytesPerFrame = 4;
    private static long frames = 0;
    private static volatile float gain = 1.0f;
    private static boolean open = false;
    private static boolean paused = false;
    private static boolean playing = false;

    private AudioApi() {
    }

    /** 音频库是否可用（OpenAL 已由游戏初始化）。 */
    public static boolean isAvailable() {
        try {
            long[] h = handles();
            return h[0] != 0L && h[1] != 0L;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 播放一段 16bit 小端 PCM（整段上传，调用后立即开始）。
     *
     * @param pcm        小端 16bit PCM
     * @param len        有效字节数
     * @param sampleRate 采样率
     * @param channels   声道数（1 或 2）
     * @param volume     0~1
     */
    public static void play(byte[] pcm, int len, int sampleRate, int channels, float volume)
            throws Exception {
        synchronized (LOCK) {
            volume = Math.max(0f, Math.min(1f, volume));
            gain = volume;
            ensureOpen(channels, sampleRate);
            if (len > MAX_PCM_BYTES) {
                throw new IllegalArgumentException("音频数据超过上限（" + (len >> 20) + "MB）");
            }
            ByteBuffer bb = BufferUtils.createByteBuffer(Math.max(1, len));
            bb.put(pcm, 0, len);
            bb.flip();
            AL10.alBufferData(buffer, format, bb, sampleRate);
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcef(source, AL10.AL_GAIN, gain);
            AL10.alSourcePlay(source);
            AL10.alGetError();
            bytesPerFrame = channels == 1 ? 2 : 4;
            frames = len / Math.max(1, bytesPerFrame);
            paused = false;
            playing = true;
        }
    }

    public static void pause() {
        synchronized (LOCK) {
            if (!open || !playing) return;
            AL10.alSourcePause(source);
            paused = true;
        }
    }

    public static void resume() {
        synchronized (LOCK) {
            if (!open || !playing) return;
            AL10.alSourcePlay(source);
            paused = false;
        }
    }

    /** 停止并释放当前音频（可再次 play）。 */
    public static void stop() {
        synchronized (LOCK) {
            playing = false;
            paused = false;
            if (source != 0) {
                AL10.alSourceStop(source);
                AL10.alSourcei(source, AL10.AL_BUFFER, 0);
                AL10.alGetError();
            }
        }
    }

    public static void setVolume(float v) {
        gain = Math.max(0f, Math.min(1f, v));
        synchronized (LOCK) {
            if (open && source != 0) {
                AL10.alSourcef(source, AL10.AL_GAIN, gain);
            }
        }
    }

    public static boolean isPlaying() {
        return playing && !paused;
    }

    public static boolean isPaused() {
        return playing && paused;
    }

    /** 当前缓冲总时长（毫秒）。 */
    public static long durationMs() {
        synchronized (LOCK) {
            return sampleRate <= 0 ? 0L : (long) (frames * 1000.0 / sampleRate);
        }
    }

    /** 释放全部占用的 OpenAL 对象（退出游戏时调用一次即可）。 */
    public static void shutdown() {
        synchronized (LOCK) {
            try {
                if (source != 0) AL10.alDeleteSources(source);
                if (buffer != 0) AL10.alDeleteBuffers(buffer);
            } catch (Throwable ignored) {
            }
            source = 0;
            buffer = 0;
            open = false;
            playing = false;
        }
    }

    // ---------------- 内部 ----------------

    private static void ensureOpen(int channels, int rate) throws Exception {
        long[] h = handles();
        long device = h[0];
        long context = h[1];
        if (device == 0L || context == 0L) {
            throw new IllegalStateException("游戏音频尚未初始化");
        }
        ALCCapabilities alcCaps = ALC.getCapabilities();
        if (alcCaps == null) {
            alcCaps = ALC.createCapabilities(device);
        }
        if (!ALC10.alcMakeContextCurrent(context)) {
            throw new IllegalStateException("绑定游戏 OpenAL 上下文失败");
        }
        try {
            AL.createCapabilities(alcCaps);
        } catch (Throwable ignored) {
        }

        if (!open) {
            AL10.alGetError();
            source = AL10.alGenSources();
            if (source == 0) throw new IllegalStateException("创建 source 失败");
            buffer = AL10.alGenBuffers();
            if (buffer == 0) throw new IllegalStateException("创建 buffer 失败");
            applyStaticProps();
            open = true;
        }
        format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        sampleRate = rate;
    }

    /** 关闭距离衰减，避免监听者位置把音量衰减掉。 */
    private static void applyStaticProps() {
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f);
        AL10.alSourcef(source, AL10.AL_GAIN, gain);
        AL10.alGetError();
    }

    private static long[] handles() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) throw new IllegalStateException("Minecraft 为空");
        SoundManager sm = mc.getSoundManager();
        if (sm == null) throw new IllegalStateException("SoundManager 为空");
        Field ef = SoundManager.class.getDeclaredField("soundEngine");
        ef.setAccessible(true);
        Object engine = ef.get(sm);
        if (engine == null) throw new IllegalStateException("SoundEngine 为空");
        Field lf = engine.getClass().getDeclaredField("library");
        lf.setAccessible(true);
        Object library = lf.get(engine);
        if (!(library instanceof Library lib)) throw new IllegalStateException("Library 获取失败");
        Field df = Library.class.getDeclaredField("currentDevice");
        df.setAccessible(true);
        long device = df.getLong(lib);
        Field cf = Library.class.getDeclaredField("context");
        cf.setAccessible(true);
        long context = cf.getLong(lib);
        return new long[]{device, context};
    }
}
