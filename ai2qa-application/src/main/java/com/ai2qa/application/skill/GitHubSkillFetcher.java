package com.ai2qa.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Fetches SKILL.md files from GitHub repositories.
 *
 * <p>Converts standard GitHub URLs to raw.githubusercontent.com for direct
 * content access, and computes SHA-256 hashes for change detection.
 */
@Component
public class GitHubSkillFetcher {

    private static final Logger log = LoggerFactory.getLogger(GitHubSkillFetcher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    public GitHubSkillFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /**
     * Constructor for test injection.
     */
    GitHubSkillFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches content from a GitHub URL.
     *
     * @param githubUrl The GitHub URL (standard or raw)
     * @return Optional containing the fetch result, or empty on failure
     */
    public Optional<FetchResult> fetch(String githubUrl) {
        if (githubUrl == null || githubUrl.isBlank()) {
            return Optional.empty();
        }

        String rawUrl = toRawUrl(githubUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rawUrl))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("GitHub fetch failed for {}: HTTP {}", rawUrl, response.statusCode());
                return Optional.empty();
            }

            String content = response.body();
            if (content == null || content.isBlank()) {
                log.warn("GitHub fetch returned empty content for {}", rawUrl);
                return Optional.empty();
            }

            String hash = computeHash(content);
            return Optional.of(new FetchResult(content, rawUrl, hash));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("GitHub fetch interrupted for {}", rawUrl);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("GitHub fetch failed for {}: {}", rawUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Converts a standard GitHub URL to raw.githubusercontent.com.
     *
     * <p>Example: {@code https://github.com/owner/repo/blob/main/SKILL.md}
     * becomes {@code https://raw.githubusercontent.com/owner/repo/main/SKILL.md}
     *
     * @param url The GitHub URL
     * @return The raw content URL
     */
    static String toRawUrl(String url) {
        if (url.contains("raw.githubusercontent.com")) {
            return url;
        }
        return url
                .replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/");
    }

    /**
     * Computes SHA-256 hash of content for change detection.
     *
     * @param content The content to hash
     * @return The hex-encoded SHA-256 hash
     */
    static String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Result of a successful GitHub fetch.
     *
     * @param content   The raw file content
     * @param sourceUrl The URL the content was fetched from
     * @param sourceHash SHA-256 hash of the content
     */
    public record FetchResult(String content, String sourceUrl, String sourceHash) {
    }
}
