package com.fundagent.core.trace;

import java.util.Map;

public interface TraceCanonicalizer {

    String canonicalizeJson(String json);

    String canonicalizeFields(Map<String, Object> fields);
}
