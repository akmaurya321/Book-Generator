package book.example.services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguredLlmProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void openAiCompatiblePreservesBasePathAndAuthorization() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            LlmProperties.Provider config = provider("openai", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "gpt-test", "secret");
            assertEquals("ok", new ConfiguredLlmProvider(config, objectMapper, 1000, 2000).generate("hello"));
            assertEquals("Bearer secret", auth.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void geminiUsesGoogleApiKeyHeaderAndV1BetaBasePath() throws Exception {
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"gemini ok\"}]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            LlmProperties.Provider config = provider("gemini", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta", "gemini-test", "gem-key");
            assertEquals("gemini ok", new ConfiguredLlmProvider(config, objectMapper, 1000, 2000).generate("hello"));
            assertEquals("gem-key", apiKey.get());
            assertNull(authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ollamaUsesGenerateEndpointWithoutAuthentication() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"response\":\"ollama ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            LlmProperties.Provider config = provider("ollama", "http://127.0.0.1:" + server.getAddress().getPort(), "qwen3:8b", "");
            assertEquals("ollama ok", new ConfiguredLlmProvider(config, objectMapper, 1000, 2000).generate("hello"));
            assertNull(auth.get());
        } finally {
            server.stop(0);
        }
    }

    private LlmProperties.Provider provider(String type, String baseUrl, String model, String apiKey) {
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setName("test-" + type);
        provider.setProvider(type);
        provider.setModel(model);
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(apiKey);
        provider.setEnabled(true);
        return provider;
    }
}
