package com.gothen.gmp.audio;

import com.gothen.gmpaudio.AudioApi;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 后台音频播放引擎。解码在本线程完成，输出交给独立库模组
 * {@link AudioApi}（复用游戏 OpenAL 的整段静态缓冲）。
 * <p>整首 PCM 会缓存一份：拖动进度时直接从缓存切片，避免重新解码带来的几秒延迟。
 */
public class AudioPlayer {
    private enum Result { NATURAL_END, SKIPPED, SEEKED, STOPPED }

    /** 每次读取解码流的帧数。 */
    private static final int CHUNK_FRAMES = 4096;

    /** 单曲解码到内存的上限（约 8 分钟 44.1kHz 立体声 16bit）。 */
    private static final long MAX_PCM_BYTES = 128L << 20;

    private final Object lock = new Object();

    private volatile List<Track> tracks;
    private volatile int currentIndex = -1;
    private volatile boolean paused = false;
    private volatile boolean skipRequested = false;
    private volatile boolean stopRequested = false;
    private volatile boolean running = true;
    private volatile boolean active = false;
    private volatile PlayMode mode = PlayMode.SEQUENTIAL;
    private volatile float volume = 1.0f;
    private volatile String errorMessage = null;
    private volatile boolean audioUnavailable = false;

    private volatile long posFrames = 0;
    private volatile long totalFrames = 0;
    private volatile float sampleRate = 44100f;
    private volatile long seekRequestFrames = -1L;

    // ---- 当前曲目的 PCM 缓存（用于秒级 seek） ----
    private String cachePath;
    private byte[] cachePcm;
    private int cacheFrameSize = 4;
    private int cacheChannels = 2;
    private int cacheRate = 44100;
    private long cacheFrames = 0;

    private final Thread worker;

