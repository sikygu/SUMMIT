package org.evosuite.testcase.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * LLMService is responsible for interacting with Large Language Models (LLM) APIs
 * to generate test cases based on given context.
 * This service implements the Singleton pattern to ensure single point of API access.
 */
public class LLMService {
    private static final Logger logger = LoggerFactory.getLogger(LLMService.class);
    private static LLMService instance = null;
    private final Properties config;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private static final String CONFIG_FILE = "/llm.properties";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * Private constructor to enforce singleton pattern and load configuration
     */
    private LLMService() {
        config = new Properties();
        try (InputStream input = getClass().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + CONFIG_FILE);
            }
            config.load(input);
        } catch (IOException e) {
            logger.error("Failed to load LLM configuration", e);
            throw new RuntimeException("Failed to initialize LLM service", e);
        }

        // Initialize OkHttpClient with timeouts
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        mapper = new ObjectMapper();
    }

    /**
     * Returns the singleton instance of LLMService
     *
     * @return LLMService instance
     */
    public static LLMService getInstance() {
        if (instance == null) {
            instance = new LLMService();
        }
        return instance;
    }

    /**
     * Queries the LLM API with the given prompt
     *
     * @param prompt The formatted prompt to send to the LLM
     * @return The generated response from the LLM
     */
    public String queryLLM(String prompt) {
        try {
            Map<String, Object> body = new HashMap<>();
            String model = config.getProperty("openai.model");
            if (model == null) {
                throw new IllegalStateException("openai.model not configured in llm.properties");
            }
            body.put("model", model);

            String tempStr = config.getProperty("openai.temperature");
            if (tempStr == null) {
                throw new IllegalStateException("openai.temperature not configured in llm.properties");
            }
            body.put("temperature", Double.parseDouble(tempStr));

            String maxTokensStr = config.getProperty("openai.max_tokens");
            if (maxTokensStr == null) {
                throw new IllegalStateException("openai.max_tokens not configured in llm.properties");
            }
            body.put("max_tokens", Integer.parseInt(maxTokensStr));

            body.put("stream", false);

            ArrayList<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a test generation system. Return content ONLY in the required format");
            messages.add(systemMessage);

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);

            body.put("messages", messages);

            String jsonBody = mapper.writeValueAsString(body);
            logger.debug("Sending request to LLM API:\n{}", jsonBody);

            RequestBody requestBody = RequestBody.create(jsonBody, JSON);

            String apiUrl = config.getProperty("openai.api.url");
            if (apiUrl == null) {
                throw new IllegalStateException("openai.api.url not configured in llm.properties");
            }

            String apiKey = config.getProperty("openai.api.key");
            if (apiKey == null) {
                throw new IllegalStateException("openai.api.key not configured in llm.properties");
            }

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    String errorMsg = response.body() != null ? response.body().string() : "No response body";
                    logger.error("API call failed: " + errorMsg);
                    throw new RuntimeException("Failed to get response from LLM API: " + response.code());
                }

                String responseBody = response.body().string();
                Map<String, Object> responseMap = mapper.readValue(responseBody, Map.class);
                ArrayList<Map<String, Object>> choices = (ArrayList<Map<String, Object>>) responseMap.get("choices");

                if (choices == null || choices.isEmpty()) {
                    throw new RuntimeException("No choices in LLM response");
                }

                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message == null) {
                    throw new RuntimeException("No message in LLM response choice");
                }

                String content = (String) message.get("content");
                if (content == null) {
                    throw new RuntimeException("No content in LLM response message");
                }

                logger.debug("Received response from LLM:\n{}", content);
                return content;
            }
        } catch (Exception e) {
            logger.error("Failed to query LLM API", e);
            throw new RuntimeException("Failed to generate test code: " + e.getMessage(), e);
        }
    }
}
