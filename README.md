# Gothen's Music Player

> Minecraft **26.2**（Fabric）游戏内本地音乐播放器
> 作者：**Gothen** · 当前版本：**Beta0.2.0**

在游戏里按M键就能打开播放器，播放你放在游戏目录 `music` 文件夹下的本地音乐；
关掉界面后，游戏画面**左侧**会保留一个简易控制器（进度、暂停、下一曲）。
支持 **24bit FLAC**，也支持 MP3 / WAV / OGG 等常见格式。

---

## 目录

- [功能一览](#功能一览)
- [安装](#安装)
- [放音乐](#放音乐)
- [快捷键](#快捷键)
- [界面说明](#界面说明)
- [配置文件](#配置文件)
- [支持格式](#支持格式)
- [技术实现](#技术实现)
- 更新日志（`CHANGELOG.txt`）
- [已知限制](#已知限制)
- [后续计划](#后续计划)
- [项目结构](#项目结构)
- [从源码构建](#从源码构建)

---

## 功能一览

| 功能 | 状态 | 说明 |
|---|---|---|
| 游戏内播放本地音乐 | ✅ | 扫描游戏目录 `music`，支持子文件夹递归 |
| 快捷键开关界面 | ✅ | 默认 `M`，可在按键设置里改 |
| 关闭界面后的左侧常驻控制器 | ✅ | 曲名、状态、进度、时间、播放模式 |
| 播放列表 / 喜欢 / 本地 三个分类 | ✅ | 左侧分类，右侧展开二级列表 |
| 拖动进度条跳转 | ✅ | 松手后从目标位置继续播放 |
| 收藏（喜欢） | ✅ | 存在 `config/gmp-favorites.txt` |
| 播放模式 | ✅ | 顺序循环 / 随机 / 单曲循环 |
| 音量调节 | ✅ | 界面内滑块 0~100% |
| 24bit FLAC | ✅ | 内置 jflac 底层解码器 |
| MP3 / WAV / OGG | ✅ | JLayer / JDK / vorbisspi |
| 独立按键分类 | ✅ | 按键设置里单列「音乐播放器」 |
| 退出存档自动停止 | ✅ | Esc 菜单 / 设置界面仍继续播放 |
| 多游戏版本支持 | ⏳ | 见「后续计划」 |
| 其他玩家能听到 | ⏳ | 需要服务端同步，未实现 |
| 网易云 / 酷狗 API、AI 推荐、歌词 | ⏳ | 见「后续计划」 |

---

## 安装

1. 安装 **Fabric Loader 0.19.5+**（对应 Minecraft 26.2）。
2. 把 **Fabric API 0.161.0+26.2** 放进 `mods` 文件夹。
3. 把 **`gmpaudio-Beta0.2.0.jar`**（音频库，依赖）和 **`gmp-Beta0.2.0.jar`**（播放器）
   一起放进 `mods` 文件夹（**两个都必须装**，旧版 gmp/gmpaudio 请先删掉）。
4. 启动游戏。

> 所有解码依赖（jflac、JLayer、mp3spi、vorbisspi、jorbis、tritonus-share）都已打包，不需要额外安装任何库。

---

## 放音乐

把音乐文件放进**游戏运行目录**下的 `music` 文件夹：

| 场景 | 路径 |
|---|---|
| 启动器开启版本隔离（如 FCL / HMCL / PCL） | `.minecraft/versions/<版本名>/music/` |
| 未开启版本隔离 | `.minecraft/music/` |
| 开发环境 | `run/music/` |

- 支持子文件夹，会递归扫描；
- **同时扫描多个位置并去重**：`{游戏目录}/music`、`{.minecraft}/music`
  （游戏目录在 `versions/<版本>` 里时）、`{游戏目录上一级}/music`。
  所以开版本隔离时，歌放 `.minecraft/music` 或 `versions/<版本>/music` 都能读到；
- 界面左侧会显示「共 N 首」和实际扫描到的目录，一眼就能确认读的是哪个文件夹；
- 放好文件后，在界面点「刷新列表」，或重启游戏；
- 列表可滚动（滚轮在整个面板范围内都有效），上百首也能翻到底。

---

## 快捷键

默认键如下，全部可在「选项 → 控制 → 按键绑定」里修改，
并且**单独归在「音乐播放器 / Music Player」分类**下，不再混进「游戏内容」。

| 功能 | 默认键 |
|---|---|
| 打开 / 关闭播放器界面 | `M` |
| 播放 / 暂停 | `P` |
| 下一首 | `N` |
| 上一首 | `B` |

---

## 界面说明

### 主界面（按 `M` 打开）


- **左上角**：播放器名称；
- **左侧竖排**：`播放列表` / `喜欢` / `本地` 三个分类，点哪个右边就切换成对应的二级列表；
- **右侧列表**：序号 + 曲名 + 时长（后台线程异步探测，刚打开时可能短暂空白）+ 最右侧心形收藏；
- **列表下方**：当前曲目、**可拖动的进度条**、上一首 / 播放暂停 / 下一首、模式切换、刷新列表、音量滑块；
- 右上角「关闭」；打开界面**不会暂停游戏**。

### 左侧常驻控制器（关闭界面后）

只要正在播放，屏幕左边缘垂直居中会显示一个小面板：

- 曲名与播放状态（`>` / `||`）
- 进度条 + 当前时间 / 总时长
- 播放模式提示与「M 打开」提示
- 未播放时不显示

### 行为细节

- **拖动进度**：拖动时不会打断播放，松手后才跳到目标位置继续。
- **收藏**：点行尾心形即可加入 / 取消「喜欢」。
- **退出存档**：离开世界或断开连接时自动停止播放；在 **Esc 菜单 / 设置界面** 中音乐继续。
- **切歌**：点列表任意一行立即播放该曲。
- **原版背景音乐**：播放器有曲目在播时，会自动停掉游戏自带音乐（C418 等）；
  方块破坏、走路、怪物等音效照常播放，也不改动你的「音乐」音量设置。

---

## 配置文件

| 文件 | 内容 |
|---|---|
| `config/gmp-favorites.txt` | 「喜欢」列表，每行一个音乐文件绝对路径 |

音量、播放模式等为本次运行状态，暂未持久化（后续计划中）。

---

## 支持格式

| 格式 | 解码方式 | 说明 |
|---|---|---|
| **FLAC** | 内置 **jflac** 底层 `FLACDecoder` 逐帧解码 | 支持 **16bit / 24bit**，24bit 已实测完整解码 |
| **MP3** | 内置 **JLayer** `Bitstream` + `Decoder` | 16bit 小端输出 |
| **WAV** | JDK 内置 `AudioSystem` | PCM |
| **OGG** | **vorbisspi + jorbis** | 极少数精简环境可能不可用，会跳过该曲 |
| AIFF / M4A | 视运行环境支持 | 扩展名已收录，能否播取决于平台解码器 |

> 多声道（>2 声道）音频暂不支持，会被跳过并提示。

---

## 技术实现

### 运行环境

- Minecraft **26.2**
- Fabric Loader **0.19.5+**、Fabric API **0.161.0+26.2**
- Java **25**

### 架构

```
快捷键 / UI / HUD ──► AudioPlayer（状态：播放/暂停/进度/模式/音量）
                         │  守护线程 GMP-Audio-Worker
                         ├─ AudioSources  按扩展名分派解码器
                         │    ├─ FlacPcmStream（jflac 底层，原生位深）
                         │    ├─ Mp3PcmStream （JLayer 直接解）
                         │    └─ AudioSystem  （WAV / OGG）
                         ├─ PcmSupport + Pcm16Converter（统一转 16bit signed LE）
                         └─ gmpaudio（独立库模组）AudioApi
                              └─ 复用游戏 OpenAL：整段静态缓冲播放
```

### 关键实现点

- **播放能力由独立库模组 `gmpaudio` 提供**：播放器只负责解码，输出统一走库接口；
  库里复用游戏 OpenAL 的 source/buffer，用整段静态缓冲播放（该设备不支持流式队列）。
- **播放输出不走 `javax.sound.sampled.SourceDataLine`**：Android / FCL 的精简 JDK 把
  `AudioSystem.getSourceDataLine(AudioFormat)` 直接裁掉了，调用会抛 `NoSuchMethodError`。
- **复用游戏自己的 OpenAL 设备与上下文**：通过反射从
  `Minecraft → SoundManager → SoundEngine → Library` 取出 `currentDevice` / `context`，
  只创建自己的 1 个 source + 4 个 buffer；**绝不 `alcOpenDevice / alcCreateContext /
  alcMakeContextCurrent` 切走上下文**（否则游戏音效会全部报 `Invalid name parameter`），
  也绝不销毁游戏共用的设备/上下文。
- **流式播放**：4 个缓冲循环队列；每写入一块 PCM 就无条件 `alSourcePlay`
  （已在播放时是空操作）；切歌 / 拖动 / 停止时**重建 source 与 buffer**。
  **完全不依赖 `AL_BUFFERS_QUEUED / AL_BUFFERS_PROCESSED` 等查询**——它们在该
  OpenSL 后端上不可靠，曾分别导致“永不播放”和“切歌后永久无声”。
- **24bit FLAC**：绕开 jflac 自带的 SPI 转换器（只支持 16bit，24bit 时
  `isConversionSupported` 返回 false），改为直接 `readStreamInfo()` +
  `readNextFrame()` + `decodeFrame()` 拉取 PCM；jflac 输出的 `frameSize` 为 `-1`，
  转换时按 `声道数 × (位深/8)` 自行计算帧长；输出为**小端**（用过零次数验证：440Hz 正弦约 879 次/秒）。
- **进度跳转**：重新打开解码流并丢弃到目标帧（长 FLAC 约 0.2~1 秒延迟，不卡画面）。
- **界面/输入**：使用 26.2 的新渲染上下文 `GuiGraphicsExtractor` 与 Record 型输入事件
  （`KeyEvent`、`MouseButtonEvent`）；26.2 框架已自动调用过一次 `extractBackground`（含模糊），
  子类不能再调，否则 `Can only blur once per frame`。
- **时长探测**：行内时长由后台单线程 `GMP-Meta` 探测并缓存，避免打开界面时同步解码造成卡顿。

---

## 版本更新记录

更新日志已独立到同目录的 **`CHANGELOG.txt`**，每次改版都在该文件末尾追加新版本。

---

## 已知限制

- **仅客户端模组**：不改变服务器逻辑，其他玩家听不到你的音乐。
- **多声道音频**（>2 声道）暂不支持，会跳过并提示。
- **OGG** 依赖音频 SPI 自动发现，极少数精简运行环境下可能不可用。
- **进度跳转**采用“重开流 + 丢弃帧”，长 FLAC 跳转有约 0.2~1 秒延迟。
- 音量、播放模式等尚未持久化，重启游戏恢复默认。
- 首次播放一首歌需要先整首解码（约 0.3~2 秒）才出声；拖动进度因为用缓存切片，
  已经是毫秒级。单曲解码后 PCM 上限 128MB（约 8 分钟），超长曲目会提示不播。
- 行内时长探测在部分格式上可能返回未知，此时不显示时长。

---

## 后续计划


1. 支持更多游戏版本
2. 完善 24bit 支持（当前已支持 FLAC 24bit）
3. 让其他玩家也能听到（服务端同步）
4. 接入 NetEase Music API / Kugou API
5. AI 推荐音乐
6. 歌词（for another mod）
7. Introduction Page
8. 更多功能请尽情期待，可通过Github向我推荐。

---

## 项目结构

```
gothens-music-player/
├── build.gradle                    # Loom 1.17.0-alpha.8，无 mappings，implementation 依赖
├── gradle.properties               # minecraft_version=26.2, mod_version=Beta0.1.8 ...
├── settings.gradle
├── README.md
└── src/main/
    ├── java/com/gothen/gmp/
    │   ├── GmpClient.java          # 入口：扫描、按键、HUD、断连停播
    │   ├── audio/
    │   │   ├── AudioPlayer.java    # 播放线程与状态机（播放/暂停/进度/模式/音量）
    │   │   ├── OpenAlSink.java     # 复用游戏 OpenAL 的流式输出
    │   │   ├── AudioSources.java   # 按扩展名分派解码器
    │   │   ├── FlacPcmStream.java  # jflac 底层逐帧解码（16/24bit）
    │   │   ├── Mp3PcmStream.java   # JLayer 直接解码
    │   │   ├── PcmSupport.java     # 统一 16bit signed LE
    │   │   ├── Pcm16Converter.java # 8/16/24/32bit、大小端转换
    │   │   ├── MusicLibrary.java   # 扫描 music 目录
    │   │   ├── Favorites.java      # 喜欢列表持久化
    │   │   ├── DurationCache.java  # 后台探测时长
    │   │   ├── PlayMode.java
    │   │   └── Track.java
    │   └── ui/
    │       ├── MusicScreen.java    # 主界面（分类 / 列表 / 播放区）
    │       ├── GmpHud.java         # 左侧常驻控制器
    │       ├── SeekSlider.java     # 可拖动进度条
    │       └── VolumeSlider.java   # 音量滑块
    └── resources/
        ├── fabric.mod.json         # id=gmp, environment=client
        └── assets/gmp/lang/        # en_us / zh_cn
```
