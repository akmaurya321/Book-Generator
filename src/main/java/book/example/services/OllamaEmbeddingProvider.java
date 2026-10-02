package book.example.services;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private final AiProperties aiProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OllamaEmbeddingProvider(
            AiProperties aiProperties,
            ObjectMapper objectMapper) {

        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;

        this.restClient = RestClient.builder()
                .baseUrl(aiProperties.getEmbeddingBaseUrl())
                .build();
    }

    @Override
    public List<Float> embed(String text) {

        try {

            String response = restClient.post()
                    .uri("/api/embed")
                    .body("""
                            {
                              "model": "%s",
                              "input": "%s"
                            }
                            """.formatted(
                            aiProperties.getEmbeddingModel(),
                            escapeJson(text)
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);

            JsonNode embeddingNode = root
                    .path("embeddings")
                    .path(0);

            if (!embeddingNode.isArray()) {
                throw new IllegalStateException(
                        "Ollama did not return a valid embedding"
                );
            }

            List<Float> embedding = new ArrayList<>();

            for (JsonNode value : embeddingNode) {
                embedding.add((float) value.asDouble());
            }

            return embedding;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to generate Ollama embedding: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private String escapeJson(String text) {

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}