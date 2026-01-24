package org.example.http2;

/**
 * HTTP/2 错误码枚举
 * 参考：RFC 7540 Section 7
 */
public enum ErrorCode {
    /**
     * 没有错误 (0x0)
     */
    NO_ERROR(0x0, "No error"),

    /**
     * 协议错误 (0x1)
     * 端点检测到不明确协议错误
     */
    PROTOCOL_ERROR(0x1, "Protocol error"),

    /**
     * 内部错误 (0x2)
     * 端点遇到意外的内部错误
     */
    INTERNAL_ERROR(0x2, "Internal error"),

    /**
     * 流量控制错误 (0x3)
     * 端点检测到其对端违反了流量控制协议
     */
    FLOW_CONTROL_ERROR(0x3, "Flow control error"),

    /**
     * 设置超时 (0x4)
     * 端点发送了一个 SETTINGS 帧，但没有及时收到响应
     */
    SETTINGS_TIMEOUT(0x4, "Settings timeout"),

    /**
     * 流关闭 (0x5)
     * 端点在流半关闭后接收到帧
     */
    STREAM_CLOSED(0x5, "Stream closed"),

    /**
     * 帧大小错误 (0x6)
     * 端点接收到大小无效的帧
     */
    FRAME_SIZE_ERROR(0x6, "Frame size error"),

    /**
     * 拒绝流 (0x7)
     * 端点在执行任何应用处理之前拒绝流
     */
    REFUSED_STREAM(0x7, "Refused stream"),

    /**
     * 取消 (0x8)
     * 用于指示不再需要所请求的流
     */
    CANCEL(0x8, "Cancel"),

    /**
     * 压缩错误 (0x9)
     * 端点无法维护首部压缩的上下文
     */
    COMPRESSION_ERROR(0x9, "Compression error"),

    /**
     * 连接错误 (0xA)
     * 为特定连接建立的压缩上下文所需的连接错误
     */
    CONNECT_ERROR(0xA, "Connect error"),

    /**
     * 增强你的冷静 (0xB)
     * 端点检测到其对端表现出可能产生过大负载的行为
     */
    ENHANCE_YOUR_CALM(0xB, "Enhance your calm"),

    /**
     * 不足的安全性 (0xC)
     * 基础传输的属性不符合最低安全要求
     */
    INADEQUATE_SECURITY(0xC, "Inadequate security"),

    /**
     * 需要 HTTP/1.1 (0xD)
     * 端点要求使用 HTTP/1.1 而非 HTTP/2
     */
    HTTP_1_1_REQUIRED(0xD, "HTTP/1.1 required");

    private final int code;
    private final String description;

    ErrorCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取错误码数值
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取错误描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 通过数值查找错误码枚举
     * @param code 错误码数值
     * @return 对应的错误码枚举，未找到时返回 null
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return null;
    }

    /**
     * 通过数值查找错误码枚举，未找到时抛出异常
     * @param code 错误码数值
     * @return 对应的错误码枚举
     * @throws IllegalArgumentException 当错误码无效时抛出
     */
    public static ErrorCode fromCodeOrThrow(int code) {
        ErrorCode errorCode = fromCode(code);
        if (errorCode == null) {
            throw new IllegalArgumentException("Invalid error code: 0x" + Integer.toHexString(code));
        }
        return errorCode;
    }

    /**
     * 判断给定的数值是否为有效的错误码
     */
    public static boolean isValidCode(int code) {
        return fromCode(code) != null;
    }

    /**
     * 获取十六进制表示的代码
     */
    public String getHexCode() {
        return String.format("0x%02X", code);
    }

    @Override
    public String toString() {
        return name() + " (" + getHexCode() + ") - " + description;
    }

    /**
     * 获取所有错误码的格式化字符串
     */
    public static String getAllErrorCodes() {
        StringBuilder sb = new StringBuilder("HTTP/2 Error Codes:\n");
        for (ErrorCode code : values()) {
            sb.append(String.format("  %-25s %-8s %s\n",
                    code.name(),
                    code.getHexCode(),
                    code.getDescription()));
        }
        return sb.toString();
    }
}