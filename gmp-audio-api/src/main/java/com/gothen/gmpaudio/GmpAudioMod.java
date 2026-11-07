package com.gothen.gmpaudio;

import net.fabricmc.api.ClientModInitializer;

/** 库模组入口：只负责打印版本，真正的播放能力由 {@link AudioApi} 提供。 */
public class GmpAudioMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("[GMP-Audio] 音频库已加载 (Beta0.2.0)");
    }
}
