package com.gothen.gmp.audio;

import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * FLAC 流式解码（拉取式）。
 * <p>jflac 的 SPI 格式转换器只支持 16bit，24bit FLAC 会失败；
 * 这里直接用底层 {@link FLACDecoder} 逐帧解码，输出原生位深的小端 PCM。
 */
public final class FlacPcmStream extends InputStream {
    private final FLACDecoder decoder;
    private final ByteData buffer = new ByteData(0);
    private final StreamInfo streamInfo;
    private final AudioFormat format;
    private final long totalSamples;

    private byte[] chunk = new byte[0];
    private int chunkPos = 0;
    private boolean eof = false;

    public FlacPcmStream(File file) throws IOException {
        // 直接读文件，绕开 AudioSystem 的 SPI（Fabric 环境下第三方 SPI 可能不可见）
        InputStream raw;
        try {
            raw = new java.io.BufferedInputStream(new java.io.FileInputStream(file));
        } catch (IOException e) {
            throw e;
        }
        this.decoder = new FLACDecoder(raw);
        try {
            this.streamInfo = decoder.readStreamInfo();
            decoder.readMetadata(streamInfo);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("无法读取 FLAC 头信息: " + e.getMessage(), e);
        }
        int ch = Math.max(1, streamInfo.getChannels());
        int bits = Math.max(8, streamInfo.getBitsPerSample());
        int bps = (bits + 7) / 8;
        float sr = streamInfo.getSampleRate() > 0 ? streamInfo.getSampleRate() : 44100f;
        // jflac 解码输出为小端 PCM
        this.format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                sr, bits, ch, ch * bps, sr, false);
        this.totalSamples = streamInfo.getTotalSamples();
    }

    public AudioFormat getFormat() {
        return format;
    }

    public long getTotalSamples() {
        return totalSamples;
    }

    private void fill() {
        if (eof) return;
        try {
            Frame frame = decoder.readNextFrame();
            if (frame == null) {
                eof = true;
                chunk = new byte[0];
                chunkPos = 0;
                return;
            }
            ByteData data = decoder.decodeFrame(frame, buffer);
            int len = data.getLen();
            byte[] bytes = data.getData();
            chunk = new byte[len];
            System.arraycopy(bytes, 0, chunk, 0, len);
            chunkPos = 0;
            if (len == 0) {
                fill();
            }
        } catch (Throwable e) {
            eof = true;
            chunk = new byte[0];
            chunkPos = 0;
        }
    }

    @Override
    public int read() {
        byte[] one = new byte[1];
        int r = read(one, 0, 1);
        return r <= 0 ? -1 : (one[0] & 0xFF);
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (len <= 0) return 0;
        int written = 0;
        while (written < len) {
            if (chunkPos >= chunk.length) {
                if (eof) break;
                fill();
                if (eof && chunk.length == 0) break;
                continue;
            }
            int can = Math.min(len - written, chunk.length - chunkPos);
            System.arraycopy(chunk, chunkPos, b, off + written, can);
            chunkPos += can;
            written += can;
        }
        return written == 0 ? -1 : written;
    }
}
