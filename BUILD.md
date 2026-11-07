# 构建说明

本仓库包含两个模组工程，需按顺序构建（播放器依赖音频库提供的类）：

| 目录 | 产物 | 说明 |
|---|---|---|
| `gmp-audio-api` | `gmpaudio-Beta0.2.0.jar` | 音频输出库（必需依赖） |
| `gothens-music-player` | `gmp-Beta0.2.0.jar` | 播放器主体 |

环境：**JDK 25** + **Gradle 9.5.1**（Minecraft 26.2 为非混淆版本，Loom 使用
`net.fabricmc.fabric-loom`，不要写 `mappings`）。

```bash
# 1. 先构建音频库
cd gmp-audio-api
gradle build --no-daemon

# 2. 把音频库 jar 放到播放器的 libs 目录（构建期编译依赖）
mkdir -p ../gothens-music-player/libs
cp build/libs/gmpaudio-*.jar ../gothens-music-player/libs/

# 3. 构建播放器
cd ../gothens-music-player
gradle build --no-daemon

# 4. 两个 jar 一起放进 mods/
```

版本记录见 [`CHANGELOG.txt`](CHANGELOG.txt)。
