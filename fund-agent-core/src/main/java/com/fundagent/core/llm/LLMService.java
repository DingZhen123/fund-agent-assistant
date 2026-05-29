package com.fundagent.core.llm;

import com.fundagent.common.model.Message;
import java.util.List;
import java.util.function.Consumer;

public interface LLMService {
    String chat(String systemPrompt, List<Message> history, String currentMessage);

    String chatStructured(String systemPrompt, List<Message> history, String currentMessage,
                          String schemaName, String schemaJson);

    String chatStream(String systemPrompt, List<Message> history,
                      String currentMessage, Consumer<String> onToken);
}
