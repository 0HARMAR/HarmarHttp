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

    public static EnumSet<FrameFlag> parse(FrameType type, int flagsByte) {
        EnumSet<FrameFlag> set = EnumSet.noneOf(FrameFlag.class);

        switch (type) {
            case HEADERS, DATA -> {
                if ((flagsByte & 0x1) != 0) set.add(END_STREAM);
                if ((flagsByte & 0x4) != 0) set.add(END_HEADERS);
                if ((flagsByte & 0x8) != 0) set.add(PADDED);
                if ((flagsByte & 0x20) != 0) set.add(PRIORITY);
            }
            case SETTINGS, PING -> {
                if ((flagsByte & 0x1) != 0) set.add(ACK);
            }
            default -> {}
        }

        return set;
    }


    public int getFlagBit() {
        return value;
    }
}
