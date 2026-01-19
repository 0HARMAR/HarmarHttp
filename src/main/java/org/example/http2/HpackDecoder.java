package org.example.http2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.example.http2.HuffmanTable.HUFFMAN_TABLE;

public class HpackDecoder {

    private HpackDynamicTable hpackDynamicTable;
    private static final String[][] STATIC_TABLE = StaticTable.TABLE;
    private static HuffmanNode HUFFMAN_ROOT = null;

    public HpackDecoder(HpackDynamicTable hpackDynamicTable) {
        this.hpackDynamicTable = hpackDynamicTable;
        this.HUFFMAN_ROOT = buildHuffmanTrie();
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

    // --------- 字符串解码 ---------
    private String decodeString(ByteArrayInputStream in, int firstByte) throws IOException {
        boolean huffman = (firstByte & 0x80) != 0;
        int length = decodeInteger(in, 7, firstByte);

        byte[] buf = new byte[length];
        int read = in.read(buf);
        if (read != length) throw new IOException("Unexpected string length");

        if (huffman) {
            byte[] decoded = huffmanDecode(buf);
            return new String(decoded, StandardCharsets.UTF_8);
        } else {
            return new String(buf, StandardCharsets.UTF_8);
        }
    }


    HuffmanNode buildHuffmanTrie() {
        HuffmanNode root = new HuffmanNode();

        for (int[] entry : HUFFMAN_TABLE) {
            int symbol = entry[0];
            int bits = entry[1];
            int len = entry[2];

            HuffmanNode node = root;
            for (int i = len - 1; i >= 0; i--) {
                int bit = (bits >> i) & 1;
                if (bit == 0) {
                    if (node.zero == null) node.zero = new HuffmanNode();
                    node = node.zero;
                } else {
                    if (node.one == null) node.one = new HuffmanNode();
                    node = node.one;
                }
            }
            node.symbol = symbol;
        }
        return root;
    }

    byte[] huffmanDecode(byte[] encoded) throws IOException {
        BitReader reader = new BitReader(encoded);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        HuffmanNode node = HUFFMAN_ROOT;

        while (reader.hasMoreBits()) {
            int bit = reader.readBit();
            node = (bit == 0) ? node.zero : node.one;

            if (node == null) {
                throw new IOException("Invalid Huffman code");
            }

            if (node.symbol != -1) {
                if (node.symbol == 256) {
                    throw new IOException("Unexpected EOS");
                }
                out.write(node.symbol);
                node = HUFFMAN_ROOT;
            }
        }

        return out.toByteArray();
    }

    public Map<String, String> decode(Frame frame) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        ByteArrayInputStream in = new ByteArrayInputStream(frame.payload);

        if (frame.header.FrameFlags.contains(FrameFlag.PADDED)) {
            // Pad Length (1 byte)
            in.read();
        }

        if (frame.header.FrameFlags.contains(FrameFlag.PRIORITY)) {
            // Priority (5 bytes)
            byte[] priority = new byte[5];
            in.read(priority);
        }

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
