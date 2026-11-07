package com.gothen.gmp.ui;

import com.gothen.gmp.audio.AudioPlayer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** 音量滑块（0~100%）。 */
public class VolumeSlider extends AbstractSliderButton {
    private final AudioPlayer player;

    public VolumeSlider(int x, int y, int w, int h, double initial, AudioPlayer player) {
        super(x, y, w, h, Component.empty(), Math.max(0.0, Math.min(1.0, initial)));
        this.player = player;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal("音量: " + (int) Math.round(this.value * 100) + "%"));
    }

    @Override
    protected void applyValue() {
        if (player != null) {
            player.setVolume((float) this.value);
        }
    }
}
