package com.fundagent.core.trace;

public interface TraceSigner {

    TraceSignature sign(String value);

    boolean verify(String value, TraceSignature signature);
}
