package org.example.http2;

public enum SettingsConfig {

    HEADER_TABLE_SIZE(0x1, 4096, "HPACK Header 压缩表大小"),
    ENABLE_PUSH(0x2, 1, "是否允许服务器 push"),
    MAX_CONCURRENT_STREAMS(0x3, Integer.MAX_VALUE, "最大并发 stream 数"),
    INITIAL_WINDOW_SIZE(0x4, 65535, "每个 stream 的初始 flow-control 窗口大小"),
    MAX_FRAME_SIZE(0x5, 16384, "允许的最大帧大小"),
    MAX_HEADER_LIST_SIZE(0x6, Integer.MAX_VALUE, "允许的 header list 最大长度");

    private final int id;
    private final int defaultValue;
    private final String description;

    SettingsConfig(int id, int defaultValue, String description) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 ID 查找对应的枚举
     */
    public static SettingsConfig fromId(int id) {
        for (SettingsConfig s : values()) {
            if (s.id == id) return s;
        }
        return null; // 未知设置项
    }
}