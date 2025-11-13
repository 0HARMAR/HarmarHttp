package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileCacheManager {
    private final long maxSizeBytes;
    private final Map<String,CacheEntity> cache;
    private long currentSize;

    public FileCacheManager(long maxSizeBytes,int maxEntries) {
        this.maxSizeBytes = maxSizeBytes;
        this.cache = Collections.synchronizedMap(
                new LinkedHashMap<String,CacheEntity>(maxEntries,0.75f,true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, CacheEntity> eldest) {
                        synchronized (FileCacheManager.this) {
                            return size() > maxEntries || currentSize > maxSizeBytes;
                        }
                    }
                }
        );
    }

    public CacheEntity get(String filePath) {
        return cache.computeIfPresent(filePath,(key,entry) -> {
            try {
                Path path = Paths.get(filePath);
                if (Files.exists(path)) {
                    long currentModTime = Files.getLastModifiedTime(path).toMillis();
                    if (currentModTime != entry.lastModified) {
                        return null;    // cache filed
                    }
                }
            } catch (IOException e) {
                return null;
            }
            return entry;
        });
    }

    public void put(String filePath,CacheEntity entry) {
        if (entry.content.length > 1 * 1024 * 1024) {return;}

        synchronized (this) {
            CacheEntity oldEntry = cache.remove(filePath);
            if (oldEntry != null) {
                currentSize -= oldEntry.content.length;
            }

            // add new entry
            currentSize += entry.content.length;
            cache.put(filePath, entry);
        }
    }
    public static class CacheEntity {
        public final byte[] content;
        public final String contentType;
        public final long lastModified;

        public CacheEntity(byte[] content,String contentType,long lastModified) {
            this.content = content;
            this.contentType = contentType;
            this.lastModified = lastModified;
        }
    }

    /*
     * 隐藏功能:缓存清空术!
     * 　∧＿∧　
     * （｡･ω･｡)つ━☆・*。
     * ⊂　　 ノ 　　　・゜+.
     * 　しーＪ　　　°。+ *´¨)
     * 　　　　　　　　　.· ´¸.·*´¨)
     * 　　　　　　　　　　(¸.·´ (¸.·'* ☆ 咻咻!
     */
    public void clearCache() {
        cache.clear();
        currentSize = 0;
    }
}
