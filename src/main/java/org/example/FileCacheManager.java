package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class FileCacheManager {
    private final long maxSizeBytes;
    private final AtomicLong currentSize = new AtomicLong(0);

    private final TrieNode root = new TrieNode(); // URL path Trie root
    private final ConcurrentHashMap<String, CacheEntity> cacheMap = new ConcurrentHashMap<>(); // URL path -> CacheEntity

    // LFU structure: access frequency ->
    // double linked list store same frequency key
    private final Map<Integer, LinkedHashSet<String>> frequencyMap = new HashMap<>();
    private final Map<String, Integer> keyFrequency = new HashMap<>();
    private int minFrequency = 0;

    // counts
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictCount = new AtomicLong();

    // hot reload
    private final WatchService watchService;
    private final Thread watchThread;
    private final Path watchRoot;

    public FileCacheManager(long maxSizeBytes, Path watchRoot) throws IOException{
        this.maxSizeBytes = maxSizeBytes;
        this.watchRoot = watchRoot;

        // init file listener
        this.watchService = FileSystems.getDefault().newWatchService();
        registerAllDirs(watchRoot);

        this.watchThread = new Thread(this::watchLoop, "FileCacheWatcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public CacheEntity get(String urlPath) {
        CacheEntity entry = cacheMap.get(urlPath);
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }

        // check whether file modified
        try {
            Path path = Paths.get(entry.filePath);
            if (Files.exists(path)) {
                long modTime = Files.getLastModifiedTime(path).toMillis();
                if (modTime != entry.lastModified) {
                    remove(urlPath); // file out of time
                    missCount.incrementAndGet();
                    return null;
                }
            }
        } catch (IOException e) {
            remove(urlPath); // file not found
            missCount.incrementAndGet();
            return null;
        }

        hitCount.incrementAndGet();
        increaseFrequency(urlPath);
        return entry;
    }

    public void put(String urlPath,CacheEntity entry) {
        if (entry.content.length > maxSizeBytes) {return;}

        synchronized (this) {
            // delete old cache
            if (cacheMap.containsKey(urlPath)) remove(urlPath);

            // evict cache util space enough
            while(currentSize.get() + entry.content.length > maxSizeBytes) {
                evictLFU();
            }
            cacheMap.put(urlPath, entry);
            insertTrie(urlPath);
            currentSize.addAndGet(entry.content.length);

            // init access frequency
            keyFrequency.put(urlPath, 1);
            frequencyMap.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(urlPath);
            minFrequency = 1;
        }
    }

    public static class CacheEntity {
        public final byte[] content;
        public final String contentType;
        public final long lastModified;
        public final String filePath;

        public CacheEntity(byte[] content,String contentType,long lastModified, String filePath) {
            this.content = content;
            this.contentType = contentType;
            this.lastModified = lastModified;
            this.filePath = filePath;
        }
    }

    public void clearCache() {
        cacheMap.clear();
        keyFrequency.clear();
        frequencyMap.clear();
        currentSize.set(0);
        minFrequency = 0;
        root.children.clear();
    }

    public Map<String, Long> stats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("hit", hitCount.get());
        stats.put("miss", missCount.get());
        stats.put("evict", evictCount.get());
        stats.put("currentSize", currentSize.get());
        stats.put("cacheCount", (long)cacheMap.size());
        return stats;
    }

    /* =============== LFU + LRU evict ================ */
    private void increaseFrequency(String key){
        int freq = keyFrequency.get(key);
        LinkedHashSet<String> set = frequencyMap.get(freq);
        set.remove(key);

        if (freq == minFrequency && set.isEmpty()) {
            minFrequency++;
        }

        keyFrequency.put(key, freq + 1);
        frequencyMap.computeIfAbsent(freq + 1, k -> new LinkedHashSet<>()).add(key);
    }

    private void evictLFU() {
        LinkedHashSet<String> set = frequencyMap.get(minFrequency);
        if (set == null || set.isEmpty()) return;

        // LRU: fetch first insert key
        String evictKey = set.iterator().next();
        set.remove(evictKey);

        CacheEntity entity = cacheMap.remove(evictKey);
        if (entity != null) {
            currentSize.addAndGet(-entity.content.length);
        }

        keyFrequency.remove(evictKey);
        removeTrie(evictKey);
        evictCount.incrementAndGet();

        // update minFrequency
        if (set.isEmpty()) {
            frequencyMap.remove(minFrequency);
            // next Frequency is minFrequency
            minFrequency = frequencyMap.keySet().stream().min(Integer::compareTo).orElse(0);
        }
    }

    private void remove(String key) {
        CacheEntity entity = cacheMap.remove(key);
        if (entity != null) {
            currentSize.addAndGet(-entity.content.length);
        }

        Integer freq = keyFrequency.remove(key);
        if (freq != null) {
            LinkedHashSet<String> set = frequencyMap.get(freq);
            if (set != null) {
                set.remove(key);
            }
        }
        removeTrie(key);
    }

     /* =============== Trie ================ */
    private static class TrieNode {
        Map<String, TrieNode> children = new HashMap<>();
        boolean isEnd = false;
    }

    private void insertTrie(String urlPath) {
        String[] parts = urlPath.split("/");
        TrieNode node = root;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            node = node.children.computeIfAbsent(p, k -> new TrieNode());
        }
        node.isEnd = true;
    }

    private void removeTrie(String urlPath) {
        removeTrie(root, urlPath.split("/"), 0);
    }

    private boolean removeTrie(TrieNode node, String[] parts, int index) {
        if (index == parts.length) {
            node.isEnd = false;
            return node.children.isEmpty();
        }
        String part = parts[index];
        TrieNode child = node.children.get(part);
        if (child == null) return false;
        boolean shouldDelete = removeTrie(child, parts, index + 1);
        if (shouldDelete) {
            node.children.remove(part);
        }
        return node.children.isEmpty() && !node.isEnd;
    }

    /* ================= hot reload ================ */
    private void watchLoop() {
        try {
            while(true) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {

                    Path changed = ((Path) event.context());
                    Path fullPath = ((Path) key.watchable()).resolve(changed);
                    String urlPath = watchRoot.resolve(fullPath).toString().replace("\\", "/");

                    remove(urlPath);
                    System.out.println("[HOT-RELOAD] file update: " + urlPath);
                }
                key.reset();
            }
        } catch (InterruptedException ignored) {
        }
        catch (ClosedWatchServiceException ignored) {
        }
    }

    private void registerAllDirs(Path start) throws IOException {
        Files.walk(start).filter(Files::isDirectory).forEach(path -> {
            try {
                path.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
