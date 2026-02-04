package com.ai2qa.config.ai;

import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.application.port.PromptLoaderPort;
import com.ai2qa.config.BrowserConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import com.google.cloud.vertexai.VertexAI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Gemini-only AI configuration for hackathon.
 *
 * <p>Simplified to use only Vertex AI / Gemini for the
 * Google DeepMind Gemini 3 Hackathon.
 */
@Configuration
public class AiProviderConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfiguration.class);

    @Value("${ai2qa.ai.model:gemini-2.5-flash}")
    private String model;

    @Value("${ai2qa.ai.temperature:0.2}")
    private double temperature;

    @Value("${GOOGLE_CLOUD_PROJECT:}")
    private String gcpProject;

    @Value("${spring.ai.vertex.ai.gemini.location:us-central1}")
    private String gcpLocation;

    /**
     * Creates Vertex AI Gemini ChatModel.
     */
    @Bean
    @Primary
    public ChatModel geminiChatModel() {
        log.info("Creating Vertex AI Gemini ChatModel with project: {}, location: {}, model: {}",
                gcpProject, gcpLocation, model);
        VertexAI vertexAI = new VertexAI(gcpProject, gcpLocation);
        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .build();
        return new VertexAiGeminiChatModel(vertexAI, options);
    }

    /**
     * Creates the primary ChatClient.Builder bean.
     */
    @Bean
    @Primary
    public ChatClient.Builder primaryChatClientBuilder(ChatModel chatModel) {
        log.info("Initializing Gemini AI with model: {} (temperature: {})", model, temperature);
        return ChatClient.builder(chatModel);
    }

    /**
     * Creates a generic ChatClient for AI operations.
     */
    @Bean
    public ChatClient genericChatClient(ChatClient.Builder builder) {
        log.debug("Creating generic ChatClient");
        return builder.build();
    }

    /**
     * Creates a ChatClientPort for step planning operations (The Architect).
     */
    @Bean("plannerChatPort")
    public ChatClientPort plannerChatPort(
            ChatClient genericChatClient,
            PromptLoaderPort promptLoader) {
        log.debug("Creating Architect ChatClientPort");
        return new SpringChatClientPort(genericChatClient, null, null,
                model, null, "architect", promptLoader, null);
    }

    /**
     * Creates a ChatClientPort for selector finding operations (The Hunter).
     */
    @Bean("selectorChatPort")
    public ChatClientPort selectorChatPort(
            ChatClient genericChatClient,
            PromptLoaderPort promptLoader,
            BrowserConfiguration browserConfig) {
        log.debug("Creating Hunter ChatClientPort");
        return new SpringChatClientPort(genericChatClient, null, null,
                model, null, "hunter", promptLoader, browserConfig);
    }

    /**
     * Creates a ChatClientPort for repair planning operations (The Healer).
     */
    @Bean("repairChatPort")
    public ChatClientPort repairChatPort(
            ChatClient genericChatClient,
            PromptLoaderPort promptLoader,
            BrowserConfiguration browserConfig) {
        log.debug("Creating Healer ChatClientPort");
        return new SpringChatClientPort(genericChatClient, null, null,
                model, null, "healer", promptLoader, browserConfig);
    }

    @Bean
    public com.ai2qa.application.port.AiProviderInfo aiProviderInfo() {
        return new com.ai2qa.application.port.AiProviderInfo("vertex-ai", model);
    }

    @Bean
    public AiConfigurationLogger aiConfigurationLogger() {
        return new AiConfigurationLogger(model, temperature);
    }

    public static class AiConfigurationLogger {
        public AiConfigurationLogger(String model, double temperature) {
            LoggerFactory.getLogger(AiConfigurationLogger.class).info(
                    "\n╔══════════════════════════════════════════════════════════╗\n" +
                            "║          GEMINI 3 HACKATHON AI CONFIGURATION             ║\n" +
                            "╠══════════════════════════════════════════════════════════╣\n" +
                            "║  Provider:    Vertex AI / Gemini\n" +
                            "║  Model:       {}\n" +
                            "║  Temperature: {}\n" +
                            "╚══════════════════════════════════════════════════════════╝",
                    model, temperature);
        }
    }
}
