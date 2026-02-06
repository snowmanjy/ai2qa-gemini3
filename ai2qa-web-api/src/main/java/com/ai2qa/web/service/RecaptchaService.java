package com.ai2qa.web.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * Service to verify Google reCAPTCHA v3 tokens.
 * Prevents bot abuse.
 */
@Service
public class RecaptchaService {

    private static final Logger log = LoggerFactory.getLogger(RecaptchaService.class);
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private static final double MIN_SCORE = 0.5;

    @Value("${recaptcha.secret-key:}")
    private String secretKey;

    @Value("${recaptcha.enabled:false}")
    private boolean enabled;

    private final RestClient restClient;

    public RecaptchaService() {
        this.restClient = RestClient.create();
    }

    /**
     * Verify a reCAPTCHA token.
     *
     * @param token The reCAPTCHA token from the frontend
     * @return true if verification passes or reCAPTCHA is disabled, false otherwise
     */
    public boolean verify(String token) {
        if (!enabled) {
            log.debug("reCAPTCHA verification disabled");
            return true;
        }

        if (token == null || token.isBlank()) {
            log.warn("reCAPTCHA token is missing");
            return false;
        }

        if (secretKey == null || secretKey.isBlank()) {
            log.warn("reCAPTCHA secret key not configured, skipping verification");
            return true;
        }

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("secret", secretKey);
            formData.add("response", token);

            RecaptchaResponse response = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(RecaptchaResponse.class);

            if (response == null) {
                log.error("reCAPTCHA verification returned null response");
                return false;
            }

            if (!response.success()) {
                log.warn("reCAPTCHA verification failed: errors={}", response.errorCodes());
                return false;
            }

            double score = response.score().orElse(0.0);
            if (score < MIN_SCORE) {
                log.warn("reCAPTCHA score too low: {} (minimum: {})", score, MIN_SCORE);
                return false;
            }

            log.debug("reCAPTCHA verification passed: score={}", score);
            return true;

        } catch (Exception e) {
            log.error("reCAPTCHA verification error", e);
            // Fail open in case of network issues
            return true;
        }
    }

    /**
     * Check if reCAPTCHA verification is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RecaptchaResponse(
            boolean success,
            @JsonProperty("score") Double scoreValue,
            String action,
            @JsonProperty("challenge_ts") String challengeTs,
            String hostname,
            @JsonProperty("error-codes") List<String> errorCodes
    ) {
        Optional<Double> score() {
            return Optional.ofNullable(scoreValue);
        }
    }
}
