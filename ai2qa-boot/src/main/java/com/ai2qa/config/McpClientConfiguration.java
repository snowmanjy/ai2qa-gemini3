package com.ai2qa.config;

import com.ai2qa.mcp.McpClient;
import com.ai2qa.mcp.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Spring configuration for MCP Client beans.
 */
@Configuration
public class McpClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpClientConfiguration.class);

    @Value("${ai2qa.mcp.server-path:#{null}}")
    private String serverPathOverride;

    @Value("${ai2qa.mcp.timeout-ms:60000}")
    private long timeoutMs;

    @Value("${ai2qa.mcp.context-creation-timeout-ms:90000}")
    private long contextCreationTimeoutMs;

    /**
     * Creates the MCP Client bean.
     *
     * <p>The server path defaults to the bundled MCP server in classpath resources,
     * but can be overridden via the ai2qa.mcp.server-path property.
     *
     * <p>Browser configuration is injected from BrowserConfiguration.
     */
    @Bean
    public McpClient mcpClient(BrowserConfiguration browserConfig) {
        Path serverPath = resolveServerPath();
        log.info("Creating MCP Client with server path: {}", serverPath);
        log.info("MCP timeouts: general={}ms, contextCreation={}ms", timeoutMs, contextCreationTimeoutMs);
        log.info("Browser config: {}", browserConfig);

        return McpClient.builder()
                .serverPath(serverPath)
                .timeoutMs(timeoutMs)
                .contextCreationTimeoutMs(contextCreationTimeoutMs)
                .browserEngine(browserConfig.getEngine())
                .snapshotMode(browserConfig.getEffectiveSnapshotMode())
                .ariaEnabled(browserConfig.isAriaEnabled())
                .fallbackEnabled(browserConfig.isFallbackEnabled())
                .build();
    }

    /**
     * Creates the MCP Tool Registry bean.
     */
    @Bean
    public McpToolRegistry mcpToolRegistry(McpClient mcpClient) {
        return new McpToolRegistry(mcpClient);
    }

    /**
     * Resolves the MCP server path.
     *
     * <p>Priority:
     * 1. Explicit configuration via ai2qa.mcp.server-path property (or AI2QA_MCP_SERVER_PATH env var)
     * 2. AI2QA_HOME environment variable + /ai2qa-mcp-bridge/src/main/resources/mcp-server
     * 3. Development paths (for IDE/Maven runs) - searches multiple possible locations
     * 4. Classpath resource extraction (for packaged JAR - requires npm install)
     */
    private Path resolveServerPath() {
        // 1. Explicit property override (Spring binds AI2QA_MCP_SERVER_PATH to ai2qa.mcp.server-path)
        if (serverPathOverride != null && !serverPathOverride.isBlank()) {
            Path path = Path.of(serverPathOverride);
            log.info("Using MCP server path from property: {} (exists: {})", serverPathOverride, path.toFile().exists());
            return path;
        }

        // 1b. Fallback: Check AI2QA_MCP_SERVER_PATH env var directly (for Docker/Cloud Run)
        String mcpServerPath = System.getenv("AI2QA_MCP_SERVER_PATH");
        if (mcpServerPath != null && !mcpServerPath.isBlank()) {
            Path path = Path.of(mcpServerPath);
            log.info("Using MCP server from AI2QA_MCP_SERVER_PATH env: {} (exists: {})", mcpServerPath, path.toFile().exists());
            return path;
        }

        // 2. AI2QA_HOME environment variable (recommended for development)
        String ai2qaHome = System.getenv("AI2QA_HOME");
        if (ai2qaHome != null && !ai2qaHome.isBlank()) {
            Path homePath = Path.of(ai2qaHome, "ai2qa-mcp-bridge/src/main/resources/mcp-server");
            if (homePath.toFile().exists() && homePath.resolve("node_modules").toFile().exists()) {
                log.info("Using MCP server from AI2QA_HOME: {}", homePath);
                return homePath.toAbsolutePath();
            } else {
                log.warn("AI2QA_HOME is set but MCP server not found at: {}", homePath);
            }
        }

        // 3. Try multiple possible paths relative to different working directories
        String cwd = System.getProperty("user.dir");
        log.debug("Resolving MCP server path. Working directory: {}", cwd);

        String[] possiblePaths = {
            "ai2qa-mcp-bridge/src/main/resources/mcp-server",  // From project root
            "../ai2qa-mcp-bridge/src/main/resources/mcp-server",  // From ai2qa-boot
            "src/main/resources/mcp-server",  // From mcp-bridge module directly
        };

        for (String pathStr : possiblePaths) {
            Path path = Path.of(pathStr);
            if (path.toFile().exists() && path.resolve("node_modules").toFile().exists()) {
                log.info("Found MCP server at: {} (resolved: {})", pathStr, path.toAbsolutePath());
                return path.toAbsolutePath();
            }
        }

        // 4. Attempt classpath extraction (packaged JARs)
        var extractedPath = extractClasspathServer();
        if (extractedPath.isPresent()) {
            log.warn("Using extracted classpath server at: {} - node_modules may be missing!", extractedPath.get());
            return extractedPath.get();
        }

        // Default: provide helpful error message
        throw new IllegalStateException(
                "MCP server path not found. Set AI2QA_HOME environment variable to project root, " +
                "or set ai2qa.mcp.server-path property. Working dir: " + cwd
        );
    }

    private java.util.Optional<Path> extractClasspathServer() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:mcp-server/*");
            if (resources.length == 0) {
                return java.util.Optional.empty();
            }

            Path tempDir = Files.createTempDirectory("ai2qa-mcp-server-");
            tempDir.toFile().deleteOnExit();

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                Path target = tempDir.resolve(filename);
                Files.copy(resource.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                target.toFile().deleteOnExit();
            }

            log.debug("Extracted MCP server resources to {}", tempDir);
            return java.util.Optional.of(tempDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract MCP server resources", e);
        }
    }
}
