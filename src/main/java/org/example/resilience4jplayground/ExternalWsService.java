/*
 * Customization TAO
 * Copyright (C) 2022-2025
 */
package org.example.resilience4jplayground;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Component
public class ExternalWsService {

    private static final Logger LOG = LogManager.getLogger(ExternalWsService.class);

    private final ThreadFactory virtualThreadFactory;

    public ExternalWsService() {
        virtualThreadFactory = Thread.ofVirtual().name("externalWS").factory();
    }

    public String call() {

        try {
            return CompletableFuture.supplyAsync(
                            this::callExternalWs,
                            CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS, Executors.newThreadPerTaskExecutor(virtualThreadFactory))
                    )
                    .exceptionally((ex) -> {
                        LOG.error(ex);
                        return "Call failed: " + ex.getMessage();
                    })
                    .get();
        } catch (ExecutionException | InterruptedException ex) {
            LOG.error(ex);
            return "Call failed: " + ex.getMessage();
        }
    }

    private String callExternalWs() {
        LOG.info("callExternalWs");
        return "Call succeeded";
    }
}
