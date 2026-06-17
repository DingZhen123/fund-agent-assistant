package com.fundagent.server.rag;

import java.util.List;

public interface EmbeddingService {
    List<Double> embed(String text);
}
