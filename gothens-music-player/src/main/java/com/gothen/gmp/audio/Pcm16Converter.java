package com.gothen.gmp.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 把任意 PCM 帧（8/16/24/32bit，signed/unsigned，大小端）转换为
 * 16bit signed little-endian PCM 的字节流。主要用于 24bit FLAC 等
 * 系统转换器可能不支持的情况。
 */
final class Pcm16Converter extends InputStream {
    private final AudioInputStream in;
    private final int frameSize;
    private final int channels;
    private final int bits;
    private final boolean signed;
    private final boolean bigEndian;

    private byte[] pending = new byte[0];
    private int pendingPos = 0;
    private boolean eof = false;

    Pcm16Converter(AudioInputStream in, AudioFormat fmt) {
        this.in = in;
        this.channels = Math.max(1, fmt.getChannels());
        this.bits = Math.max(8, fmt.getSampleSizeInBits());
        // 某些解码器（如 24bit FLAC）不给 frameSize，按 声道*采样字节 计算。
        int fs = fmt.getFrameSize();
        if (fs <= 0) {
            fs = this.channels * Math.max(1, this.bits / 8);
        }
        this.frameSize = fs;
        // 只对 PCM 有意义的转换
        this.signed = fmt.getEncoding() == AudioFormat.Encoding.PCM_SIGNED;
        this.bigEndian = fmt.isBigEndian();
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int r = read(one, 0, 1);
        return r <= 0 ? -1 : (one[0] & 0xFF);
    }

    @Override
    public int read(byte[] out, int off, int len) throws IOException {
        if (len <= 0) return 0;
        int written = 0;
        while (written < len) {
            if (pendingPos >= pending.length) {
                byte[] frame = new byte[frameSize];
                int got = 0;
                // 尽量读满一帧
                while (got < frameSize) {
                    int n = in.read(frame, got, frameSize - got);
                    if (n <= 0) {
                        eof = true;
                        break;
                    }
                    got += n;
                }
                if (eof) {
                    if (written == 0) return -1;
                    break;
                }
                pending = convertFrame(frame);
                pendingPos = 0;
            }
            int can = Math.min(len - written, pending.length - pendingPos);
            System.arraycopy(pending, pendingPos, out, off + written, can);
            pendingPos += can;
            written += can;
        }
        return written == 0 ? -1 : written;
    }

    private byte[] convertFrame(byte[] f) {
        byte[] out = new byte[channels * 2];
        int bytesPerSample = bits / 8;
        for (int c = 0; c < channels; c++) {
            int pos = c * bytesPerSample;
            int val;
            switch (bits) {
                case 8 -> {
                    int u = f[pos] & 0xFF;
                    if (signed) {
                        val = ((byte) u) << 8;
                    } else {
                        val = (u - 128) << 8;
                    }
                }
                case 16 -> val = readSigned(f, pos, 2);
                case 24 -> val = readSigned(f, pos, 3) >> 8;
                case 32 -> val = readSigned(f, pos, 4) >> 16;
                default -> val = 0;
            }
            if (val > 32767) val = 32767;
            if (val < -32768) val = -32768;
            out[c * 2] = (byte) (val & 0xFF);
            out[c * 2 + 1] = (byte) ((val >> 8) & 0xFF);
        }
        return out;
    }

    /** 读一个 signed 整数（bytes 字节，按端序），返回符号扩展后的 32bit 值。 */
    private int readSigned(byte[] f, int pos, int bytes) {
        int value = 0;
        if (bigEndian) {
            for (int i = 0; i < bytes; i++) {
                value = (value << 8) | (f[pos + i] & 0xFF);
            }
        } else {
            for (int i = bytes - 1; i >= 0; i--) {
                value = (value << 8) | (f[pos + i] & 0xFF);
            }
        }
        // 符号扩展（按实际位宽）
        int totalBits = bytes * 8;
        int shift = 32 - totalBits;
        value = (value << shift) >> shift;
        return value;
    }
}
