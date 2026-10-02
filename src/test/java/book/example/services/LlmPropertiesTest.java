package book.example.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmPropertiesTest {

    @Test
    void emptyProviderSlotsAreIgnoredAutomatically() {
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setProvider("");
        provider.setModel("");
        provider.setBaseUrl("");
        provider.setEnabled(true);

        assertFalse(provider.isConfigured());
    }

    @Test
    void configuredProviderSlotIsDetected() {
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setProvider("ollama");
        provider.setModel("qwen3:8b");
        provider.setBaseUrl("http://localhost:11434");
        provider.setEnabled(true);

        assertTrue(provider.isConfigured());
    }

    @Test
    void unsupportedConfiguredProviderFailsValidation() {
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setName("bad");
        provider.setProvider("unknown");
        provider.setModel("model");
        provider.setBaseUrl("http://localhost");
        assertThrows(IllegalStateException.class, provider::validate);
    }

    @Test
    void structuredOutputModeSelectsNativeCapabilityOrPromptFallback() {
        LlmProperties.Provider openAi = provider("openai");
        assertTrue(openAi.useNativeStructuredOutput());

        LlmProperties.Provider huggingFace = provider("huggingface");
        assertFalse(huggingFace.useNativeStructuredOutput());

        huggingFace.setStructuredOutput("native");
        assertTrue(huggingFace.useNativeStructuredOutput());
        huggingFace.setStructuredOutput("invalid");
        assertThrows(IllegalStateException.class, huggingFace::validate);
    }

    private LlmProperties.Provider provider(String type) {
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setName(type);
        provider.setProvider(type);
        provider.setModel("test-model");
        provider.setBaseUrl("http://localhost");
        provider.setApiKey("test-key");
        provider.setEnabled(true);
        return provider;
    }

    @Test
    void cloudProviderWithoutApiKeyFailsValidation() {
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setName("openai");
        provider.setProvider("openai");
        provider.setModel("gpt-test");
        provider.setBaseUrl("https://api.openai.com/v1");
        assertThrows(IllegalStateException.class, provider::validate);
    }

    @Test
    void disabledConfiguredProviderIsIgnored() {
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setProvider("openai");
        provider.setModel("gpt-4.1-mini");
        provider.setBaseUrl("https://api.openai.com/v1");
        provider.setApiKey("test");
        provider.setEnabled(false);

        assertFalse(provider.isConfigured());
    }
}
