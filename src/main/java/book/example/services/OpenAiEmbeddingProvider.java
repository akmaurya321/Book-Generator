package book.example.services;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class OpenAiEmbeddingProvider
        implements EmbeddingProvider {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiEmbeddingProvider(
            AiProperties aiProperties,
            ObjectMapper objectMapper) {

        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.openai.com")
                .build();
    }

    @Override
    public List<Float> embed(String text) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Text cannot be empty"
            );
        }

        if (aiProperties.getApiKey() == null ||
                aiProperties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API key is not configured"
            );
        }

        if (aiProperties.getEmbeddingModel() == null ||
                aiProperties.getEmbeddingModel().isBlank()) {
            throw new IllegalStateException(
                    "OpenAI embedding model is not configured"
            );
        }

        try {

            String json = """
                    {
                      "model": "%s",
                      "input": "%s"
                    }
                    """.formatted(
                    escapeJson(
                            aiProperties.getEmbeddingModel()
                    ),
                    escapeJson(text)
            );

            String response = restClient.post()
                    .uri("/v1/embeddings")
                    .header(
                            "Authorization",
                            "Bearer " + aiProperties.getApiKey()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new IllegalStateException(
                        "OpenAI returned an empty embedding response"
                );
            }

            JsonNode root =
                    objectMapper.readTree(response);

            JsonNode vector =
                    root.path("data")
                            .path(0)
                            .path("embedding");

            if (!vector.isArray()) {
                throw new IllegalStateException(
                        "Invalid OpenAI embedding response: "
                                + response
                );
            }

            List<Float> embedding =
                    new ArrayList<>();

            for (JsonNode value : vector) {
                embedding.add(
                        (float) value.asDouble()
                );
            }

            if (embedding.isEmpty()) {
                throw new IllegalStateException(
                        "OpenAI returned an empty embedding"
                );
            }

            return embedding;

        } catch (Exception e) {

            throw new RuntimeException(
                    "OpenAI embedding request failed: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private String escapeJson(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}