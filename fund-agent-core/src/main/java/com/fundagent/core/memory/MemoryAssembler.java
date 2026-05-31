package com.fundagent.core.memory;

public interface MemoryAssembler {
    MemoryContext assemble(Memory memory, String userMessage, MemoryUseCase useCase);
}
