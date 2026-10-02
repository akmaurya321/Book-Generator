package book.example.services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguredLlmProviderStructuredOutputTest {
    @Test
    void ollamaReceivesNativeJsonSchemaFormat() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"response\":\"{\\\"chapterId\\\":\\\"c1\\\",\\\"sections\\\":[] }\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            LlmProperties.Provider config = new LlmProperties.Provider();
            config.setName("ollama-test"); config.setProvider("ollama"); config.setModel("qwen2.5:1.5b");
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort()); config.setEnabled(true);
            new ConfiguredLlmProvider(config, new ObjectMapper(), 1000, 2000)
                    .generate("chapter", Map.of("type", "object", "properties", Map.of("chapterId", Map.of("type", "string"))));
            assertNotNull(request.get());
            assertTrue(request.get().contains("\"format\""), request.get());
            assertTrue(request.get().contains("\"chapterId\""), request.get());
            assertFalse(request.get().contains("\"response_format\""), request.get());
        } finally { server.stop(0); }
    }

    @Test
    void geminiReceivesOnlySupportedSchemaFields() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            LlmProperties.Provider config = new LlmProperties.Provider();
            config.setName("gemini-test");
            config.setProvider("gemini");
            config.setModel("gemini-test");
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta");
            config.setApiKey("test-key");
            config.setEnabled(true);

            Map<String, Object> schema = Map.of(
                    "type", "object",
                    "additionalProperties", false,
                    "minItems", 1,
                    "properties", Map.of(
                            "sections", Map.of(
                                    "type", "array",
                                    "items", Map.of(
                                            "type", "object",
                                            "minLength", 1,
                                            "properties", Map.of(
                                                    "content", Map.of("type", "string", "minLength", 1))))));
            new ConfiguredLlmProvider(config, new ObjectMapper(), 1000, 2000)
                    .generate("chapter", schema);

            assertTrue(request.get().contains("\"responseSchema\""), request.get());
            assertTrue(request.get().contains("\"content\""), request.get());
            assertFalse(request.get().contains("additionalProperties"), request.get());
            assertFalse(request.get().contains("minLength"), request.get());
            assertFalse(request.get().contains("minItems"), request.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void autoModeUsesPromptSchemaForHuggingFaceCompatibility() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            LlmProperties.Provider config = new LlmProperties.Provider();
            config.setName("hf-test");
            config.setProvider("huggingface");
            config.setModel("test-model");
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setApiKey("test-key");
            config.setEnabled(true);

            new ConfiguredLlmProvider(config, new ObjectMapper(), 1000, 2000)
                    .generate("return a chapter", Map.of("type", "object", "properties", Map.of("chapterId", Map.of("type", "string"))));

            assertFalse(request.get().contains("response_format"), request.get());
            assertTrue(request.get().contains("matching this schema"), request.get());
            assertTrue(request.get().contains("chapterId"), request.get());
        } finally {
            server.stop(0);
        }
    }
}
