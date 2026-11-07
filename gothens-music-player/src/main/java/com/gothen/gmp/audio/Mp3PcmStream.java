package com.gothen.gmp.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * MP3 流式解码（直接调用 JLayer 的 Bitstream/Decoder），
 * 输出 16bit 小端 PCM，不依赖 AudioSystem 的第三方 SPI。
 */
public final class Mp3PcmStream extends InputStream {
    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private final AudioFormat format;

    private byte[] chunk = new byte[0];
    private int chunkPos = 0;
    private boolean eof = false;

    public Mp3PcmStream(File file) throws IOException {
        try {
            this.bitstream = new Bitstream(new BufferedInputStream(new FileInputStream(file)));
            Header header = bitstream.readFrame();
            if (header == null) {
                throw new IOException("空或无效的 MP3 文件: " + file.getName());
            }
            SampleBuffer first = decode(header);
            int channels = first.getChannelCount();
            float rate = first.getSampleFrequency();
            if (channels <= 0) channels = 2;
            if (rate <= 0) rate = 44100f;
            this.format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    rate, 16, channels, channels * 2, rate, false);
            this.chunk = toBytes(first.getBuffer(), first.getBufferLength());
            this.chunkPos = 0;
            bitstream.closeFrame();
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("MP3 解码初始化失败: " + e.getMessage(), e);
        }
    }

    private SampleBuffer decode(Header header) throws Exception {
        Object ob = decoder.decodeFrame(header, bitstream);
        return (SampleBuffer) ob;
    }

    public AudioFormat getFormat() {
        return format;
    }

    private void fill() {
        if (eof) return;
        try {
            Header header = bitstream.readFrame();
            if (header == null) {
                eof = true;
                chunk = new byte[0];
                chunkPos = 0;
                return;
            }
            SampleBuffer sb = decode(header);
            chunk = toBytes(sb.getBuffer(), sb.getBufferLength());
            chunkPos = 0;
            bitstream.closeFrame();
            if (chunk.length == 0) {
                fill();
            }
        } catch (Throwable e) {
            eof = true;
            chunk = new byte[0];
            chunkPos = 0;
        }
    }

    private static byte[] toBytes(short[] buf, int len) {
        byte[] out = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            short s = buf[i];
            out[i * 2] = (byte) (s & 0xFF);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
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
