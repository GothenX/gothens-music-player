package com.gothen.gmp;

import com.gothen.gmp.audio.AudioPlayer;
import com.gothen.gmp.audio.MusicLibrary;
import com.gothen.gmp.ui.GmpHud;
import com.gothen.gmp.ui.MusicScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;

/**
 * Gothen's Music Player 客户端入口。
 */
public class GmpClient implements ClientModInitializer {
    public static final String MOD_ID = "gmp";

    public static final MusicLibrary LIBRARY = new MusicLibrary();
    public static final AudioPlayer PLAYER = new AudioPlayer();

    /** 按键设置里独立的「Music Player」分类，不混进「游戏内容」。 */
    public static KeyMapping.Category CATEGORY;

    private static KeyMapping openKey;
    private static KeyMapping playPauseKey;
    private static KeyMapping nextKey;
    private static KeyMapping prevKey;

    private static MusicScreen cachedScreen;

    @Override
    public void onInitializeClient() {
        // 扫描 music 目录
        LIBRARY.refresh();
        PLAYER.bindLibrary(LIBRARY.getTracks());

        // 单独的按键分类：设置界面显示为「Music Player」
        CATEGORY = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "music"));

        openKey = new KeyMapping("key.gmp.open",
                InputConstants.Type.KEYSYM, InputConstants.KEY_M, CATEGORY);
        playPauseKey = new KeyMapping("key.gmp.playpause",
                InputConstants.Type.KEYSYM, InputConstants.KEY_P, CATEGORY);
        nextKey = new KeyMapping("key.gmp.next",
                InputConstants.Type.KEYSYM, InputConstants.KEY_N, CATEGORY);
        prevKey = new KeyMapping("key.gmp.prev",
                InputConstants.Type.KEYSYM, InputConstants.KEY_B, CATEGORY);

        KeyMappingHelper.registerKeyMapping(openKey);
        KeyMappingHelper.registerKeyMapping(playPauseKey);
        KeyMappingHelper.registerKeyMapping(nextKey);
        KeyMappingHelper.registerKeyMapping(prevKey);

        // HUD 控制器（左侧）
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "player_controller"), new GmpHud());

        // 退出存档 / 断开连接时停止音乐；Esc 菜单不会触发，所以音乐保留。
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PLAYER.stop());

        // 每 tick 处理按键
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                if (!(mc.gui.screen() instanceof MusicScreen)) {
                    openUi();
                }
            }
            while (playPauseKey.consumeClick()) {
                PLAYER.togglePause();
            }
            while (nextKey.consumeClick()) {
                PLAYER.next();
            }
            while (prevKey.consumeClick()) {
                PLAYER.prev();
            }

            // 播放器有曲目时压掉游戏原版背景音乐（C418 等）：
            // 先把「音乐」这一类的运行时增益置 0，再停掉正在播的那一首。
            // 不改 options 里的音量设置，也不影响 BLOCKS/PLAYERS 等其它音效。
            applyVanillaMusicMute(PLAYER.getCurrentIndex() >= 0, mc);
        });
    }

    /** 是否已把原版音乐静音（用于停止播放时恢复）。 */
    private static boolean musicMuted = false;

    /**
     * 静音/恢复游戏原版背景音乐。
     * <p>做法是直接把 {@code SoundEngine.gainBySource} 里 MUSIC 的运行时增益写 0，
     * 不碰 options，所以不会把玩家的音量设置改掉；BLOCKS、PLAYERS 等音效完全不受影响。
     */
    private static void applyVanillaMusicMute(boolean mute, Minecraft mc) {
        try {
            SoundManager sm = mc.getSoundManager();
            if (sm == null) return;
            var field = SoundManager.class.getDeclaredField("soundEngine");
            field.setAccessible(true);
            Object engine = field.get(sm);
            if (!(engine instanceof SoundEngine se)) return;

            if (mute) {
                MusicManager mm = mc.getMusicManager();
                if (mm != null) mm.stopPlaying();
                var gainField = SoundEngine.class.getDeclaredField("gainBySource");
                gainField.setAccessible(true);
                @SuppressWarnings("unchecked")
                it.unimi.dsi.fastutil.objects.Object2FloatMap<SoundSource> gains =
                        (it.unimi.dsi.fastutil.objects.Object2FloatMap<SoundSource>) gainField.get(se);
                if (gains != null) {
                    gains.put(SoundSource.MUSIC, 0f);
                }
                musicMuted = true;
            } else if (musicMuted) {
                musicMuted = false;
                se.refreshCategoryVolume(SoundSource.MUSIC);
            }
        } catch (Throwable e) {
            System.err.println("[GMP] 处理原版音乐失败: " + e);
        }
    }

    /** 打开播放器界面（复用同一个实例，避免每次重建带来的顿挫）。 */
    public static void openUi() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof MusicScreen) return;
        if (cachedScreen == null) {
            cachedScreen = new MusicScreen();
        }
        mc.gui.setScreen(cachedScreen);
    }
}
