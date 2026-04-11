package com.ming.rag.domain.query.port;

import com.ming.rag.domain.query.RegisteredTool;
import java.util.List;

public interface ToolRegistryPort {

    List<RegisteredTool> listRegisteredTools();

    List<RegisteredTool> resolveTools(List<String> candidateTools);
}
