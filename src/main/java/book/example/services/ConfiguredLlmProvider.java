package book.example.services;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ConfiguredLlmProvider {
    private final LlmProperties.Provider config;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public ConfiguredLlmProvider(LlmProperties.Provider config, ObjectMapper objectMapper,
            int connectionTimeoutMs, int readTimeoutMs) {
        this.config = config;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectionTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.client = RestClient.builder().requestFactory(factory).baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .build();
    }

    public String getName() {
        return config.getName();
    }

    public String generate(String prompt) {
        return generate(prompt, null);
    }

    public String generate(String prompt, Map<String, Object> responseSchema) {
        if (prompt == null || prompt.isBlank())
            throw new IllegalArgumentException("Prompt cannot be empty");
        if (responseSchema != null && !config.useNativeStructuredOutput()) {
            prompt = prompt + "\n\nReturn JSON matching this schema exactly. The backend validates the response:\n"
                    + serializeSchema(responseSchema);
            responseSchema = null;
        }
        String type = config.getProvider() == null ? "" : config.getProvider().toLowerCase();
        if (type.equals("ollama"))
            return generateOllama(prompt, responseSchema);
        if (type.equals("gemini") || type.equals("google") || type.equals("google-gemini")) {
            return generateGemini(prompt, responseSchema);
        }
        if (type.equals("openai") || type.equals("huggingface") || type.equals("hf")
                || type.equals("openai-compatible")) {
            return generateOpenAiCompatible(prompt, responseSchema);
        }
        throw new IllegalArgumentException("Unsupported LLM provider type: " + config.getProvider());
    }

    private String generateOllama(String prompt, Map<String, Object> responseSchema) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", config.getModel());
        request.put("prompt", prompt);
        request.put("stream", false);
        request.put("think", false);
        request.put("options", Map.of("num_predict", 6000));
        if (responseSchema != null)
            request.put("format", responseSchema);
        String body = post("/api/generate", request);
        JsonNode root = read(body);
        if (root.path("error").isTextual())
            throw new IllegalStateException(root.path("error").asText());
        return root.path("response").asText("");
    }

    private String generateGemini(String prompt, Map<String, Object> responseSchema) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Gemini API key is required for " + config.getName());
        }

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", java.util.List.of(part));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", 6000);
        if (responseSchema != null) {
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("responseSchema", geminiSchema(responseSchema));
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contents", java.util.List.of(content));
        request.put("generationConfig", generationConfig);

        String model = config.getModel();
        String path = "models/" + model + ":generateContent";
        String body = post(path, request, "x-goog-api-key", config.getApiKey());
        JsonNode root = read(body);

        if (root.path("error").isObject()) {
            throw new IllegalStateException(root.path("error").toString());
        }

        StringBuilder textBuilder = new StringBuilder();
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (parts.isArray()) {
            for (JsonNode partNode : parts) {
                String partText = partNode.path("text").asText("");
                if (!partText.isBlank()) {
                    if (textBuilder.length() > 0)
                        textBuilder.append("\n");
                    textBuilder.append(partText);
                }
            }
        }
        return textBuilder.toString();
    }

    private String generateOpenAiCompatible(String prompt, Map<String, Object> responseSchema) {
        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", config.getModel());
        request.put("messages", java.util.List.of(message));
        request.put("temperature", 0.2);
        request.put("stream", false);
        if (responseSchema != null) {
            request.put("response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of("name", "chapter_response", "strict", true, "schema", responseSchema)));
        }
        String path = config.getBaseUrl() != null && config.getBaseUrl().matches(".*?/v1/?$")
                ? "chat/completions"
                : "v1/chat/completions";
        String body = post(path, request);
        JsonNode root = read(body);
        if (root.path("error").isObject())
            throw new IllegalStateException(root.path("error").toString());
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private String post(String path, Object request) {
        return post(path, request, null, null);
    }

    private String post(String path, Object request, String extraHeaderName, String extraHeaderValue) {
        try {
            String relativePath = path == null ? "" : path.replaceFirst("^/+", "");
            var builder = client.post().uri(endpoint(relativePath)).contentType(MediaType.APPLICATION_JSON);
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                String provider = config.getProvider() == null ? "" : config.getProvider().trim().toLowerCase();
                if (!provider.equals("gemini") && !provider.equals("google") && !provider.equals("google-gemini")) {
                    builder.header("Authorization", "Bearer " + config.getApiKey());
                }
            }
            if (extraHeaderName != null && extraHeaderValue != null && !extraHeaderValue.isBlank()) {
                builder.header(extraHeaderName, extraHeaderValue);
            }
            return builder.body(objectMapper.writeValueAsString(request)).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean transientFailure = status == 408 || status == 425 || status == 429 || status >= 500;
            throw new LlmProviderException("Provider " + config.getName() + " request failed with HTTP " + status + ": "
                    + e.getResponseBodyAsString(), status, transientFailure, e);
        } catch (Exception e) {
            throw new LlmProviderException("Provider " + config.getName() + " request failed: " + e.getMessage(), null,
                    true, e);
        }
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid LLM response from " + config.getName(), e);
        }
    }

    private String serializeSchema(Map<String, Object> schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize the structured response schema.", e);
        }
    }

    private Map<String, Object> geminiSchema(Map<String, Object> schema) {
        Set<String> supportedFields = Set.of(
                "type", "properties", "required", "items", "enum", "format", "description", "nullable");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            if (!supportedFields.contains(entry.getKey())) continue;
            Object value = entry.getValue();
            if ("properties".equals(entry.getKey()) && value instanceof Map<?, ?> properties) {
                Map<String, Object> convertedProperties = new LinkedHashMap<>();
                properties.forEach((name, propertySchema) -> {
                    if (name instanceof String propertyName && propertySchema instanceof Map<?, ?> childSchema) {
                        Map<String, Object> child = new LinkedHashMap<>();
                        childSchema.forEach((key, childValue) -> {
                            if (key instanceof String childKey) {
                                child.put(childKey, childValue);
                            }
                        });
                        convertedProperties.put(propertyName, geminiSchema(child));
                    }
                });
                result.put(entry.getKey(), convertedProperties);
            } else if ("items".equals(entry.getKey()) && value instanceof Map<?, ?> itemSchema) {
                Map<String, Object> child = new LinkedHashMap<>();
                itemSchema.forEach((key, childValue) -> {
                    if (key instanceof String childKey) {
                        child.put(childKey, childValue);
                    }
                });
                result.put(entry.getKey(), geminiSchema(child));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private String endpoint(String path) {
        String baseUrl = normalizeBaseUrl(config.getBaseUrl());
        String relativePath = path == null ? "" : path.replaceFirst("^/+", "");
        return baseUrl + "/" + relativePath;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank())
            throw new IllegalArgumentException("LLM base-url is required for " + config.getName());
        return baseUrl.replaceAll("/+$", "");
    }
}
