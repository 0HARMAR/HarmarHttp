package org.example.http2;

public enum FrameType {
    DATA(0, "传输数据"),
    HEADERS(1, "发送请求或响应头"),
    PRIORITY(2, "设置流的优先级"),
    RST_STREAM(3, "重置流"),
    SETTINGS(4, "交换参数设置"),
    PUSH_PROMISE(5, "服务器推送承诺"),
    PING(6, "心跳检测"),
    GOAWAY(7, "通知连接关闭"),
    WINDOW_UPDATE(8, "流量控制更新"),
    CONTINUATION(9, "续传 HEADERS 或 PUSH_PROMISE");

    private final int typeCode;
    private final String description;

    FrameType(int typeCode, String description) {
        this.typeCode = typeCode;
        this.description = description;
    }

    public int getTypeCode() {
        return typeCode;
    }

    public String getDescription() {
        return description;
    }

    // 根据类型码获取枚举实例
    public static FrameType fromTypeCode(int code) {
        for (FrameType type : values()) {
            if (type.typeCode == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown frame type code: " + code);
    }

    @Override
    public String toString() {
        return typeCode + " (" + name() + "): " + description;
    }
}
