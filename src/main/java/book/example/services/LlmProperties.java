package book.example.services;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private List<Provider> providers = new ArrayList<>();
    private long retryBackoffMs = 1500;
    private long providerCooldownMs = 10000;
    private int maxProviderAttempts = 3;
    private int connectionTimeoutMs = 15000;
    private int readTimeoutMs = 180000;

    public List<Provider> getProviders() { return providers; }
    public void setProviders(List<Provider> providers) { this.providers = providers == null ? new ArrayList<>() : providers; }
    public long getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
    public long getProviderCooldownMs() { return providerCooldownMs; }
    public void setProviderCooldownMs(long providerCooldownMs) { this.providerCooldownMs = providerCooldownMs; }
    public int getMaxProviderAttempts() { return maxProviderAttempts; }
    public void setMaxProviderAttempts(int maxProviderAttempts) { this.maxProviderAttempts = maxProviderAttempts; }
    public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(int value) { this.connectionTimeoutMs = value; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int value) { this.readTimeoutMs = value; }

    public static class Provider {
        private String name;
        private String provider;
        private String model;
        private String baseUrl;
        private String apiKey;
        private String structuredOutput = "auto";
        private boolean enabled = true;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getStructuredOutput() { return structuredOutput; }
        public void setStructuredOutput(String structuredOutput) { this.structuredOutput = structuredOutput; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean useNativeStructuredOutput() {
            String mode = structuredOutput == null ? "auto" : structuredOutput.trim().toLowerCase();
            if ("native".equals(mode)) return true;
            if ("prompt".equals(mode)) return false;
            String type = provider == null ? "" : provider.trim().toLowerCase();
            return switch (type) {
                case "ollama", "openai", "openai-compatible", "gemini", "google", "google-gemini" -> true;
                case "huggingface", "hf" -> false;
                default -> false;
            };
        }

        /**
         * A provider slot is considered configured when the minimum routing
         * information is present. Empty .env slots are therefore ignored
         * automatically.
         */
        public boolean isEmptySlot() {
            return provider == null || provider.isBlank();
        }

        public boolean isConfigured() {
            return enabled
                    && provider != null && !provider.isBlank()
                    && model != null && !model.isBlank()
                    && baseUrl != null && !baseUrl.isBlank();
        }

        public void validate() {
            if (!enabled || isEmptySlot()) {
                return;
            }

            if (model == null || model.isBlank()) {
                throw new IllegalStateException("LLM provider " + displayName() + " is missing its model.");
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("LLM provider " + displayName() + " is missing its base URL.");
            }

            String type = provider.trim().toLowerCase();
            if (!switch (type) {
                case "ollama", "openai", "gemini", "google", "google-gemini",
                     "huggingface", "hf", "openai-compatible" -> true;
                default -> false;
            }) {
                throw new IllegalStateException("Unsupported LLM provider type: " + provider
                        + ". Supported types: ollama, openai, gemini, huggingface.");
            }

            if (!type.equals("ollama") && (apiKey == null || apiKey.isBlank())) {
                throw new IllegalStateException("LLM provider " + displayName()
                        + " requires an API key.");
            }

            String outputMode = structuredOutput == null ? "auto" : structuredOutput.trim().toLowerCase();
            if (!outputMode.equals("auto") && !outputMode.equals("native") && !outputMode.equals("prompt")) {
                throw new IllegalStateException("LLM provider " + displayName()
                        + " has an invalid structured-output mode. Use auto, native, or prompt.");
            }
        }

        private String displayName() {
            return name == null || name.isBlank() ? provider : name;
        }
    }
}