    public AudioPlayer() {
        worker = new Thread(this::runWorker, "GMP-Audio-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    public void bindLibrary(List<Track> list) {
        this.tracks = list;
    }

    // ---------------- worker ----------------

    private void runWorker() {
        try {
            loop();
        } catch (Throwable e) {
            System.err.println("[GMP] 音频线程异常退出: " + e);
            e.printStackTrace();
        } finally {
            AudioApi.shutdown();
        }
    }

    private void loop() {
        while (running) {
            int idx;
            List<Track> list = tracks;
            synchronized (lock) {
                while (running && currentIndex < 0) {
                    try {
                        lock.wait(200);
                    } catch (InterruptedException ignored) {
                    }
                }
                idx = currentIndex;
                if (!running) break;
                if (list == null || idx < 0 || idx >= list.size()) {
                    currentIndex = -1;
                    continue;
                }
            }

            Track t = list.get(idx);
            long startFrame;
            synchronized (lock) {
                startFrame = Math.max(0L, seekRequestFrames);
                seekRequestFrames = -1L;
            }
            Result r;
            try {
                r = playOne(t, startFrame);
            } catch (Throwable e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                errorMessage = msg;
                System.err.println("[GMP] 播放失败: " + t.name() + " -> " + msg);
                if (!AudioApi.isAvailable()) {
                    audioUnavailable = true;
                    synchronized (lock) {
                        currentIndex = -1;
                        skipRequested = false;
                    }
                    continue;
                }
                r = Result.SKIPPED;
            }

            synchronized (lock) {
                if (r == Result.STOPPED) {
                    stopRequested = false;
                    paused = false;
                    currentIndex = -1;
                    continue;
                }
                if (r == Result.SEEKED || r == Result.SKIPPED) {
                    skipRequested = false;
                    continue;
                }
                List<Track> cur = tracks;
                if (cur == null || cur.isEmpty()) {
                    currentIndex = -1;
                    continue;
                }
                switch (mode) {
                    case LOOP_SINGLE -> {
                    }
                    case RANDOM -> currentIndex = ThreadLocalRandom.current().nextInt(cur.size());
                    case SEQUENTIAL -> currentIndex = (idx + 1) % cur.size();
                }
            }
        }
    }

    private Result playOne(Track t, long startFrame) throws Exception {
        synchronized (lock) {
            skipRequested = false;
            stopRequested = false;
        }
        active = true;
        paused = false;
        posFrames = startFrame;

        ensureDecoded(t);
        long baseFrame = Math.min(startFrame, cacheFrames);
        long remaining = cacheFrames - baseFrame;
        if (remaining <= 0) {
            throw new Exception("曲目已播放完毕");
        }
        int fs = cacheFrameSize;
        byte[] slice = Arrays.copyOfRange(cachePcm,
                (int) Math.min(cachePcm.length, baseFrame * fs),
                (int) Math.min(cachePcm.length, (baseFrame + remaining) * fs));

        totalFrames = cacheFrames;
        sampleRate = cacheRate;

        AudioApi.setVolume(volume);
        AudioApi.play(slice, slice.length, cacheRate, cacheChannels, volume);

        long startNanos = System.nanoTime();
        long pausedNanos = 0L;
        long pauseStart = 0L;
        boolean apiPaused = false;
        long lastLog = System.currentTimeMillis();
        Result result = Result.NATURAL_END;
        long secondsPerLog = 0;
        try {
            while (true) {
                if (skipRequested) {
                    result = Result.SKIPPED;
                    break;
                }
                if (stopRequested) {
                    result = Result.STOPPED;
                    break;
                }
                if (seekRequestFrames >= 0) {
                    result = Result.SEEKED;
                    break;
                }

                long now = System.nanoTime();
                if (paused) {
                    if (!apiPaused) {
                        AudioApi.pause();
                        apiPaused = true;
                        pauseStart = now;
                    }
                    Thread.sleep(20);
                    continue;
                }
                if (apiPaused) {
                    AudioApi.resume();
                    apiPaused = false;
                    pausedNanos += now - pauseStart;
                }

                long elapsed = now - startNanos - pausedNanos;
                posFrames = baseFrame + (long) (elapsed / 1_000_000_000.0 * cacheRate);
                if (posFrames >= baseFrame + remaining) {
                    result = Result.NATURAL_END;
                    break;
                }

                long ms = System.currentTimeMillis();
                if (ms - lastLog > 5000L) {
                    lastLog = ms;
                    System.out.println("[GMP] 播放中 " + t.name() + " pos="
                            + String.format("%.1f", posFrames / Math.max(1f, cacheRate))
                            + "s/" + String.format("%.1f", totalFrames / Math.max(1f, cacheRate)) + "s");
                }
                Thread.sleep(20);
            }
        } finally {
            AudioApi.stop();
            active = false;
        }

        if (result == Result.NATURAL_END) {
            posFrames = totalFrames;
        }
        return result;
    }

    /** 解码整首并缓存；同一首歌拖动进度时不再重复解码。 */
    private void ensureDecoded(Track t) throws Exception {
        String path = t.path().toAbsolutePath().toString();
        if (path.equals(cachePath) && cachePcm != null) {
            return;
        }
        cachePcm = null;
        cachePath = null;

        AudioInputStream pcm = PcmSupport.toPlayablePcm(AudioSources.open(t.path().toFile()));
        try {
            AudioFormat fmt = pcm.getFormat();
            int channels = fmt.getChannels();
            if (channels < 1 || channels > 2) {
                throw new Exception("暂不支持 " + channels + " 声道音频");
            }
            int frameSize = fmt.getFrameSize();
            if (frameSize <= 0) frameSize = channels * 2;
            float rate = fmt.getSampleRate();
            long tf = pcm.getFrameLength();
            if (tf <= 0) tf = estimateFrames(t.path().toFile(), rate);

            long estimate = Math.max(1, tf) * frameSize;
            if (estimate > MAX_PCM_BYTES) {
                throw new IOException("曲目过长（解码后约 " + (estimate >> 20) + "MB，超过上限）");
            }

            long t0 = System.currentTimeMillis();
            byte[] data = readAll(pcm, frameSize, tf);
            if (data.length == 0) throw new IOException("没有解出任何音频数据");

            cachePcm = data;
            cacheFrameSize = frameSize;
            cacheChannels = channels;
            cacheRate = Math.max(1, Math.round(rate));
            cacheFrames = data.length / frameSize;
            cachePath = path;
            System.out.println("[GMP] 解码完成 " + (data.length >> 20) + "MB（"
                    + String.format("%.1f", cacheFrames / Math.max(1f, cacheRate)) + "s），耗时 "
                    + (System.currentTimeMillis() - t0) + "ms");
        } finally {
            try {
                pcm.close();
            } catch (Exception ignored) {
            }
        }
    }

    private byte[] readAll(AudioInputStream pcm, int frameSize, long totalFrames) throws IOException {
        long estimate = Math.max(totalFrames, 1) * frameSize;
        int cap = (int) Math.min(MAX_PCM_BYTES, Math.max(1L << 20, estimate));
        byte[] out = new byte[cap];
        int size = 0;
        byte[] buf = new byte[frameSize * CHUNK_FRAMES];
        while (true) {
            if (skipRequested || stopRequested || seekRequestFrames >= 0) break;
            int n = pcm.read(buf);
            if (n <= 0) break;
            if (size + n > out.length) {
                if (out.length >= MAX_PCM_BYTES) break;
                int grown = (int) Math.min(MAX_PCM_BYTES, Math.max(out.length * 2L, size + n));
                out = Arrays.copyOf(out, grown);
            }
            System.arraycopy(buf, 0, out, size, n);
            size += n;
        }
        return size == out.length ? out : Arrays.copyOf(out, size);
    }

    /** 当解码流不报告帧数时，从文件元数据估算总帧数。 */
    private static long estimateFrames(java.io.File f, float sr) {
        try {
            javax.sound.sampled.AudioFileFormat aff = AudioSystem.getAudioFileFormat(f);
            long fl = aff.getFrameLength();
            if (fl > 0) return fl;
            Object dur = aff.properties().get("duration");
            if (dur instanceof Number num && num.longValue() > 0) {
                return (long) ((num.doubleValue() / 1_000_000.0) * sr);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    // ---------------- 控制 ----------------

    public void playIndex(int i) {
        List<Track> list = tracks;
        if (list == null || i < 0 || i >= list.size()) return;
        synchronized (lock) {
            currentIndex = i;
            skipRequested = true;
            stopRequested = false;
            paused = false;
            errorMessage = null;
            lock.notifyAll();
        }
    }

    public void next() {
        List<Track> list = tracks;
        if (list == null || list.isEmpty()) return;
        synchronized (lock) {
            if (currentIndex < 0) {
                currentIndex = 0;
            } else {
                currentIndex = (currentIndex + 1) % list.size();
            }
            skipRequested = true;
            paused = false;
            errorMessage = null;
            lock.notifyAll();
        }
    }

    public void prev() {
        List<Track> list = tracks;
        if (list == null || list.isEmpty()) return;
        synchronized (lock) {
            if (currentIndex < 0) {
                currentIndex = list.size() - 1;
            } else {
                currentIndex = (currentIndex - 1 + list.size()) % list.size();
            }
            skipRequested = true;
            paused = false;
            errorMessage = null;
            lock.notifyAll();
        }
    }

    public void togglePause() {
        synchronized (lock) {
            if (currentIndex < 0) {
                List<Track> list = tracks;
                if (list != null && !list.isEmpty()) {
                    currentIndex = 0;
                    skipRequested = true;
                    paused = false;
                    lock.notifyAll();
                }
                return;
            }
            paused = !paused;
            lock.notifyAll();
        }
    }

    public void stop() {
        synchronized (lock) {
            stopRequested = true;
            seekRequestFrames = -1L;
            lock.notifyAll();
        }
        AudioApi.stop();
    }

    /** 按比例跳转到指定进度（0~1）。已有解码缓存时是毫秒级。 */
    public void seekToFraction(double fraction) {
        long tf = totalFrames;
        if (tf <= 0) return;
        double f = Math.max(0.0, Math.min(1.0, fraction));
        seekRequestFrames = (long) (f * tf);
    }

    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        AudioApi.setVolume(volume);
    }

    public float getVolume() {
        return volume;
    }

    public void setMode(PlayMode m) {
        mode = m;
    }

    public PlayMode getMode() {
        return mode;
    }

    public void cycleMode() {
        mode = mode.next();
    }

    // ---------------- 状态读取 ----------------

    public int getCurrentIndex() {
        return currentIndex;
    }

    public boolean isPlaying() {
        return currentIndex >= 0 && !paused;
    }

    public boolean isPaused() {
        return paused && currentIndex >= 0;
    }

    public boolean isActive() {
        return active;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isAudioUnavailable() {
        return audioUnavailable;
    }

    public String getCurrentTitle() {
        List<Track> list = tracks;
        int i = currentIndex;
        if (list != null && i >= 0 && i < list.size()) {
            return list.get(i).name();
        }
        return null;
    }

    public double getProgress() {
        long tf = totalFrames;
        if (tf <= 0) return 0.0;
        return Math.min(1.0, (double) posFrames / (double) tf);
    }

    public long getPositionMs() {
        float sr = sampleRate;
        if (sr <= 0) return 0;
        return (long) ((posFrames / sr) * 1000L);
    }

    public long getDurationMs() {
        float sr = sampleRate;
        if (sr <= 0) return 0;
        long tf = Math.max(0, totalFrames);
        return (long) ((tf / sr) * 1000L);
    }

    public void shutdown() {
        running = false;
        stop();
    }
}
