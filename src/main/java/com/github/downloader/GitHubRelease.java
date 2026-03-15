package com.github.downloader;

import java.util.List;

public class GitHubRelease {
    public String tagName;
    public String name;
    public String body;
    public String publishedAt;
    public boolean prerelease;
    public List<Asset> assets;

    public static class Asset {
        public String name;
        public String browserDownloadUrl;
        public long size;
        public String contentType;

        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
