/*
 * Customization TAO
 * Copyright (C) 2022-2025
 */
package org.example.resilience4jplayground;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
public class ExternalWsService {

    private static final Logger LOG = LogManager.getLogger(ExternalWsService.class);

    private final ThreadFactory virtualThreadFactory;

    public ExternalWsService() {
        virtualThreadFactory = Thread.ofVirtual().name("externalWS").factory();
    }

    public String call() {

        try {
            return callExternalWsFuture().get();
        } catch (ExecutionException ex) {
            LOG.error(ex);
            return "Call failed: " + ex.getMessage();
        } catch (InterruptedException ex) {
            LOG.error(ex);
            Thread.currentThread().interrupt();
            return "Call failed: " + ex.getMessage();
        }
    }

    public void resilience4J() {
        // Create a CircuitBreaker with default configuration
        CircuitBreaker circuitBreaker = CircuitBreaker
                .ofDefaults("externalWsCircuitBreaker");

// Create a Retry with default configuration
// 3 retry attempts and a fixed time interval between retries of 500ms
        Retry retry = Retry
                .ofDefaults("externalWsRetry");

// Create a Bulkhead with default configuration
        Bulkhead bulkhead = Bulkhead
                .ofDefaults("externalWsBulkhead");

        Supplier<String> supplier = this::call;

// Decorate your call to backendService.doSomething()
// with a Bulkhead, CircuitBreaker and Retry
// **note: you will need the resilience4j-all dependency for this
        Supplier<String> decoratedSupplier = Decorators.ofSupplier(supplier)
                .withCircuitBreaker(circuitBreaker)
                .withBulkhead(bulkhead)
                .withRetry(retry)
                .decorate();

// When you don't want to decorate your lambda expression,
// but just execute it and protect the call by a CircuitBreaker.
        String result = circuitBreaker
                .executeSupplier(this::call);

// You can also run the supplier asynchronously in a ThreadPoolBulkhead
        ThreadPoolBulkhead threadPoolBulkhead = ThreadPoolBulkhead
                .ofDefaults("externaleWsThreadPoolBulkhead");

// The Scheduler is needed to schedule a timeout
// on a non-blocking CompletableFuture
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);
        TimeLimiter timeLimiter = TimeLimiter.of(Duration.ofSeconds(1));

        CompletableFuture<String> future = Decorators.ofSupplier(supplier)
                .withThreadPoolBulkhead(threadPoolBulkhead)
                .withTimeLimiter(timeLimiter, scheduledExecutorService)
                .withCircuitBreaker(circuitBreaker)
                .withFallback(Arrays.asList(TimeoutException.class,
                                CallNotPermittedException.class,
                                BulkheadFullException.class),
                        throwable -> "throwable")
                .get().toCompletableFuture();
    }

    private CompletableFuture<String> callExternalWsFuture() {
        return CompletableFuture.supplyAsync(
                        this::callExternalWs,
                        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS, Executors.newThreadPerTaskExecutor(virtualThreadFactory))
                )
                .exceptionally(ex -> {
                    LOG.error(ex);
                    return "Call failed: " + ex.getMessage();
                })
                .thenApply(response -> {
                    LOG.info("callExternalWs response: {}", () -> response);
                    return response;
                });
    }

    private String callExternalWs() {
        return "Call succeeded";
    }
}
