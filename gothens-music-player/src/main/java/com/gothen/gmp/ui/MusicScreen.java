package com.gothen.gmp.ui;

import com.gothen.gmp.GmpClient;
import com.gothen.gmp.audio.AudioPlayer;
import com.gothen.gmp.audio.DurationCache;
import com.gothen.gmp.audio.Favorites;
import com.gothen.gmp.audio.PlayMode;
import com.gothen.gmp.audio.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 游戏内音乐播放器主界面（按快捷键打开）。
 * <p>布局参考手稿：左上角播放器名称，左侧 List / Love / Local 三个分类，
 * 右侧为对应的二级列表，列表下方是播放控制区（含可拖动的进度条）。
 */
public class MusicScreen extends Screen {
    private static final int ITEM_H = 20;
    private static final int HEART_W = 16;

    private static final int TAB_LIST = 0;
    private static final int TAB_LOVE = 1;
    private static final int TAB_LOCAL = 2;

    private int tab = TAB_LIST;
    private int listScroll = 0;

    private int panelX, panelY, panelW, panelH;
    private int sideX, sideW;
    private int contentX, contentW;
    private int listY, listH;
    private int playerY;
    private int tabY;

    private SeekSlider seekSlider;
    private Button playPauseBtn;
    private Button modeBtn;

    public MusicScreen() {
        super(Component.literal("Gothen's Music Player"));
    }

    @Override
    public boolean isPauseScreen() {
        // 不暂停游戏，音乐继续（所以在 Esc 菜单里音乐也会保留）。
        return false;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        panelW = Math.min(680, this.width - 28);
        panelH = Math.min(400, this.height - 24);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        sideX = panelX + 12;
        sideW = 116;
        contentX = panelX + sideW + 22;
        contentW = panelW - sideW - 34;

        listY = panelY + 12;
        playerY = panelY + panelH - 128;
        listH = playerY - listY - 8;

        tabY = panelY + 52;

        int seekY = playerY + 16;
        int btnY = playerY + 40;
        int volY = playerY + 66;

        seekSlider = addRenderableWidget(new SeekSlider(contentX, seekY, contentW, 16,
                GmpClient.PLAYER.getProgress(), GmpClient.PLAYER));

        addRenderableWidget(Button.builder(Component.literal("<< 上一首"),
                b -> GmpClient.PLAYER.prev()).bounds(contentX, btnY, 64, 20).build());

        playPauseBtn = addRenderableWidget(Button.builder(playPauseText(),
                b -> GmpClient.PLAYER.togglePause()).bounds(contentX + 70, btnY, 74, 20).build());

        addRenderableWidget(Button.builder(Component.literal("下一首 >>"),
                b -> GmpClient.PLAYER.next()).bounds(contentX + 150, btnY, 68, 20).build());

        modeBtn = addRenderableWidget(Button.builder(modeText(),
                b -> GmpClient.PLAYER.cycleMode()).bounds(contentX + 224, btnY, 88, 20).build());

        addRenderableWidget(Button.builder(Component.literal("刷新列表"), b -> {
            GmpClient.LIBRARY.refresh();
            listScroll = 0;
        }).bounds(contentX + 318, btnY, 70, 20).build());

        addRenderableWidget(new VolumeSlider(contentX + contentW - 188, volY, 188, 16,
                GmpClient.PLAYER.getVolume(), GmpClient.PLAYER));

        addRenderableWidget(Button.builder(Component.literal("关闭"),
                b -> this.onClose()).bounds(panelX + panelW - 58, panelY + 8, 48, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partial) {
        // 26.2 框架已自动调用过一次 extractBackground（含模糊），这里只画遮罩。
        gg.fill(0, 0, this.width, this.height, 0xA0000000);

        // 面板
        gg.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE6121820);
        gg.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF3A7BD5);
        gg.fill(panelX, panelY, panelX + 2, panelY + panelH, 0x804A90E2);

        drawSidebar(gg, mouseX, mouseY);
        drawList(gg, mouseX, mouseY);
        drawPlayer(gg);

        if (playPauseBtn != null) playPauseBtn.setMessage(playPauseText());
        if (modeBtn != null) modeBtn.setMessage(modeText());
        if (seekSlider != null) seekSlider.sync(GmpClient.PLAYER.getProgress());

        super.extractRenderState(gg, mouseX, mouseY, partial);
    }

