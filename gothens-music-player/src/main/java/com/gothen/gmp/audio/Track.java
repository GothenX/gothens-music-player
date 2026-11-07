package com.gothen.gmp.audio;

import java.nio.file.Path;

/**
 * 一条曲目。name 为展示名（不含扩展名），path 为文件路径。
 */
public record Track(String name, Path path) {
}
