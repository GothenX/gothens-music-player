package com.gothen.gmp.audio;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「喜欢」列表，保存在 游戏目录/config/gmp-favorites.txt（每行一个文件路径）。
 */
public final class Favorites {
    private static final Set<String> PATHS = ConcurrentHashMap.newKeySet();
    private static volatile boolean loaded = false;

    private Favorites() {
    }

    public static boolean isFavorite(Track t) {
        load();
        return PATHS.contains(key(t.path()));
    }

    public static boolean toggle(Track t) {
        load();
        String k = key(t.path());
        boolean now;
        if (PATHS.contains(k)) {
            PATHS.remove(k);
            now = false;
        } else {
            PATHS.add(k);
            now = true;
        }
        save();
        return now;
    }

    public static List<Track> filter(List<Track> all) {
        load();
        return all.stream().filter(Favorites::isFavorite).toList();
    }

    private static String key(Path p) {
        return p.toAbsolutePath().normalize().toString();
    }

    private static Path file() {
        Path base;
        try {
            base = Minecraft.getInstance().gameDirectory.toPath();
        } catch (Throwable e) {
            base = Path.of(".");
        }
        return base.resolve("config").resolve("gmp-favorites.txt");
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        try {
            if (Files.isRegularFile(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String s = line.trim();
                    if (!s.isEmpty()) PATHS.add(s);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static synchronized void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Files.write(f, PATHS, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
