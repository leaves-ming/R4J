package com.ming.rag.bootstrap.config;

import com.ming.rag.application.ingestion.IngestionStateMachine;
import com.ming.rag.domain.common.ChunkIdPolicy;
import com.ming.rag.domain.common.DocumentIdPolicy;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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

    @Bean
    public ApplicationRunner runtimeDependencyPolicyVerifier(
            RagProperties ragProperties,
            Environment environment,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<Flyway> flywayProvider
    ) {
        return args -> {
            if (isTestProfile(environment)) {
                return;
            }

            if (!ragProperties.storage().metadata().required()) {
                throw new IllegalStateException("Production/default runtime must keep rag.storage.metadata.required=true");
            }
            if (!ragProperties.storage().search().required()) {
                throw new IllegalStateException("Production/default runtime must keep rag.storage.search.required=true");
            }
            if (ragProperties.storage().search().devFallbackEnabled()) {
                throw new IllegalStateException("Search dev fallback is only allowed in test or explicit dev-fallback profile");
            }
            if (dataSourceProvider.getIfAvailable() == null) {
                throw new IllegalStateException("DataSource bean is required for non-test runtime");
            }
            if (flywayProvider.getIfAvailable() == null) {
                throw new IllegalStateException("Flyway bean is required for non-test runtime");
            }
        };
    }

    private boolean isTestProfile(Environment environment) {
        return environment.matchesProfiles("test") || environment.matchesProfiles("dev-fallback");
    }
}
