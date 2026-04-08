package com.ming.rag.infrastructure.search;

import com.ming.rag.bootstrap.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ChunkRecordIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChunkRecordIndexInitializer.class);

    private final RagProperties ragProperties;
    private final SearchBackendClient searchBackendClient;

    public ChunkRecordIndexInitializer(RagProperties ragProperties, SearchBackendClient searchBackendClient) {
        this.ragProperties = ragProperties;
        this.searchBackendClient = searchBackendClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ragProperties.storage().search().initializeIndexOnStartup()) {
            log.info("Search index initialization skipped by configuration");
            return;
        }

        if (searchBackendClient.ensureIndex()) {
            log.info(
                    "Search index initialization completed for index={} url={}",
                    ragProperties.storage().search().chunkIndex(),
                    ragProperties.storage().search().url()
            );
        }
    }
}
