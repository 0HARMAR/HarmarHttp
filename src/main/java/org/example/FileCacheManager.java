package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class FileCacheManager {

    // hot reload
    private final WatchService watchService;
    private final Thread watchThread;
    private final Path watchRoot;

    public FileCacheManager(Path watchRoot) throws IOException{
        this.watchRoot = watchRoot;

        // init file listener
        this.watchService = FileSystems.getDefault().newWatchService();
        registerAllDirs(watchRoot);

        this.watchThread = new Thread(this::watchLoop, "FileCacheWatcher");
        watchThread.setDaemon(true);
        watchThread.start();
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
