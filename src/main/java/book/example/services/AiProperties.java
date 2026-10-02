package book.example.services;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String provider;
    private String apiKey;
    private String chatModel;
    private String embeddingModel;
    private String embeddingBaseUrl;
    private String baseUrl;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChatModel() {
        if (chatModel != null && !chatModel.isBlank()) {
            return chatModel;
        }

        return switch (normalizedProvider()) {
            case "openai" -> "gpt-4.1-mini";
            case "huggingface", "hf" -> "Qwen/Qwen3-4B-Instruct-2507";
            case "gemini" -> "gemini-3-flash-preview";
            default -> "qwen3:8b";
        };
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getEmbeddingModel() {
        if (embeddingModel != null && !embeddingModel.isBlank()) {
            return embeddingModel;
        }

        return switch (normalizedProvider()) {
            case "openai" -> "text-embedding-3-small";
            case "huggingface", "hf" -> "BAAI/bge-small-en-v1.5";
            case "gemini" -> "gemini-embedding-001";
            default -> "bge-m3";
        };
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getEmbeddingBaseUrl() {
        if (embeddingBaseUrl != null && !embeddingBaseUrl.isBlank()) {
            return embeddingBaseUrl;
        }
        return baseUrl;
    }

    public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
        this.embeddingBaseUrl = embeddingBaseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private String normalizedProvider() {
        return provider == null ? "" : provider.toLowerCase();
    }
}