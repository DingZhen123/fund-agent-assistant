package com.fundagent.core.trace;

import lombok.Value;

@Value
public class TraceSignature {
    String algorithm;
    String keyId;
    String value;
}
