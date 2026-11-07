package com.gothen.gmp.audio;

/** 播放模式。 */
public enum PlayMode {
    /** 顺序循环。 */
    SEQUENTIAL,
    /** 随机。 */
    RANDOM,
    /** 单曲循环。 */
    LOOP_SINGLE;

    public PlayMode next() {
        PlayMode[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
