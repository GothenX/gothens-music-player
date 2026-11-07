package com.gothen.gmp.audio;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 后台探测每首曲子的时长（毫秒），避免在打开界面时同步解码造成卡顿。
 * 返回 -1 表示未知。
 */
public final class DurationCache {
    private static final Map<String, Long> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GMP-Meta");
        t.setDaemon(true);
        return t;
    });

    private DurationCache() {
    }

    public static long getMs(Track t) {
        String key = t.path().toAbsolutePath().normalize().toString();
        Long cached = CACHE.get(key);
        if (cached != null) return cached;
        if (PENDING.add(key)) {
            EXEC.submit(() -> {
                long v = probe(t);
                CACHE.put(key, v);
                PENDING.remove(key);
            });
        }
        return -1;
    }

    private static long probe(Track t) {
        try {
            AudioFileFormat aff = AudioSystem.getAudioFileFormat(t.path().toFile());
            long frames = aff.getFrameLength();
            AudioFormat fmt = aff.getFormat();
            float sr = fmt == null ? 0f : fmt.getSampleRate();
            if (frames > 0 && sr > 0) {
                return (long) ((frames / sr) * 1000L);
            }
            Object dur = aff.properties().get("duration");
            if (dur instanceof Number num && num.longValue() > 0) {
                return num.longValue() / 1000L;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }
}
