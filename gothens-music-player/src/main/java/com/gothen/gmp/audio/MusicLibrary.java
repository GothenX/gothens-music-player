package com.gothen.gmp.audio;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 扫描游戏目录下的 music 文件夹，收集可播放的本地音乐。
 * 目录：{游戏运行目录}/music（版本隔离时即 .minecraft/versions/xxx/music）。
 */
public class MusicLibrary {
    private static final Set<String> SUPPORTED = Set.of(
            "flac", "mp3", "wav", "wave", "ogg", "aiff", "aif", "m4a");

    private final List<Track> tracks = new CopyOnWriteArrayList<>();
    private final List<Path> scannedDirs = new CopyOnWriteArrayList<>();
    private Path musicDir;

    public List<Track> getTracks() {
        return tracks;
    }

    public Path getMusicDirectory() {
        return musicDir;
    }

    /** 实际扫描过的所有 music 目录（用于界面提示）。 */
    public List<Path> getScannedDirs() {
        return scannedDirs;
    }

    /**
     * 重新扫描目录。
     * <p>同时扫描以下几处并合并去重（FCL 等启动器开启版本隔离时，
     * 玩家常把歌放在 .minecraft/music，而游戏运行目录是 versions/xxx）：
     * <ul>
     *   <li>{gameDir}/music</li>
     *   <li>{.minecraft}/music（gameDir 位于 versions/<版本> 时）</li>
     *   <li>{gameDir 的上一级}/music</li>
     * </ul>
     */
    public synchronized void refresh() {
        tracks.clear();
        scannedDirs.clear();
        musicDir = resolveMusicDir();

        List<Path> roots = new ArrayList<>();
        addRoot(roots, musicDir);
        try {
            Path game = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
            Path parent = game.getParent();
            if (parent != null && "versions".equals(fileNameOf(parent))) {
                Path mc = parent.getParent();
                if (mc != null) addRoot(roots, mc.resolve("music"));
            }
            if (parent != null) addRoot(roots, parent.resolve("music"));
        } catch (Throwable ignored) {
        }

        Set<String> seen = new LinkedHashSet<>();
        for (Path root : roots) {
            scannedDirs.add(root);
            try {
                Files.createDirectories(root);
            } catch (Throwable ignored) {
            }
            if (!Files.isDirectory(root)) continue;
            try (var walk = Files.walk(root)) {
                List<Path> found = walk.filter(Files::isRegularFile)
                        .filter(p -> supported(p.getFileName().toString()))
                        .sorted(Comparator.comparing(
                                p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
                for (Path p : found) {
                    String key = p.toAbsolutePath().normalize().toString();
                    if (seen.add(key)) {
                        tracks.add(new Track(displayName(p), p));
                    }
                }
            } catch (Throwable e) {
                System.err.println("[GMP] 扫描音乐目录失败 " + root + ": " + e);
            }
        }
        tracks.sort(Comparator.comparing(t -> t.name().toLowerCase(Locale.ROOT)));
        System.out.println("[GMP] 扫描完成，共 " + tracks.size() + " 首，目录: " + scannedDirs);
    }

    private static void addRoot(List<Path> roots, Path p) {
        if (p == null) return;
        Path n = p.toAbsolutePath().normalize();
        for (Path r : roots) {
            if (r.equals(n)) return;
        }
        roots.add(n);
    }

    private static String fileNameOf(Path p) {
        Path f = p.getFileName();
        return f == null ? "" : f.toString();
    }

    private static boolean supported(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return false;
        return SUPPORTED.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String displayName(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** 解析音乐目录：游戏运行目录下的 music。 */
    public static Path resolveMusicDir() {
        File gd = null;
        try {
            gd = Minecraft.getInstance().gameDirectory;
        } catch (Throwable ignored) {
        }
        Path base = gd != null ? gd.toPath() : Path.of(".");
        return base.resolve("music");
    }
}
