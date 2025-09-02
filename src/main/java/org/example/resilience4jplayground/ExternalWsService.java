/*
 * Customization TAO
 * Copyright (C) 2022-2025
 */
package org.example.resilience4jplayground;

import org.springframework.stereotype.Component;

@Component
public class ExternalWsService {

    Thread.Builder virtualThreadBuilder;

    public ExternalWsService() {
        virtualThreadBuilder = Thread.ofVirtual().name("externalWS");
    }

    public String call() {
//        Executors.newVirtualThreadPerTaskExecutor()


//        CompletableFuture<String> future = CompletableFuture.supplyAsync()
        return "Call succeeded";
    }
}
