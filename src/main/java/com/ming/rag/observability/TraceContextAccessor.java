package com.ming.rag.observability;

import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class TraceContextAccessor {

    public static final String TRACE_ID_KEY = "traceId";

    private final Tracer tracer;

    public TraceContextAccessor(Tracer tracer) {
        this.tracer = tracer;
    }

    public String currentTraceId() {
        var currentSpan = tracer.currentSpan();
        if (currentSpan != null && currentSpan.context() != null && currentSpan.context().traceId() != null) {
            return currentSpan.context().traceId();
        }
        return MDC.get(TRACE_ID_KEY);
    }
}
