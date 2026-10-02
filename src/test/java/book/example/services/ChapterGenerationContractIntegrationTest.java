package book.example.services;

import book.example.dto.ChapterSectionOutput;
import book.example.dto.DocumentationSection;
import book.example.dto.RagSearchResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract-level integration test: HTTP LLM worker -> structured response -> backend validator -> targeted repair response.
 */
class ChapterGenerationContractIntegrationTest {
    @Test
    void invalidSectionIsPreservedAndOnlyFailedSectionIsRepaired() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            int call = calls.incrementAndGet();
            String response = call == 1
                    ? "{\"chapterId\":\"chapter-1\",\"sections\":[{\"sectionId\":\"a\",\"content\":\"Java service [EVIDENCE: E-aaaaaa]\",\"evidenceIds\":[\"E-aaaaaa\"],\"assets\":[]}]}"
                    : "{\"chapterId\":\"chapter-1\",\"sections\":[{\"sectionId\":\"b\",\"content\":\"PostgreSQL persistence [EVIDENCE: E-bbbbbb]\",\"evidenceIds\":[\"E-bbbbbb\"],\"assets\":[]}]}";
            String body = null;
            try {
                body = "{\"choices\":[{\"message\":{\"content\":" + quote(response) + "}}]}";
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            LlmProperties props = new LlmProperties();
            props.setMaxProviderAttempts(1);
            props.setRetryBackoffMs(0);
            props.setProviderCooldownMs(1000);
            props.setProviders(List.of(provider(server)));
            LlmWorkerPool pool = new LlmWorkerPool(props, new AiProperties(), new ObjectMapper());

            DocumentationSection a = section("a");
            DocumentationSection b = section("b");
            Map<String, RagSearchResult> evidence = Map.of(
                    "E-aaaaaa", new RagSearchResult("Java service", "A.java", "JAVA", 1, 2, 1),
                    "E-bbbbbb", new RagSearchResult("PostgreSQL persistence", "B.java", "JAVA", 1, 2, 1));

            String first = pool.generateWithRetry("initial", Map.of("type", "object"));
            var firstValidation = ChapterResponseValidator.validate(first, "chapter-1", List.of(a, b), evidence, new ObjectMapper());
            assertTrue(firstValidation.validSections().containsKey("a"));
            assertTrue(firstValidation.failures().containsKey("b"));

            String repair = pool.generateWithRetry("repair b only", Map.of("type", "object"));
            var repairValidation = ChapterResponseValidator.validate(repair, "chapter-1", List.of(b), evidence, new ObjectMapper());
            assertTrue(repairValidation.failures().isEmpty());
            assertEquals("PostgreSQL persistence [EVIDENCE: E-bbbbbb]", repairValidation.validSections().get("b").getContent());
            assertEquals(2, calls.get());
            pool.shutdown();
        } finally { server.stop(0); }
    }

    private DocumentationSection section(String id) {
        DocumentationSection s = new DocumentationSection();
        s.setId(id); s.setTitle(id); s.setContentPurpose("test");
        return s;
    }

    private LlmProperties.Provider provider(HttpServer server) {
        LlmProperties.Provider p = new LlmProperties.Provider();
        p.setName("integration-worker"); p.setProvider("openai-compatible"); p.setModel("test-model");
        p.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort()); p.setApiKey("test-key"); p.setEnabled(true);
        return p;
    }

    private String quote(String value) throws Exception {
        return new ObjectMapper().writeValueAsString(value);
    }
}