    private void drawSidebar(GuiGraphicsExtractor gg, int mouseX, int mouseY) {
        gg.text(this.font, Component.literal("Gothen's"), sideX, panelY + 12, 0xFFF0F0F0);
        gg.text(this.font, Component.literal("Music Player"), sideX, panelY + 24, 0xFF3A7BD5);

        String[] tabs = {"播放列表", "喜欢", "本地"};
        for (int i = 0; i < tabs.length; i++) {
            int y = tabY + i * 26;
            boolean active = tab == i;
            boolean hover = mouseX >= sideX && mouseX < sideX + sideW && mouseY >= y && mouseY < y + 22;
            if (active) {
                gg.fill(sideX, y, sideX + sideW, y + 22, 0xFF2E9E5A);
            } else if (hover) {
                gg.fill(sideX, y, sideX + sideW, y + 22, 0x35FFFFFF);
            }
            gg.text(this.font, Component.literal(tabs[i]), sideX + 8, y + 7,
                    active ? 0xFFFFFFFF : 0xFFC8D0DC);
        }

        // 曲目总数 + 实际扫描目录（有多个目录时会分行号标出）
        int infoY = tabY + 3 * 26 + 6;
        gg.text(this.font, Component.literal("共 " + GmpClient.LIBRARY.getTracks().size() + " 首"),
                sideX, infoY, 0xFF8FA0B4);
        List<java.nio.file.Path> dirs = GmpClient.LIBRARY.getScannedDirs();
        for (int i = 0; i < dirs.size() && i < 3; i++) {
            String p = dirs.get(i).toString();
            int slash = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
            String tail = slash >= 0 ? "…" + p.substring(slash) : p;
            tail = trim(tail, sideW - 4);
            gg.text(this.font, Component.literal(tail), sideX, infoY + 11 + i * 9, 0xFF66707C);
        }
    }

    private void drawList(GuiGraphicsExtractor gg, int mouseX, int mouseY) {
        gg.fill(contentX - 4, listY - 4, contentX + contentW + 4, listY + listH + 4, 0x66000000);

        List<Track> list = currentList();
        int maxVisible = Math.max(1, listH / ITEM_H);
        int cur = GmpClient.PLAYER.getCurrentIndex();
        Track current = cur >= 0 && cur < GmpClient.LIBRARY.getTracks().size()
                ? GmpClient.LIBRARY.getTracks().get(cur) : null;

        int maxScroll = Math.max(0, list.size() - maxVisible);
        if (listScroll > maxScroll) listScroll = maxScroll;

        if (list.isEmpty()) {
            String hint = tab == TAB_LOVE ? "还没有喜欢的音乐（点右侧心形添加）"
                    : "music 文件夹为空，请把音乐放进 游戏目录/music";
            gg.text(this.font, Component.literal(hint), contentX + 8, listY + 8, 0xFFAAAAAA);
        }

        for (int row = 0; row < maxVisible; row++) {
            int idx = listScroll + row;
            if (idx >= list.size()) break;
            Track t = list.get(idx);
            int y = listY + row * ITEM_H;
            boolean hover = mouseX >= contentX && mouseX < contentX + contentW
                    && mouseY >= y && mouseY < y + ITEM_H;
            boolean isCur = current != null && current.path().equals(t.path());
            if (isCur) {
                gg.fill(contentX, y, contentX + contentW, y + ITEM_H, 0x902E9E5A);
            } else if (hover) {
                gg.fill(contentX, y, contentX + contentW, y + ITEM_H, 0x35FFFFFF);
            }

            String name = (idx + 1) + ". " + t.name();
            name = trim(name, contentW - HEART_W - 62);
            gg.text(this.font, Component.literal(name), contentX + 5, y + 6,
                    isCur ? 0xFFFFFFFF : 0xFFD0D0D0);

            int heartX = contentX + contentW - HEART_W;
            boolean fav = Favorites.isFavorite(t);
            gg.text(this.font, Component.literal(fav ? "\u2665" : "\u2661"), heartX, y + 6,
                    fav ? 0xFFFF5566 : 0xFF7A8494);

            long dur = DurationCache.getMs(t);
            if (dur > 0) {
                String d = fmt(dur);
                gg.text(this.font, Component.literal(d),
                        heartX - 6 - this.font.width(d), y + 6, 0xFF9AA4B2);
            }
        }

        if (maxScroll > 0) {
            int barH = Math.max(10, listH * maxVisible / list.size());
            int barY = listY + (listH - barH) * listScroll / maxScroll;
            gg.fill(contentX + contentW + 1, listY, contentX + contentW + 4, listY + listH, 0x40000000);
            gg.fill(contentX + contentW + 1, barY, contentX + contentW + 4, barY + barH, 0xFF3A7BD5);
        }
    }

