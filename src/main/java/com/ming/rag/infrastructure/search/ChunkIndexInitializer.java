package com.ming.rag.infrastructure.search;

import com.ming.rag.bootstrap.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ChunkIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChunkIndexInitializer.class);

    private final RagProperties ragProperties;

    public ChunkIndexInitializer(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ragProperties.storage().search().initializeIndexOnStartup()) {
            log.info("Search index initialization skipped by configuration");
            return;
        }

        log.info(
                "Search index initialization requested for index={} url={}",
                ragProperties.storage().search().chunkIndex(),
                ragProperties.storage().search().url()
        );
    }
}
