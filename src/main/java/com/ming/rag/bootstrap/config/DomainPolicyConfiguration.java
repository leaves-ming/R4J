package com.ming.rag.bootstrap.config;

import com.ming.rag.application.ingestion.IngestionStateMachine;
import com.ming.rag.domain.common.ChunkIdPolicy;
import com.ming.rag.domain.common.DocumentIdPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainPolicyConfiguration {

    @Bean
    public DocumentIdPolicy documentIdPolicy() {
        return new DocumentIdPolicy();
    }

    @Bean
    public ChunkIdPolicy chunkIdPolicy() {
        return new ChunkIdPolicy();
    }

    @Bean
    public IngestionStateMachine ingestionStateMachine() {
        return new IngestionStateMachine();
    }
}
