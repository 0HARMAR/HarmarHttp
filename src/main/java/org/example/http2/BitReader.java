package org.example.http2;

class BitReader {
    byte[] data;
    int bytePos = 0;
    int bitPos = 0;

    BitReader(byte[] data) {
        this.data = data;
    }

    boolean hasMoreBits() {
        return bytePos < data.length;
    }

    int readBit() {
        int bit = (data[bytePos] >> (7 - bitPos)) & 1;
        bitPos++;
        if (bitPos == 8) {
            bitPos = 0;
            bytePos++;
        }
        return bit;
    }
}