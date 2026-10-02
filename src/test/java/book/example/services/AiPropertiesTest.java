package book.example.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiPropertiesTest {

    @Test
    void selectsProviderSpecificModelDefaults() {
        AiProperties properties = new AiProperties();

        properties.setProvider("openai");
        assertEquals("gpt-4.1-mini", properties.getChatModel());
        assertEquals("text-embedding-3-small", properties.getEmbeddingModel());

        properties.setProvider("huggingface");
        assertEquals("Qwen/Qwen3-4B-Instruct-2507", properties.getChatModel());
        assertEquals("BAAI/bge-small-en-v1.5", properties.getEmbeddingModel());

        properties.setProvider("ollama");
        assertEquals("qwen3:8b", properties.getChatModel());
        assertEquals("bge-m3", properties.getEmbeddingModel());
    }

    @Test
    void explicitlyConfiguredModelsOverrideProviderDefaults() {
        AiProperties properties = new AiProperties();
        properties.setProvider("openai");
        properties.setChatModel("custom-chat-model");
        properties.setEmbeddingModel("custom-embedding-model");

        assertEquals("custom-chat-model", properties.getChatModel());
        assertEquals("custom-embedding-model", properties.getEmbeddingModel());
    }

    @Test
    void embeddingEndpointCanDifferFromChatEndpoint() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("https://ollama-tunnel.example");
        properties.setEmbeddingBaseUrl("http://localhost:11434");

        assertEquals("http://localhost:11434", properties.getEmbeddingBaseUrl());
    }

    @Test
    void embeddingEndpointDefaultsToChatEndpoint() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://localhost:11434");

        assertEquals("http://localhost:11434", properties.getEmbeddingBaseUrl());
    }
}
