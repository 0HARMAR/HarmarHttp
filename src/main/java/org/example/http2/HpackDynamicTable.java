package org.example.http2;

import java.util.Deque;
import java.util.LinkedList;

public class HpackDynamicTable {
    private int maxSize;            // 动态表容量上限
    private int currentSize;        // 当前占用大小
    private Deque<HpackDynamicEntry> entries;
    private int currentIndex = 61;

    public HpackDynamicTable(int maxSize) {
        this.maxSize = maxSize;
        this.currentSize = 0;
        this.entries = new LinkedList<>();
    }

    // 添加新条目（可能会触发淘汰旧条目）
    public void addEntry(String name, String value) {
        HpackDynamicEntry entry = new HpackDynamicEntry(name, value, ++currentIndex);
        // 淘汰直到能放下
        while (!entries.isEmpty() && (currentSize + entry.getSize() > maxSize)) {
            HpackDynamicEntry removed = entries.removeLast();
            currentSize -= removed.getSize();
        }
        // 如果条目本身大于最大容量，直接丢弃
        if (entry.getSize() <= maxSize) {
            entries.addFirst(entry);
            currentSize += entry.getSize();
        }
    }

    public HpackDynamicEntry getEntry(int index) {
        if (index > entries.size()) {
            System.out.println("Index out of range: " + index);
            return null;
        }
        return ((LinkedList<HpackDynamicEntry>) entries).get(index);
    }

    public HpackDynamicEntry name(String name) {
        for (HpackDynamicEntry entry : entries) {
            if (entry.getName().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int newSize) {
        this.maxSize = newSize;
        // 调整容量时可能需要删除条目
        while (!entries.isEmpty() && currentSize > maxSize) {
            HpackDynamicEntry removed = entries.removeLast();
            currentSize -= removed.getSize();
        }
    }
}

