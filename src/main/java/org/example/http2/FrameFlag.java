package org.example.http2;

import java.util.EnumSet;

public enum FrameFlag {
    END_STREAM(0x1, "数据流结束"),
    ACK(0x1, "对 SETTINGS 或 PING 的确认"),
    END_HEADERS(0x4, "HEADERS 或 CONTINUATION 结束"),
    PADDED(0x8, "帧被填充"),
    PRIORITY(0x20, "HEADERS 帧中包含优先级信息");

    private final int value;
    private final String description;

    FrameFlag(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "0x" + Integer.toHexString(value) + " (" + name() + "): " + description;
    }

    public static EnumSet<FrameFlag> parse(int flagsByte) {
        EnumSet<FrameFlag> set = EnumSet.noneOf(FrameFlag.class);
        for (FrameFlag flag : values()) {
            if ((flagsByte & flag.value) != 0) {
                set.add(flag);
            }
        }
        return set;
    }

    public int getFlagBit() {
        return value;
    }
}
