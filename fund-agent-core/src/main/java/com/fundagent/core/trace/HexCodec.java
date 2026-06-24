package com.fundagent.core.trace;

final class HexCodec {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private HexCodec() {
    }

    static String encode(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = HEX[value >>> 4];
            result[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(result);
    }

    static byte[] decode(String value) {
        if (value == null || value.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hexadecimal value");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hexadecimal value");
            }
            result[i / 2] = (byte) ((high << 4) | low);
        }
        return result;
    }
}
