package com.fundagent.core.trace;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DefaultTraceCanonicalizer implements TraceCanonicalizer {

    @Override
    public String canonicalizeJson(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        try {
            return JSON.toJSONString(normalize(JSON.parse(json)));
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Trace payload must be valid JSON", e);
        }
    }

    @Override
    public String canonicalizeFields(Map<String, Object> fields) {
        return JSON.toJSONString(normalize(fields));
    }

    private Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), normalize(item)));
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>(collection.size());
            collection.forEach(item -> normalized.add(normalize(item)));
            return normalized;
        }
        if (value.getClass().isArray()) {
            return normalize(JSON.parseArray(JSON.toJSONString(value)));
        }
        if (value instanceof BigInteger integer) {
            return integer;
        }
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString()).stripTrailingZeros();
            return decimal.scale() < 0 ? decimal.setScale(0) : decimal;
        }
        return normalize(JSON.parse(JSON.toJSONString(value)));
    }
}
