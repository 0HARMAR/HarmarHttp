package org.example.http2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class HpackDecoder {

    private HpackDynamicTable hpackDynamicTable;
    private static final String[][] STATIC_TABLE = StaticTable.TABLE;

    public HpackDecoder(HpackDynamicTable hpackDynamicTable) {
        this.hpackDynamicTable = hpackDynamicTable;
    }

    // --------- 前缀整数解码 ---------
    private static int decodeInteger(ByteArrayInputStream in, int prefixBits, int firstByte) throws IOException {
        int maxPrefix = (1 << prefixBits) - 1;
        int value = firstByte & maxPrefix;
        if (value < maxPrefix) {
            return value;
        }
        int m = 0;
        int b;
        while (true) {
            b = in.read();
            if (b == -1) throw new IOException("Unexpected end of stream");
            value += (b & 0x7F) << m;
            if ((b & 0x80) == 0) break;
            m += 7;
        }
        return value;
    }

    // --------- 字符串解码 (不处理 Huffman) ---------
    private static String decodeString(ByteArrayInputStream in, int firstByte) throws IOException {
        boolean huffman = (firstByte & 0x80) != 0;
        int length = decodeInteger(in, 7, firstByte);
        byte[] buf = new byte[length];
        int read = in.read(buf);
        if (read != length) throw new IOException("Unexpected string length");
        if (huffman) throw new IOException("Huffman not supported in stage0");
        return new String(buf, "UTF-8");
    }

    public Map<String, String> decode(byte[] payload) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        ByteArrayInputStream in = new ByteArrayInputStream(payload);

        while (in.available() > 0) {
            int firstByte = in.read();
            if (firstByte == -1) break;

            if ((firstByte & 0x80) != 0) { // Indexed Header Field
                int index = decodeInteger(in, 7, firstByte);
                if (index < 1 || index > STATIC_TABLE.length) throw new IOException("Invalid static table index: " + index);
                String name = STATIC_TABLE[index - 1][0];
                String value = STATIC_TABLE[index - 1][1];
                headers.put(name, value);
            } else if ((firstByte & 0xF0) == 0x00) { // Literal Header Field without indexing
                // ---- 1️⃣ 解析 name ----
                String name;
                if ((firstByte & 0x0F) != 0) {
                    // indexed name
                    int nameIndex = decodeInteger(in, 4, firstByte);
                    if (nameIndex < 1 || nameIndex > STATIC_TABLE.length) {
                        throw new IOException("Invalid name index: " + nameIndex);
                    }
                    name = STATIC_TABLE[nameIndex - 1][0];
                } else {
                    // literal name
                    int nameFirst = in.read();
                    if (nameFirst == -1) throw new IOException("Unexpected end of stream");
                    name = decodeString(in, nameFirst);
                }

                // ---- 2️⃣ 解析 value（一定是字符串）----
                int valueFirst = in.read();
                if (valueFirst == -1) throw new IOException("Unexpected end of stream");
                String value = decodeString(in, valueFirst);

                headers.put(name, value);
            } else if ((firstByte & 0x40) != 0) { // Literal Header Field with Incremental Indexing
                // ---- 1️⃣ 解析 name ----
                String name;
                if ((firstByte & 0x3F) != 0) { // 前6位 != 0，表示使用静态表或动态表索引
                    int nameIndex = decodeInteger(in, 6, firstByte);
                    if (nameIndex <= STATIC_TABLE.length) {
                        // 静态表索引
                        name = STATIC_TABLE[nameIndex - 1][0];
                    } else {
                        // 动态表索引
                        int dynIndex = nameIndex - STATIC_TABLE.length - 1;
                        name = hpackDynamicTable.getEntry(dynIndex).getName();
                    }
                } else {
                    // literal name
                    int nameFirst = in.read();
                    if (nameFirst == -1) throw new IOException("Unexpected end of stream");
                    name = decodeString(in, nameFirst);
                }

                // ---- 2️⃣ 解析 value ----
                int valueFirst = in.read();
                if (valueFirst == -1) throw new IOException("Unexpected end of stream");
                String value = decodeString(in, valueFirst);

                // ---- 3️⃣ 加入动态表 ----
                hpackDynamicTable.addEntry(name, value);

                headers.put(name, value);
            }
            else if ((firstByte & 0x10) != 0) { // Literal Never Indexed
                // 阶段0先跳过
                System.out.println("Skipping never-indexed header (stage0)");
            } else {
                throw new IOException("Unknown header type: " + firstByte);
            }
        }

        return headers;
    }
}
