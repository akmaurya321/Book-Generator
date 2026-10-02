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
public class HuggingFaceEmbeddingProvider
        implements EmbeddingProvider {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public HuggingFaceEmbeddingProvider(
            AiProperties aiProperties,
            ObjectMapper objectMapper) {

        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(
                        "https://router.huggingface.co"
                )
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
                    "Hugging Face API key is not configured"
            );
        }

        if (aiProperties.getEmbeddingModel() == null ||
                aiProperties.getEmbeddingModel().isBlank()) {

            throw new IllegalStateException(
                    "Hugging Face embedding model is not configured"
            );
        }

        try {

            String model =
                    aiProperties.getEmbeddingModel();

            String json = """
                    {
                      "inputs": "%s"
                    }
                    """.formatted(
                    escapeJson(text)
            );

            String response =
                    restClient.post()
                            .uri(
                                    "/hf-inference/models/"
                                            + model
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " +
                                            aiProperties.getApiKey()
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(json)
                            .retrieve()
                            .body(String.class);

            if (response == null ||
                    response.isBlank()) {

                throw new IllegalStateException(
                        "Hugging Face returned an empty embedding response"
                );
            }

            JsonNode root =
                    objectMapper.readTree(response);

            return extractEmbedding(root);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Hugging Face embedding request failed: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private List<Float> extractEmbedding(
            JsonNode root) {

        List<Float> embedding =
                new ArrayList<>();

        JsonNode vector = root;

        /*
         * Feature extraction normally returns
         * an array of floating-point values.
         */
        if (vector.isArray() &&
                vector.size() > 0 &&
                vector.get(0).isArray()) {

            vector = vector.get(0);
        }

        if (!vector.isArray()) {

            throw new IllegalStateException(
                    "Invalid Hugging Face embedding response: "
                            + root
            );
        }

        for (JsonNode value : vector) {
            if (value.isNumber()) {
                embedding.add(
                        (float) value.asDouble()
                );
            }
        }

        if (embedding.isEmpty()) {

            throw new IllegalStateException(
                    "Embedding vector is empty"
            );
        }

        return embedding;
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