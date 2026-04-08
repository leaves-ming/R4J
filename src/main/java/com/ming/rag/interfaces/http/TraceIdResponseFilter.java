package com.ming.rag.interfaces.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TraceIdResponseFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(request, response);

        var traceId = request.getAttribute(TraceContextFilter.REQUEST_TRACE_ID);
        if (traceId instanceof String value && !value.isBlank() && !response.containsHeader(TraceContextFilter.TRACE_HEADER)) {
            response.setHeader(TraceContextFilter.TRACE_HEADER, value);
        }
    }
}
