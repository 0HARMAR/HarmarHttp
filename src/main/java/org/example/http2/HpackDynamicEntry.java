package org.example.http2;

public class HpackDynamicEntry {
    private String name;   // header name
    private String value;  // header value
    private int index;
    private int size;      // size in bytes according to HPACK规则：32 + name.length + value.length

    public HpackDynamicEntry(String name, String value, int index) {
        this.name = name;
        this.value = value;
        this.index = index;
        this.size = 32 + name.length() + value.length();
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public int getSize() {
        return size;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}
