package org.example.resilience4jplayground;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Controller
public class GreetingController {

    @Resource
    private ExternalWsService externalWsService;

    @GetMapping("/greeting")
    public String greeting(@RequestParam(name = "name", required = false, defaultValue = "World") String name, Model model) {
        model.addAttribute("name", name);
        model.addAttribute("externalWsResponse", externalWsService.call());

//        resilience4J();

        return "greeting";
    }

    public void resilience4J() {
        // Create a CircuitBreaker with default configuration
        CircuitBreaker circuitBreaker = CircuitBreaker
                .ofDefaults("externaleWsCircuitBreaker");

// Create a Retry with default configuration
// 3 retry attempts and a fixed time interval between retries of 500ms
        Retry retry = Retry
                .ofDefaults("externaleWsRetry");

// Create a Bulkhead with default configuration
        Bulkhead bulkhead = Bulkhead
                .ofDefaults("externaleWsBulkhead");

        Supplier<String> supplier = () -> externalWsService.call();

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
                .executeSupplier(externalWsService::call);

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
}