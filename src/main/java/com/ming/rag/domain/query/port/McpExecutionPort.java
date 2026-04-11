package com.ming.rag.domain.query.port;

import com.ming.rag.domain.query.ToolExecutionResult;
import com.ming.rag.domain.query.ToolExecutionRequest;
import java.util.List;

public interface McpExecutionPort {

    List<ToolExecutionResult> execute(List<ToolExecutionRequest> requests);
}
