package com.ai2qa.application.memory;

import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.domain.port.AgentMemoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MemoryJanitorTask.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryJanitorTask")
class MemoryJanitorTaskTest {

    @Mock
    private AgentMemoryService memoryService;

    @Mock
    private ChatClientPort chatClient;

    private MemoryJanitorTask janitorTask;

    @BeforeEach
    void setUp() {
        janitorTask = new MemoryJanitorTask(memoryService, chatClient);
    }

    @Nested
    @DisplayName("When no entries need compression")
    class NoEntriesNeedCompression {

        @Test
        @DisplayName("Should skip AI calls entirely when no entries exceed threshold")
        void skipsAiCallsWhenNoLongEntries() {
            when(memoryService.findEntriesNeedingCompression(500))
                    .thenReturn(Collections.emptyList());

            janitorTask.compressMemory();

            // No warmup or compression calls when no entries
            verify(chatClient, never()).callWithTimeout(any(), anyString(), anyLong());
        }

        @Test
        @DisplayName("Should not call detectDuplicateKeys when no entries to compress")
        void skipsDuplicateDetectionWhenNoEntries() {
            when(memoryService.findEntriesNeedingCompression(500))
                    .thenReturn(Collections.emptyList());

            janitorTask.compressMemory();

            verify(memoryService, never()).getExistingTags();
        }
    }

    @Nested
    @DisplayName("When entries need compression")
    class EntriesNeedCompression {

        @Test
        @DisplayName("Should call AI to compress long entries")
        void compressesLongEntries() {
            String longInsight = "a".repeat(600);
            String compressed = "a".repeat(300);
            AgentMemoryPort.MemoryEntry entry = new AgentMemoryPort.MemoryEntry("framework:react", longInsight);

            when(memoryService.findEntriesNeedingCompression(500))
                    .thenReturn(List.of(entry));
            // Mock warmup
            when(chatClient.callWithTimeout(isNull(), contains("Respond with exactly: OK"), eq(30L)))
                    .thenReturn("OK");
            // Mock compression
            when(chatClient.callWithTimeout(isNull(), contains("Memory Curator"), eq(30L)))
                    .thenReturn(compressed);
            when(memoryService.getExistingTags())
                    .thenReturn(List.of("framework:react"));

            janitorTask.compressMemory();

            // 2 calls: warmup + compression (duplicate detection skipped - only 1 tag)
            verify(chatClient, times(2)).callWithTimeout(any(), anyString(), anyLong());
            verify(memoryService).updateInsight("framework:react", compressed);
        }

        @Test
        @DisplayName("Should run duplicate detection only after successful compression")
        void runsDuplicateDetectionAfterCompression() {
            String longInsight = "a".repeat(600);
            String compressed = "a".repeat(300);
            AgentMemoryPort.MemoryEntry entry = new AgentMemoryPort.MemoryEntry("framework:react", longInsight);

            when(memoryService.findEntriesNeedingCompression(500))
                    .thenReturn(List.of(entry));
            // Mock warmup
            when(chatClient.callWithTimeout(isNull(), contains("Respond with exactly: OK"), eq(30L)))
                    .thenReturn("OK");
            // Mock compression
            when(chatClient.callWithTimeout(isNull(), contains("Memory Curator"), eq(30L)))
                    .thenReturn(compressed);
            // Mock duplicate detection
            when(chatClient.callWithTimeout(isNull(), contains("analyzing a knowledge base"), eq(30L)))
                    .thenReturn("NO_DUPLICATES");
            when(memoryService.getExistingTags())
                    .thenReturn(List.of("framework:react", "error:hydration"));

            janitorTask.compressMemory();

            // 3 AI calls: warmup + compression + duplicate detection
            verify(chatClient, times(3)).callWithTimeout(any(), anyString(), anyLong());
            verify(memoryService).getExistingTags();
        }

        @Test
        @DisplayName("Should skip compression when warmup fails")
        void skipsCompressionWhenWarmupFails() {
            String longInsight = "a".repeat(600);
            AgentMemoryPort.MemoryEntry entry = new AgentMemoryPort.MemoryEntry("framework:react", longInsight);

            when(memoryService.findEntriesNeedingCompression(500))
                    .thenReturn(List.of(entry));
            // Mock warmup failure
            when(chatClient.callWithTimeout(isNull(), contains("Respond with exactly: OK"), eq(30L)))
                    .thenThrow(new RuntimeException("AI timeout"));

            janitorTask.compressMemory();

            // Only warmup call, compression skipped
            verify(chatClient, times(1)).callWithTimeout(any(), anyString(), anyLong());
            verify(memoryService, never()).updateInsight(anyString(), anyString());
            verify(memoryService, never()).getExistingTags();
        }

