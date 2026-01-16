package org.example.http2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class HpackEncoder {
    private HpackDynamicTable dynamicTable;

    public HpackEncoder(HpackDynamicTable dynamicTable) {
        this.dynamicTable = dynamicTable;
    }

    public byte[] encode(Map<String, String> headers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (Map.Entry<String, String> e : headers.entrySet()) {
            String name = e.getKey();
            String value = e.getValue();

            // 1️⃣ 静态表全匹配 → Indexed
            Integer full = StaticTable.full(name, value);
            if (full != null) {
                out.write(0x80 | full);
                continue;
            }

            // 2️⃣ Literal without indexing(or incremental indexing)
            Integer nameIdx = StaticTable.name(name);
            if (nameIdx != null) {
                // Literal without indexing - Indexed Name
                // 前缀: 0000 (4 bits) + 索引
                writeInteger(out, 0x00, 4, nameIdx);
                // 值字符串
                writeString(out, value, false);
            } else {
                HpackDynamicEntry dynamicEntry = dynamicTable.name(name);
                if (dynamicEntry != null) {
                    // Literal with incremental indexing
                    int dynamicIdx = dynamicEntry.getIndex();

                    // 前缀: 01xxxxxx (6 bits index)
                    writeInteger(out, 0x40, 6, dynamicIdx);

                    writeString(out, value, false);

                    dynamicTable.addEntry(name, value);
                } else {
                    // not in static table or dynamic table
                    // send with Literal without indexing - New Name
                    out.write(0x00); // 00000000: 类型+新名称标记

                    // 名称长度（H=0）和名称
                    writeInteger(out, 0x00, 7, name.length());
                    writeString(out, name, false);

                    // 值长度（H=0）和值
                    writeInteger(out, 0x00, 7, value.length());
                    writeString(out, value, false);
                }
            }
        }

        return out.toByteArray();
    }

    /**
     * 写入可变长度整数 (RFC 7541 5.1)
     * @param prefixMask 前缀掩码 (如 0x80, 0x00)
     * @param prefixBits 前缀位数 (如 7, 4)
     * @param value 要编码的整数值
     */
    private void writeInteger(ByteArrayOutputStream out, int prefixMask, int prefixBits, int value) {
        int maxPrefixValue = (1 << prefixBits) - 1;

        if (value < maxPrefixValue) {
            // 值可以放入前缀中
            out.write(prefixMask | value);
        } else {
            // 前缀放满，使用后续字节
            out.write(prefixMask | maxPrefixValue);
            value -= maxPrefixValue;

            while (value >= 128) {
                // 延续位=1
                out.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            // 最后字节，延续位=0
            out.write(value);
        }
    }

    /**
     * 写入字符串（支持Huffman编码）
     * @param huffman true=使用Huffman编码，false=不使用
     */
    private void writeString(ByteArrayOutputStream out, String str, boolean huffman) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);

        if (huffman) {
            // 这里可以添加Huffman编码逻辑
            byte[] encoded = huffmanEncode(bytes); // 需要实现
            writeInteger(out, 0x80, 7, encoded.length); // H=1
            out.write(encoded, 0, encoded.length);
        } else {
            writeInteger(out, 0x00, 7, bytes.length); // H=0
            out.write(bytes, 0, bytes.length);
        }
    }

    // 示例：Huffman编码方法（需要完整实现）
    private byte[] huffmanEncode(byte[] input) {
        // 这里应该实现HPACK的Huffman编码表
        // 暂时返回原始数据作为示例
        return input;
    }
}
