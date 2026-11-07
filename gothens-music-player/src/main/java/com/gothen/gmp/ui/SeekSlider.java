package com.gothen.gmp.ui;

import com.gothen.gmp.audio.AudioPlayer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 可拖动的播放进度条：松手时才真正跳转。 */
public class SeekSlider extends AbstractSliderButton {
    private final AudioPlayer player;
    private boolean dragging = false;

    public SeekSlider(int x, int y, int w, int h, double initial, AudioPlayer player) {
        super(x, y, w, h, Component.empty(), Math.max(0.0, Math.min(1.0, initial)));
        this.player = player;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        long pos = (long) (this.value * Math.max(0, player.getDurationMs()));
        long dur = player.getDurationMs();
        this.setMessage(Component.literal(fmt(pos) + " / " + fmt(dur)));
    }

    @Override
    protected void applyValue() {
        // 只在松手时跳转，拖动过程中不打断播放
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        boolean r = super.mouseClicked(e, doubled);
        if (r) dragging = true;
        return r;
    }

    @Override
    public void onRelease(MouseButtonEvent e) {
        super.onRelease(e);
        if (dragging) {
            dragging = false;
            player.seekToFraction(this.value);
        }
    }

    /** 播放进度变化时刷新显示（拖动中不覆盖用户的位置）。 */
    public void sync(double progress) {
        if (dragging) return;
        this.setValue(Math.max(0.0, Math.min(1.0, progress)));
        this.updateMessage();
    }

    private static String fmt(long ms) {
        if (ms <= 0) return "0:00";
        long totalSec = ms / 1000L;
        long m = totalSec / 60L;
        long s = totalSec % 60L;
        return m + ":" + (s < 10 ? "0" + s : String.valueOf(s));
    }
}