        @Test
        @DisplayName("Should skip duplicate detection when compression fails")
        void skipsDuplicateDetectionWhenCompressionFails() {
            String longInsight = "a".repeat(600);
            AgentMemoryPort.MemoryEntry entry = new AgentMemoryPort.MemoryEntry("framework:react", longInsight);

            when(memoryService.findEntriesNeedingCompression(500))
                    .thenReturn(List.of(entry));
            // Mock warmup success
            when(chatClient.callWithTimeout(isNull(), contains("Respond with exactly: OK"), eq(30L)))
                    .thenReturn("OK");
            // Mock compression failure
            when(chatClient.callWithTimeout(isNull(), contains("Memory Curator"), eq(30L)))
                    .thenThrow(new RuntimeException("AI timeout"));

            janitorTask.compressMemory();

            // 2 AI calls: warmup + failed compression, no duplicate detection
            verify(chatClient, times(2)).callWithTimeout(any(), anyString(), anyLong());
            verify(memoryService, never()).getExistingTags();
        }

        @Test
        @DisplayName("Should not update memory when AI returns longer text")
        void doesNotUpdateWhenCompressedIsLonger() {
            String longInsight = "a".repeat(600);
            String longerResult = "a".repeat(700);
            AgentMemoryPort.MemoryEntry entry = new AgentMemoryPort.MemoryEntry("framework:react", longInsight);

            when(memoryService.findEntriesNeedingCompression(500))
                    .thenReturn(List.of(entry));
            // Mock warmup
            when(chatClient.callWithTimeout(isNull(), contains("Respond with exactly: OK"), eq(30L)))
                    .thenReturn("OK");
            // Mock compression returning longer text
            when(chatClient.callWithTimeout(isNull(), contains("Memory Curator"), eq(30L)))
                    .thenReturn(longerResult);

            janitorTask.compressMemory();

            verify(memoryService, never()).updateInsight(anyString(), anyString());
            // No successful compression → no duplicate detection
            verify(memoryService, never()).getExistingTags();
        }

        @Test
        @DisplayName("Should not update memory when AI returns blank")
        void doesNotUpdateWhenCompressedIsBlank() {
            String longInsight = "a".repeat(600);
            AgentMemoryPort.MemoryEntry entry = new AgentMemoryPort.MemoryEntry("framework:react", longInsight);

            when(memoryService.findEntriesNeedingCompression(500))
                    .thenReturn(List.of(entry));
            // Mock warmup
            when(chatClient.callWithTimeout(isNull(), contains("Respond with exactly: OK"), eq(30L)))
                    .thenReturn("OK");
            // Mock compression returning blank
            when(chatClient.callWithTimeout(isNull(), contains("Memory Curator"), eq(30L)))
                    .thenReturn("   ");

            janitorTask.compressMemory();

            verify(memoryService, never()).updateInsight(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Duplicate detection")
    class DuplicateDetection {

        @Test
        @DisplayName("Should skip duplicate detection when only one tag exists")
        void skipsDuplicateDetectionWithSingleTag() {
            String longInsight = "a".repeat(600);
            String compressed = "a".repeat(300);
            AgentMemoryPort.MemoryEntry entry = new AgentMemoryPort.MemoryEntry("framework:react", longInsight);

            when(memoryService.findEntriesNeedingCompression(500))
                    .thenReturn(List.of(entry));
            // Mock warmup
            when(chatClient.callWithTimeout(isNull(), contains("Respond with exactly: OK"), eq(30L)))
                    .thenReturn("OK");
            // Mock compression
            when(chatClient.callWithTimeout(isNull(), contains("Memory Curator"), eq(30L)))
                    .thenReturn(compressed);
            when(memoryService.getExistingTags())
                    .thenReturn(List.of("framework:react"));

            janitorTask.compressMemory();

            // Only 2 AI calls (warmup + compression), duplicate detection skipped (< 2 tags)
            verify(chatClient, times(2)).callWithTimeout(any(), anyString(), anyLong());
        }
    }
}
