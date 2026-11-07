package com.gothen.gmp.ui;

import com.gothen.gmp.GmpClient;
import com.gothen.gmp.audio.AudioPlayer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 关闭 UI 后，显示在游戏界面左侧的简易控制器：
 * 曲名、进度条、播放/暂停状态与时间。
 */
public class GmpHud implements HudElement {
    private static final int W = 196;
    private static final int H = 48;

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, DeltaTracker deltaTracker) {
        AudioPlayer p = GmpClient.PLAYER;
        String title = p.getCurrentTitle();
        if (title == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int x = 6;                 // 左侧
        int y = sh / 2 - H / 2;    // 垂直居中偏左

        // 面板
        gg.fill(x, y, x + W, y + H, 0xB0000000);
        gg.fill(x, y, x + 3, y + H, 0xFF3A7BD5);

        // 曲名 + 状态
        String state = p.isPaused() ? "||" : ">";
        String name = state + " " + trim(font, title, W - 24);
        gg.text(font, Component.literal(name), x + 9, y + 7, 0xFFFFFFFF);

        // 进度
        double prog = p.getProgress();
        int barX = x + 9;
        int barY = y + 23;
        int barW = W - 18;
        gg.fill(barX, barY, barX + barW, barY + 6, 0x602A2F3A);
        gg.fill(barX, barY, barX + (int) (barW * prog), barY + 6, 0xFF3A7BD5);

        // 时间
        String time = fmt(p.getPositionMs()) + " / " + fmt(p.getDurationMs());
        gg.text(font, Component.literal(time), x + 9, y + 33, 0xFFBFC8D4);

        // 模式
        String mode = switch (p.getMode()) {
            case SEQUENTIAL -> "顺序";
            case RANDOM -> "随机";
            case LOOP_SINGLE -> "单曲";
        };
        String hint = mode + " · M 打开";
        gg.text(font, Component.literal(hint), x + W - 9 - font.width(hint), y + 33, 0xFF8A94A4);
    }

    private static String trim(Font font, String s, int maxWidth) {
        if (s == null) return "";
        if (font.width(s) <= maxWidth) return s;
        String ell = "...";
        int ellW = font.width(ell);
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (font.width(sb.toString() + c) + ellW > maxWidth) break;
            sb.append(c);
        }
        return sb + ell;
    }

    private static String fmt(long ms) {
        long totalSec = Math.max(0, ms) / 1000L;
        long m = totalSec / 60L;
        long s = totalSec % 60L;
        return m + ":" + (s < 10 ? "0" + s : String.valueOf(s));
    }
}
