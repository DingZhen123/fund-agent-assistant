package com.fundagent.core.memory;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class SharedMemory {
    private final Map<String, Object> store = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        store.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) store.get(key);
    }

    public Map<String, Object> snapshot() {
        return new HashMap<>(store);
    }

    public void clear() {
        store.clear();
    }
}
