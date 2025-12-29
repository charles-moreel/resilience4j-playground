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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
@State(Scope.Benchmark)
public class ExternalWsService {

    private static final Logger LOG = LogManager.getLogger(ExternalWsService.class);

    private final ThreadFactory virtualThreadFactory;

    public ExternalWsService() {
        virtualThreadFactory = Thread.ofVirtual().name("externalWS").factory();
    }

    public String call() {
        return simpleCallExternalWsFuture();
//        return resilience4jCallExternalWsFuture();
    }

    @Benchmark
    @Threads(value = 2)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 0)
    @Fork(value = 1)
    public String simpleCallExternalWsFuture() {

        try {
            return CompletableFuture.supplyAsync(this::callExternalWs)
                    .exceptionally(ex -> {
                        LOG.error(ex);
                        return "Call failed: " + ex.getMessage();
                    })
                    .thenApply(response -> {
                        LOG.info("callExternalWs response: {}", () -> response);
                        return response;
                    })
                    .get();
        } catch (final ExecutionException ex) {
            LOG.error(ex);
            return "Call failed: " + ex.getMessage();
        } catch (final InterruptedException ex) {
            LOG.error(ex);
            Thread.currentThread().interrupt();
            return "Call failed: " + ex.getMessage();
        }
    }

    //    @Benchmark
//    @Threads(value = 2)
//    @OutputTimeUnit(TimeUnit.MILLISECONDS)
//    @BenchmarkMode(Mode.AverageTime)
//    @Warmup(iterations = 0)
//    @Fork(value = 1)
    public String resilience4jCallExternalWsFuture() {

        try {
            return CompletableFuture.supplyAsync(this::callExternalWs)
                    .exceptionally(ex -> {
                        LOG.error(ex);
                        return "Call failed: " + ex.getMessage();
                    })
                    .thenApply(response -> {
                        LOG.info("callExternalWs response: {}", () -> response);
                        return response;
                    })
                    .get();
        } catch (final ExecutionException ex) {
            LOG.error(ex);
            return "Call failed: " + ex.getMessage();
        } catch (final InterruptedException ex) {
            LOG.error(ex);
            Thread.currentThread().interrupt();
            return "Call failed: " + ex.getMessage();
        }
    }


    public void resilience4J() {
        // Create a CircuitBreaker with default configuration
        final CircuitBreaker circuitBreaker = CircuitBreaker
                .ofDefaults("externalWsCircuitBreaker");

// Create a Retry with default configuration
// 3 retry attempts and a fixed time interval between retries of 500ms
        final Retry retry = Retry
                .ofDefaults("externalWsRetry");

// Create a Bulkhead with default configuration
        final Bulkhead bulkhead = Bulkhead
                .ofDefaults("externalWsBulkhead");

        final Supplier<String> supplier = this::call;

// Decorate your call to backendService.doSomething()
// with a Bulkhead, CircuitBreaker and Retry
// **note: you will need the resilience4j-all dependency for this
        final Supplier<String> decoratedSupplier = Decorators.ofSupplier(supplier)
                .withCircuitBreaker(circuitBreaker)
                .withBulkhead(bulkhead)
                .withRetry(retry)
                .decorate();

// When you don't want to decorate your lambda expression,
// but just execute it and protect the call by a CircuitBreaker.
        final String result = circuitBreaker
                .executeSupplier(this::call);

// You can also run the supplier asynchronously in a ThreadPoolBulkhead
        final ThreadPoolBulkhead threadPoolBulkhead = ThreadPoolBulkhead
                .ofDefaults("externaleWsThreadPoolBulkhead");

// The Scheduler is needed to schedule a timeout
// on a non-blocking CompletableFuture
        final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);
        final TimeLimiter timeLimiter = TimeLimiter.of(Duration.ofSeconds(1));

        final CompletableFuture<String> future = Decorators.ofSupplier(supplier)
                .withThreadPoolBulkhead(threadPoolBulkhead)
                .withTimeLimiter(timeLimiter, scheduledExecutorService)
                .withCircuitBreaker(circuitBreaker)
                .withFallback(Arrays.asList(TimeoutException.class,
                                CallNotPermittedException.class,
                                BulkheadFullException.class),
                        throwable -> "throwable")
                .get()
                .toCompletableFuture();
    }

    private String callExternalWs() {
        try {
            return CompletableFuture.supplyAsync(
                            () -> "Call succeeded",
                            CompletableFuture.delayedExecutor(ThreadLocalRandom.current().nextLong(20, 120), TimeUnit.MILLISECONDS, Executors.newThreadPerTaskExecutor(virtualThreadFactory))
                    )
                    .get();
        } catch (final ExecutionException ex) {
            LOG.error(ex);
            return "Call failed: " + ex.getMessage();
        } catch (final InterruptedException ex) {
            LOG.error(ex);
            Thread.currentThread().interrupt();
            return "Call failed: " + ex.getMessage();
        }
    }
}
