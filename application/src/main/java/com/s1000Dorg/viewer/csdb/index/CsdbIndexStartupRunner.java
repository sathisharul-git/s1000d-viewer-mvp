package com.s1000Dorg.viewer.csdb.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CsdbIndexStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(CsdbIndexStartupRunner.class);

    private final CsdbIndexer indexer;
    private final boolean indexOnStartup;

    public CsdbIndexStartupRunner(
        CsdbIndexer indexer,
        @Value("${viewer.indexer.index-on-startup:true}") boolean indexOnStartup
    ) {
        this.indexer = indexer;
        this.indexOnStartup = indexOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!indexOnStartup) {
            log.info("CSDB startup indexing disabled.");
            return;
        }
        log.info("Starting CSDB metadata indexing.");
        indexer.indexAll();
    }
}
