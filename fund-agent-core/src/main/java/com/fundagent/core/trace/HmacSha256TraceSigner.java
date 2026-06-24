package com.fundagent.core.trace;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

public class HmacSha256TraceSigner implements TraceSigner {
    public static final String ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_KEY_BYTES = 32;

    private final String keyId;
    private final byte[] key;

    public HmacSha256TraceSigner(String keyId, byte[] key) {
        this.keyId = requireText(keyId, "keyId");
        if (key == null || key.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("HMAC key must contain at least 32 bytes");
        }
        this.key = Arrays.copyOf(key, key.length);
    }

    @Override
    public TraceSignature sign(String value) {
        return new TraceSignature(ALGORITHM, keyId, HexCodec.encode(mac(value)));
    }

    @Override
    public boolean verify(String value, TraceSignature signature) {
        if (signature == null
                || !ALGORITHM.equals(signature.getAlgorithm())
                || !keyId.equals(signature.getKeyId())
                || signature.getValue() == null) {
            return false;
        }
        byte[] expected = mac(value);
        byte[] actual;
        try {
            actual = HexCodec.decode(signature.getValue());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] mac(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate Trace HMAC", e);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
