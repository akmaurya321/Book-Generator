package book.example.services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LlmWorkerPoolTest {

    @Test
    void failedProviderIsolatedAndHealthyProviderCanCompleteRetry() throws Exception {
        HttpServer failing = server(exchange -> {
            byte[] body = "{\"error\":\"primary unavailable\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        HttpServer healthy = server(exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"healthy response\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        try {
            LlmProperties properties = properties(failing, healthy, 3, 1000);
            LlmWorkerPool pool = new LlmWorkerPool(properties, new AiProperties(), new ObjectMapper());
            String result = pool.generateWithRetry("hello");
            assertEquals("healthy response", result);
            assertTrue(pool.providerStates().stream().anyMatch(s -> s.startsWith("primary=UNAVAILABLE")));
            assertTrue(pool.providerStates().stream().anyMatch(s -> s.startsWith("secondary=AVAILABLE")));
            pool.shutdown();
        } finally {
            failing.stop(0);
            healthy.stop(0);
        }
    }


    @Test
    void transientRetryStaysOnSameWorkerBeforeFailingOver() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                byte[] body = "{\"error\":\"temporary\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
                return;
            }
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"recovered\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        try {
            LlmProperties properties = new LlmProperties();
            properties.setMaxProviderAttempts(2);
            properties.setRetryBackoffMs(0);
            properties.setProviderCooldownMs(1000);
            properties.setConnectionTimeoutMs(1000);
            properties.setReadTimeoutMs(2000);
            properties.setProviders(List.of(provider("sticky", server)));
            LlmWorkerPool pool = new LlmWorkerPool(properties, new AiProperties(), new ObjectMapper());
            assertEquals("recovered", pool.generateWithRetry("hello"));
            assertEquals(2, calls.get());
            pool.shutdown();
        } finally { server.stop(0); }
    }
    @Test
    void providerConcurrencyIsOnePerConfiguredWorker() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(2);
        HttpServer server = server(exchange -> {
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            entered.countDown();
            try {
                Thread.sleep(120);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
            active.decrementAndGet();
        });
        try {
            LlmProperties properties = concurrencyProperties(server);
            // Same endpoint is intentionally configured twice as two independent workers.
            LlmWorkerPool pool = new LlmWorkerPool(properties, new AiProperties(), new ObjectMapper());
            var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
            executor.submit(() -> pool.generate("one"));
            executor.submit(() -> pool.generate("two"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(2, maxActive.get());
            executor.shutdownNow();
            pool.shutdown();
        } finally {
            server.stop(0);
        }
    }


    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void configuredWorkerCountBoundsTrueProviderConcurrency(int workerCount) throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(workerCount);
        HttpServer server = server(exchange -> {
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            entered.countDown();
            try {
                Thread.sleep(150);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
            active.decrementAndGet();
        });
        var executor = java.util.concurrent.Executors.newFixedThreadPool(workerCount);
        LlmWorkerPool pool = null;
        try {
            LlmProperties properties = new LlmProperties();
            properties.setMaxProviderAttempts(1);
            properties.setProviderCooldownMs(100);
            properties.setConnectionTimeoutMs(1000);
            properties.setReadTimeoutMs(2000);
            List<LlmProperties.Provider> providers = new java.util.ArrayList<>();
            for (int i = 0; i < workerCount; i++) providers.add(provider("worker-" + i, server));
            properties.setProviders(providers);
            pool = new LlmWorkerPool(properties, new AiProperties(), new ObjectMapper());
            final LlmWorkerPool testPool = pool;
            List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < workerCount; i++) {
                futures.add(executor.submit(() -> testPool.generate("parallel")));
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS), "All configured workers should receive a request concurrently");
            for (var future : futures) assertEquals("ok", future.get(3, TimeUnit.SECONDS));
            assertEquals(workerCount, maxActive.get());
        } finally {
            executor.shutdownNow();
            if (pool != null) pool.shutdown();
            server.stop(0);
        }
    }

    @Test
    void transientFailuresCanExhaustTwoWorkersAndFailOverToThirdHealthyWorker() throws Exception {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        HttpServer first = server(exchange -> respond503(exchange, firstCalls.incrementAndGet()));
        HttpServer second = server(exchange -> respond503(exchange, secondCalls.incrementAndGet()));
        HttpServer third = server(exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"third-worker\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        try {
            LlmProperties properties = new LlmProperties();
            properties.setMaxProviderAttempts(5);
            properties.setRetryBackoffMs(0);
            properties.setProviderCooldownMs(10_000);
            properties.setConnectionTimeoutMs(1000);
            properties.setReadTimeoutMs(2000);
            properties.setProviders(List.of(provider("first", first), provider("second", second), provider("third", third)));
            LlmWorkerPool pool = new LlmWorkerPool(properties, new AiProperties(), new ObjectMapper());
            assertEquals("third-worker", pool.generateWithRetry("failover"));
            assertEquals(2, firstCalls.get());
            assertEquals(2, secondCalls.get());
            pool.shutdown();
        } finally { first.stop(0); second.stop(0); third.stop(0); }
    }

    @Test
    void permanentProviderErrorIsNotRetried() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"error\":\"bad request\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        try {
            LlmProperties properties = new LlmProperties();
            properties.setMaxProviderAttempts(5);
            properties.setRetryBackoffMs(0);
            properties.setProviderCooldownMs(100);
            properties.setConnectionTimeoutMs(1000);
            properties.setReadTimeoutMs(2000);
            properties.setProviders(List.of(provider("bad-request", server)));
            LlmWorkerPool pool = new LlmWorkerPool(properties, new AiProperties(), new ObjectMapper());
            assertThrows(LlmProviderException.class, () -> pool.generateWithRetry("permanent"));
            assertEquals(1, calls.get());
            pool.shutdown();
        } finally { server.stop(0); }
    }

    @Test
    void emptyModelResponseIsReturnedForSectionValidationWithoutProviderRetries() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        try {
            LlmProperties properties = new LlmProperties();
            properties.setMaxProviderAttempts(5);
            properties.setRetryBackoffMs(0);
            properties.setProviders(List.of(provider("empty-output", server)));
            LlmWorkerPool pool = new LlmWorkerPool(properties, new AiProperties(), new ObjectMapper());

            assertEquals("", pool.generateWithRetry("produce structured output", Map.of("type", "object")));
            assertEquals(1, calls.get());
            pool.shutdown();
        } finally {
            server.stop(0);
        }
    }

    private void respond503(com.sun.net.httpserver.HttpExchange exchange, int call) throws java.io.IOException {
        byte[] body = ("{\"error\":\"temporary-" + call + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(503, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
    }

    private LlmProperties properties(HttpServer primary, HttpServer secondary, int attempts, long cooldown) {
        LlmProperties properties = new LlmProperties();
        properties.setMaxProviderAttempts(attempts);
        properties.setProviderCooldownMs(cooldown);
        properties.setRetryBackoffMs(0);
        properties.setConnectionTimeoutMs(1000);
        properties.setReadTimeoutMs(2000);
        properties.setProviders(List.of(provider("primary", primary), provider("secondary", secondary)));
        return properties;
    }

    private LlmProperties concurrencyProperties(HttpServer server) {
        LlmProperties properties = new LlmProperties();
        properties.setMaxProviderAttempts(1);
        properties.setProviderCooldownMs(100);
        properties.setConnectionTimeoutMs(1000);
        properties.setReadTimeoutMs(2000);
        properties.setProviders(List.of(provider("worker-a", server), provider("worker-b", server)));
        return properties;
    }

    private LlmProperties.Provider provider(String name, HttpServer server) {
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setName(name);
        provider.setProvider("openai-compatible");
        provider.setModel("test-model");
        provider.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        provider.setApiKey("test-key");
        provider.setEnabled(true);
        return provider;
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", handler);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        return server;
    }
}
