package com.ai2qa.application.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GitHubSkillFetcher")
class GitHubSkillFetcherTest {

    @Nested
    @DisplayName("toRawUrl()")
    class ToRawUrlTests {

        @Test
        @DisplayName("converts standard GitHub URL to raw URL")
        void convertsStandardUrl() {
            String input = "https://github.com/owner/repo/blob/main/SKILL.md";
            String expected = "https://raw.githubusercontent.com/owner/repo/main/SKILL.md";

            assertThat(GitHubSkillFetcher.toRawUrl(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("preserves already-raw URL")
        void preservesRawUrl() {
            String input = "https://raw.githubusercontent.com/owner/repo/main/SKILL.md";

            assertThat(GitHubSkillFetcher.toRawUrl(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("converts URL with nested path")
        void convertsNestedPath() {
            String input = "https://github.com/org/repo/blob/main/skills/security/SKILL.md";
            String expected = "https://raw.githubusercontent.com/org/repo/main/skills/security/SKILL.md";

            assertThat(GitHubSkillFetcher.toRawUrl(input)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("computeHash()")
    class ComputeHashTests {

        @Test
        @DisplayName("produces consistent SHA-256 hash")
        void producesConsistentHash() {
            String content = "test content";
            String hash1 = GitHubSkillFetcher.computeHash(content);
            String hash2 = GitHubSkillFetcher.computeHash(content);

            assertThat(hash1).isEqualTo(hash2);
            assertThat(hash1).hasSize(64); // SHA-256 produces 32 bytes = 64 hex chars
        }

        @Test
        @DisplayName("different content produces different hash")
        void differentContentDifferentHash() {
            String hash1 = GitHubSkillFetcher.computeHash("content A");
            String hash2 = GitHubSkillFetcher.computeHash("content B");

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("fetch()")
    class FetchTests {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("happy path returns content and hash")
        void happyPathReturnsContentAndHash() throws Exception {
            HttpClient mockClient = mock(HttpClient.class);
            HttpResponse<String> mockResponse = mock(HttpResponse.class);

            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.body()).thenReturn("---\nname: test\n---\n# Body");
            when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockResponse);

            GitHubSkillFetcher fetcher = new GitHubSkillFetcher(mockClient);
            Optional<GitHubSkillFetcher.FetchResult> result =
                    fetcher.fetch("https://github.com/owner/repo/blob/main/SKILL.md");

            assertThat(result).isPresent();
            assertThat(result.get().content()).contains("name: test");
            assertThat(result.get().sourceUrl()).contains("raw.githubusercontent.com");
            assertThat(result.get().sourceHash()).hasSize(64);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("HTTP error returns empty")
        void httpErrorReturnsEmpty() throws Exception {
            HttpClient mockClient = mock(HttpClient.class);
            HttpResponse<String> mockResponse = mock(HttpResponse.class);

            when(mockResponse.statusCode()).thenReturn(404);
            when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(mockResponse);

            GitHubSkillFetcher fetcher = new GitHubSkillFetcher(mockClient);
            Optional<GitHubSkillFetcher.FetchResult> result =
                    fetcher.fetch("https://github.com/owner/repo/blob/main/SKILL.md");

            assertThat(result).isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("IOException returns empty")
        void ioExceptionReturnsEmpty() throws Exception {
            HttpClient mockClient = mock(HttpClient.class);
            when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("Connection timed out"));

            GitHubSkillFetcher fetcher = new GitHubSkillFetcher(mockClient);
            Optional<GitHubSkillFetcher.FetchResult> result =
                    fetcher.fetch("https://github.com/owner/repo/blob/main/SKILL.md");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null URL returns empty")
        void nullUrlReturnsEmpty() {
            GitHubSkillFetcher fetcher = new GitHubSkillFetcher(mock(HttpClient.class));

            assertThat(fetcher.fetch(null)).isEmpty();
        }

        @Test
        @DisplayName("blank URL returns empty")
        void blankUrlReturnsEmpty() {
            GitHubSkillFetcher fetcher = new GitHubSkillFetcher(mock(HttpClient.class));

            assertThat(fetcher.fetch("   ")).isEmpty();
        }
    }
}