    private void drawPlayer(GuiGraphicsExtractor gg) {
        AudioPlayer p = GmpClient.PLAYER;
        gg.fill(contentX - 4, playerY - 2, contentX + contentW + 4, playerY + 96, 0x55000000);

        String title = p.getCurrentTitle();
        String line = "正在播放: " + (title == null ? "— 未播放 —" : title);
        gg.text(this.font, Component.literal(trim(line, contentW)), contentX, playerY + 1, 0xFFFFFFFF);

        String err = p.getErrorMessage();
        if (err != null) {
            gg.text(this.font, Component.literal(trim("错误: " + err, contentW)),
                    contentX, playerY + 84, 0xFFFF6B6B);
        }
    }

    private List<Track> currentList() {
        List<Track> all = GmpClient.LIBRARY.getTracks();
        if (tab == TAB_LOVE) return Favorites.filter(all);
        return all;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x();
        double my = e.y();

        // 左侧分类
        for (int i = 0; i < 3; i++) {
            int y = tabY + i * 26;
            if (mx >= sideX && mx < sideX + sideW && my >= y && my < y + 22) {
                tab = i;
                listScroll = 0;
                return true;
            }
        }

        // 列表行 / 心形收藏
        if (mx >= contentX && mx <= contentX + contentW && my >= listY && my <= listY + listH) {
            int row = (int) ((my - listY) / ITEM_H);
            int idx = listScroll + row;
            List<Track> list = currentList();
            if (idx >= 0 && idx < list.size()) {
                Track t = list.get(idx);
                int heartX = contentX + contentW - HEART_W;
                if (mx >= heartX) {
                    Favorites.toggle(t);
                    return true;
                }
                List<Track> all = GmpClient.LIBRARY.getTracks();
                int li = all.indexOf(t);
                if (li >= 0) {
                    GmpClient.PLAYER.playIndex(li);
                }
                return true;
            }
        }
        return super.mouseClicked(e, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 整个面板内滚轮都生效（列表区域较小，玩家常滚不到）
        if (mouseX >= panelX && mouseX <= panelX + panelW
                && mouseY >= panelY && mouseY <= panelY + panelH) {
            int total = currentList().size();
            int maxVisible = Math.max(1, listH / ITEM_H);
            int maxScroll = Math.max(0, total - maxVisible);
            int step = (int) Math.max(1, Math.min(5, Math.round(Math.abs(scrollY))));
            int delta = scrollY > 0 ? -step : (scrollY < 0 ? step : 0);
            listScroll = Math.max(0, Math.min(maxScroll, listScroll + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(null);
    }

    private Component playPauseText() {
        AudioPlayer p = GmpClient.PLAYER;
        if (p.getCurrentIndex() < 0 || p.isPaused()) {
            return Component.literal("> 播放");
        }
        return Component.literal("|| 暂停");
    }

    private Component modeText() {
        PlayMode m = GmpClient.PLAYER.getMode();
        String label = switch (m) {
            case SEQUENTIAL -> "顺序";
            case RANDOM -> "随机";
            case LOOP_SINGLE -> "单曲循环";
        };
        return Component.literal("模式: " + label);
    }

    private String trim(String s, int maxWidth) {
        if (s == null) return "";
        if (this.font.width(s) <= maxWidth) return s;
        String ell = "...";
        int ellW = this.font.width(ell);
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (this.font.width(sb.toString() + c) + ellW > maxWidth) break;
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
