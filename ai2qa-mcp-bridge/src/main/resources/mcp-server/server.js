#!/usr/bin/env node
/**
 * Ai2QA MCP Server - JSON-RPC over Stdio
 *
 * Implements the Model Context Protocol (MCP) for browser automation.
 * Communicates with the Java McpClient via stdin/stdout using JSON-RPC 2.0.
 *
 * Supports two browser engines:
 * - puppeteer: Legacy CSS selector-based snapshots (browser.js)
 * - playwright: Accessibility tree snapshots with refs (playwright-engine.js)
 */

import * as readline from 'readline';
import * as puppeteerBrowser from './browser.js';
import * as playwrightBrowser from './playwright-engine.js';
import { parseRef, isRef } from './aria-snapshot.js';

const SERVER_INFO = {
    name: 'ai2qa-mcp-server',
    version: '1.0.0',
};

const PROTOCOL_VERSION = '2024-11-05';

// Browser configuration (set during initialize)
let browserConfig = {
    engine: 'puppeteer',
    snapshotMode: 'auto',
    ariaEnabled: true,
    fallbackEnabled: true,
};

// Active browser engine (selected based on config)
let browser = puppeteerBrowser;

// Tool definitions for MCP
const TOOLS = [
    {
        name: 'navigate_page',
        description: 'Navigate the browser to a URL or perform navigation actions (back, forward, reload)',
        inputSchema: {
            type: 'object',
            properties: {
                url: { type: 'string', description: 'The URL to navigate to (required for type=url)' },
                type: { type: 'string', enum: ['url', 'back', 'forward', 'reload'], default: 'url' },
                timeout: { type: 'integer', description: 'Navigation timeout in milliseconds' },
            },
        },
    },
    {
        name: 'click',
        description: 'Click on an element by CSS selector or ref',
        inputSchema: {
            type: 'object',
            properties: {
                selector: { type: 'string', description: 'CSS selector of the element to click' },
                ref: { type: 'string', description: 'Element ref (e.g., "@e1") from aria snapshot' },
                uid: { type: 'string', description: 'Legacy alias for selector' },
                dblClick: { type: 'boolean', description: 'Double-click instead of single click', default: false },
            },
        },
    },
    {
        name: 'fill',
        description: 'Type text into an input element',
        inputSchema: {
            type: 'object',
            properties: {
                selector: { type: 'string', description: 'CSS selector of the input element' },
                ref: { type: 'string', description: 'Element ref (e.g., "@e2") from aria snapshot' },
                uid: { type: 'string', description: 'Legacy alias for selector' },
                value: { type: 'string', description: 'The text to type' },
                clear: { type: 'boolean', description: 'Clear existing content first', default: false },
            },
            required: ['value'],
        },
    },
    {
        name: 'hover',
        description: 'Hover over an element',
        inputSchema: {
            type: 'object',
            properties: {
                selector: { type: 'string', description: 'CSS selector of the element' },
                ref: { type: 'string', description: 'Element ref (e.g., "@e3") from aria snapshot' },
                uid: { type: 'string', description: 'Legacy alias for selector' },
            },
        },
    },
    {
        name: 'press_key',
        description: 'Press a key or key combination (e.g., "Enter", "Control+A")',
        inputSchema: {
            type: 'object',
            properties: {
                key: { type: 'string', description: 'Key or key combination to press' },
            },
            required: ['key'],
        },
    },
    {
        name: 'take_screenshot',
        description: 'Capture a screenshot of the page or element',
        inputSchema: {
            type: 'object',
            properties: {
                fullPage: { type: 'boolean', description: 'Capture full page', default: false },
                selector: { type: 'string', description: 'CSS selector to screenshot specific element' },
                format: { type: 'string', enum: ['png', 'jpeg', 'webp'], default: 'png' },
                quality: { type: 'integer', description: 'Quality 0-100 for jpeg/webp' },
            },
        },
    },
    {
        name: 'take_snapshot',
        description: 'Get the DOM snapshot (accessibility tree or CSS selectors)',
        inputSchema: {
            type: 'object',
            properties: {
                verbose: { type: 'boolean', description: 'Include all nodes, not just interesting ones', default: false },
                mode: { type: 'string', enum: ['legacy', 'aria', 'auto'], description: 'Snapshot mode: legacy (CSS selectors), aria (refs), auto (uses config)', default: 'auto' },
                interactive: { type: 'boolean', description: 'Only include interactive elements (aria mode only)', default: false },
            },
        },
    },
    {
        name: 'wait_for',
        description: 'Wait for a condition',
        inputSchema: {
            type: 'object',
            properties: {
                selector: { type: 'string', description: 'Wait for this selector to appear' },
                text: { type: 'string', description: 'Wait for this text to appear' },
                ms: { type: 'integer', description: 'Wait for this many milliseconds' },
                timeout: { type: 'integer', description: 'Maximum wait time' },
            },
        },
    },
    {
        name: 'evaluate',
        description: 'Execute JavaScript in the page context',
        inputSchema: {
            type: 'object',
            properties: {
                script: { type: 'string', description: 'JavaScript code to execute' },
            },
            required: ['script'],
        },
    },
    {
        name: 'get_performance_metrics',
        description: 'Capture Core Web Vitals and performance metrics from the current page',
        inputSchema: {
            type: 'object',
            properties: {
                includeResources: { type: 'boolean', description: 'Include resource timing data', default: true },
                resourceThresholdMs: { type: 'integer', description: 'Threshold for slow resources in ms', default: 500 },
                resourceThresholdKb: { type: 'integer', description: 'Threshold for large resources in KB', default: 100 },
            },
        },
    },
];

// JSON-RPC error codes
const ERROR_CODES = {
    PARSE_ERROR: -32700,
    INVALID_REQUEST: -32600,
    METHOD_NOT_FOUND: -32601,
    INVALID_PARAMS: -32602,
    INTERNAL_ERROR: -32603,
};

/**
 * Handles JSON-RPC requests.
 */
async function handleRequest(request) {
    const { id, method, params } = request;

    try {
        switch (method) {
            case 'initialize':
                // Process browser configuration from client
                if (params?.browserConfig) {
                    const cfg = params.browserConfig;
                    browserConfig = {
                        engine: cfg.engine || 'puppeteer',
                        snapshotMode: cfg.snapshotMode || 'auto',
                        ariaEnabled: cfg.ariaEnabled !== false,
                        fallbackEnabled: cfg.fallbackEnabled !== false,
                    };

                    // Select browser engine based on config
                    if (browserConfig.engine === 'playwright') {
                        browser = playwrightBrowser;
                        console.error('[Server] Using Playwright engine');
                    } else {
                        browser = puppeteerBrowser;
                        console.error('[Server] Using Puppeteer engine');
                    }

                    console.error('[Server] Browser config:', JSON.stringify(browserConfig));
                }

                return {
                    jsonrpc: '2.0',
                    id,
                    result: {
                        protocolVersion: PROTOCOL_VERSION,
                        capabilities: {
                            tools: {},
                        },
                        serverInfo: SERVER_INFO,
                        browserConfig: browserConfig,
                    },
                };

            case 'notifications/initialized':
                // Client confirmed initialization - launch browser
                await browser.launch();
                return null; // Notifications don't get responses

            case 'tools/list':
                return {
                    jsonrpc: '2.0',
                    id,
                    result: { tools: TOOLS },
                };

            case 'browser/createContext':
                // Context creation has retry logic in browser.js, but we add
                // a server-level retry as fallback for edge cases
                let contextAttempt = 0;
                const maxContextAttempts = 2;
                let lastContextError = null;

                while (contextAttempt < maxContextAttempts) {
                    try {
                        contextAttempt++;
                        await browser.createContext(params || {});
                        return {
                            jsonrpc: '2.0',
                            id,
                            result: { success: true, runId: params?.runId },
                        };
                    } catch (contextError) {
                        lastContextError = contextError;
                        const isRetryable = contextError.message &&
                            (contextError.message.includes('main frame') ||
                             contextError.message.includes('Target closed'));

                        if (isRetryable && contextAttempt < maxContextAttempts) {
                            console.error(`[Server] Context creation failed (attempt ${contextAttempt}), retrying: ${contextError.message}`);
                            await new Promise(resolve => setTimeout(resolve, 500));
                        } else {
                            throw contextError;
                        }
                    }
                }
                throw lastContextError;

            case 'browser/closeContext':
                await browser.closeContext();
                return {
                    jsonrpc: '2.0',
                    id,
                    result: { success: true },
                };

            case 'tools/call':
                const toolResult = await executeTool(params.name, params.arguments || {});

                // Inject captured console logs/errors
                toolResult.logs = browser.flushLogs();

                return {
                    jsonrpc: '2.0',
                    id,
                    result: toolResult,
                };

            case 'shutdown':
                await browser.close();
                return {
                    jsonrpc: '2.0',
                    id,
                    result: { success: true },
                };

            default:
                return {
                    jsonrpc: '2.0',
                    id,
                    error: {
                        code: ERROR_CODES.METHOD_NOT_FOUND,
                        message: `Unknown method: ${method}`,
                    },
                };
        }
    } catch (error) {
        console.error(`[Server] Error handling ${method}:`, error.message);
        return {
            jsonrpc: '2.0',
            id,
            error: {
                code: ERROR_CODES.INTERNAL_ERROR,
                message: error.message,
            },
        };
    }
}

/**
 * Executes a tool call.
 */
async function executeTool(name, args) {
    console.error(`[Server] Executing tool: ${name}`, JSON.stringify(args));

    try {
        let result;

        switch (name) {
            case 'navigate_page':
                if (args.type === 'back') {
                    // Use browser back
                    const { page } = await browser.launch();
                    await page.goBack();
                    result = await browser.getPageInfo();
                } else if (args.type === 'forward') {
                    const { page } = await browser.launch();
                    await page.goForward();
                    result = await browser.getPageInfo();
                } else if (args.type === 'reload') {
                    const { page } = await browser.launch();
                    await page.reload();
                    result = await browser.getPageInfo();
                } else {
                    result = await browser.navigateTo(args.url, { timeout: args.timeout });
                }
                break;

            case 'click':
                {
                    const selectorOrRef = resolveElementTarget(args);
                    if (!selectorOrRef) {
                        throw new Error('Click requires a selector or ref');
                    }
                    result = await browser.click(selectorOrRef, { dblClick: args.dblClick });
                }
                break;

            case 'fill':
                {
                    const selectorOrRef = resolveElementTarget(args);
                    if (!selectorOrRef) {
                        throw new Error('Fill requires a selector or ref');
                    }
                    result = await browser.type(selectorOrRef, args.value, { clear: args.clear });
                }
                break;

            case 'hover':
                {
                    const selectorOrRef = resolveElementTarget(args);
                    if (!selectorOrRef) {
                        throw new Error('Hover requires a selector or ref');
                    }
                    result = await browser.hover(selectorOrRef);
                }
                break;

            case 'press_key':
                result = await browser.pressKey(args.key);
                break;

            case 'take_screenshot':
                const screenshotResult = await browser.screenshot({
                    fullPage: args.fullPage,
                    selector: args.selector,
                    format: args.format,
                    quality: args.quality,
                });
                return {
                    content: [
                        {
                            type: 'image',
                            data: screenshotResult.data,
                            mimeType: screenshotResult.mimeType,
                        },
                    ],
                };

            case 'take_snapshot':
                const snapshotMode = resolveSnapshotMode(args.mode);
                console.error(`[Server] Taking snapshot with mode=${snapshotMode} (engine=${browserConfig.engine})`);
                const snapshotResult = await browser.snapshot({
                    verbose: args.verbose,
                    mode: snapshotMode,
                    interactive: args.interactive,
                });

                // Different response format for aria vs legacy
                if (snapshotMode === 'aria' && snapshotResult.tree) {
                    const contentLength = snapshotResult.tree?.length || 0;
                    const refCount = Object.keys(snapshotResult.refs || {}).length;
                    console.error(`[Server] Aria snapshot: ${contentLength} chars, ${refCount} interactive refs`);
                    return {
                        content: [
                            {
                                type: 'text',
                                text: JSON.stringify({
                                    content: snapshotResult.tree,
                                    refs: snapshotResult.refs,
                                    url: snapshotResult.url,
                                    title: snapshotResult.title,
                                    mode: 'aria',
                                }),
                            },
                        ],
                    };
                }

                const legacyContentLength = (snapshotResult.text || snapshotResult.tree || '').length;
                console.error(`[Server] Legacy snapshot: ${legacyContentLength} chars`);
                return {
                    content: [
                        {
                            type: 'text',
                            text: JSON.stringify({
                                content: snapshotResult.text || snapshotResult.tree,
                                url: snapshotResult.url,
                                title: snapshotResult.title,
                                mode: 'legacy',
                            }),
                        },
                    ],
                };

            case 'wait_for':
                result = await browser.waitFor(args);
                break;

            case 'evaluate':
                result = await browser.evaluate(args.script);
                break;

            case 'get_performance_metrics':
                result = await collectPerformanceMetrics(args);
                break;

            default:
                throw new Error(`Unknown tool: ${name}`);
        }

        return {
            content: [
                {
                    type: 'text',
                    text: JSON.stringify(result),
                },
            ],
        };
    } catch (error) {
        console.error(`[Server] Tool error:`, error.message);
        return {
            content: [
                {
                    type: 'text',
                    text: `Error: ${error.message}`,
                },
            ],
            isError: true,
        };
    }
}

/**
 * Resolves element target from args - prefers ref, then selector, then uid.
 * @param {Object} args - Tool arguments
 * @returns {string|null} - The selector or ref to use
 */
function resolveElementTarget(args) {
    // Prefer ref if provided
    if (args.ref) {
        return args.ref;
    }

    // Fall back to selector or uid
    return args.selector || args.uid || null;
}

/**
 * Resolves the snapshot mode based on args and config.
 * @param {string} requestedMode - Mode requested in args ('legacy', 'aria', 'auto')
 * @returns {string} - The resolved mode ('legacy' or 'aria')
 */
function resolveSnapshotMode(requestedMode) {
    // If aria is disabled by kill-switch, always use legacy
    if (!browserConfig.ariaEnabled) {
        console.error('[Server] Aria disabled by kill-switch, using legacy');
        return 'legacy';
    }

    // If explicit mode requested, honor it
    if (requestedMode === 'legacy' || requestedMode === 'aria') {
        // But aria requires playwright engine
        if (requestedMode === 'aria' && browserConfig.engine !== 'playwright') {
            if (browserConfig.fallbackEnabled) {
                console.error('[Server] Aria mode requested but Puppeteer engine active, falling back to legacy');
                return 'legacy';
            }
            throw new Error('Aria snapshot mode requires Playwright engine');
        }
        return requestedMode;
    }

    // Auto mode: use aria if playwright engine, otherwise legacy
    if (browserConfig.snapshotMode === 'aria' && browserConfig.engine === 'playwright') {
        return 'aria';
    }

    return 'legacy';
}

/**
 * Performance thresholds based on Google's Core Web Vitals guidelines.
 */
const PERF_THRESHOLDS = {
    LCP_GOOD: 2500,
    LCP_POOR: 4000,
    CLS_GOOD: 0.1,
    CLS_POOR: 0.25,
    FID_GOOD: 100,
    FID_POOR: 300,
    TTFB_GOOD: 800,
    TTFB_POOR: 1800,
    LOAD_GOOD: 3000,
    LOAD_POOR: 5000,
};

/**
 * JavaScript to execute in page context to collect Performance API data.
 */
const PERFORMANCE_COLLECTION_SCRIPT = `
(() => {
    const result = {
        webVitals: {},
        navigation: {},
        resources: [],
    };

    // Navigation Timing API
    const navTiming = performance.getEntriesByType('navigation')[0];
    if (navTiming) {
        result.navigation = {
            dnsLookup: navTiming.domainLookupEnd - navTiming.domainLookupStart,
            tcpConnect: navTiming.connectEnd - navTiming.connectStart,
            tlsHandshake: navTiming.secureConnectionStart > 0
                ? navTiming.connectEnd - navTiming.secureConnectionStart
                : 0,
            ttfb: navTiming.responseStart - navTiming.requestStart,
            domContentLoaded: navTiming.domContentLoadedEventEnd - navTiming.startTime,
            pageLoad: navTiming.loadEventEnd - navTiming.startTime,
            domInteractive: navTiming.domInteractive - navTiming.startTime,
        };
        result.webVitals.ttfb = navTiming.responseStart - navTiming.requestStart;
    }

    // Paint Timing API (FCP)
    const paintEntries = performance.getEntriesByType('paint');
    for (const entry of paintEntries) {
        if (entry.name === 'first-contentful-paint') {
            result.webVitals.fcp = entry.startTime;
        }
    }

    // LCP (Largest Contentful Paint)
    const lcpEntries = performance.getEntriesByType('largest-contentful-paint');
    if (lcpEntries.length > 0) {
        const lastLcp = lcpEntries[lcpEntries.length - 1];
        result.webVitals.lcp = lastLcp.startTime;
    }

    // CLS (Cumulative Layout Shift)
    const layoutShiftEntries = performance.getEntriesByType('layout-shift');
    let clsValue = 0;
    let sessionValue = 0;
    let sessionEntries = [];
    for (const entry of layoutShiftEntries) {
        if (!entry.hadRecentInput) {
            if (sessionEntries.length > 0 &&
                entry.startTime - sessionEntries[sessionEntries.length - 1].startTime < 1000 &&
                entry.startTime - sessionEntries[0].startTime < 5000) {
                sessionValue += entry.value;
                sessionEntries.push(entry);
            } else {
                clsValue = Math.max(clsValue, sessionValue);
                sessionValue = entry.value;
                sessionEntries = [entry];
            }
        }
    }
    clsValue = Math.max(clsValue, sessionValue);
    result.webVitals.cls = clsValue;

    // FID (First Input Delay) - only available if there was user input
    const fidEntries = performance.getEntriesByType('first-input');
    if (fidEntries.length > 0) {
        result.webVitals.fid = fidEntries[0].processingStart - fidEntries[0].startTime;
    }

    // Resource Timing API
    const resourceEntries = performance.getEntriesByType('resource');
    result.resources = resourceEntries.map(entry => ({
        name: entry.name,
        type: entry.initiatorType,
        durationMs: entry.duration,
        transferSizeKb: (entry.transferSize || 0) / 1024,
        decodedSizeKb: (entry.decodedBodySize || 0) / 1024,
    }));

    return result;
})()
`;

/**
 * Collects performance metrics from the current page.
 */
async function collectPerformanceMetrics(args) {
    const includeResources = args.includeResources !== false;
    const resourceThresholdMs = args.resourceThresholdMs || 500;
    const resourceThresholdKb = args.resourceThresholdKb || 100;

    try {
        // Execute performance collection script in page context
        // browser.evaluate returns { result: ... }, so unwrap it
        const evalResult = await browser.evaluate(PERFORMANCE_COLLECTION_SCRIPT);
        const perfData = evalResult.result || evalResult;  // Handle both wrapped and unwrapped

        if (!perfData || (!perfData.webVitals && !perfData.navigation)) {
            console.error('[Server] Performance data is empty or malformed:', JSON.stringify(evalResult));
            return {
                success: false,
                error: 'Failed to collect performance metrics: empty result from browser',
            };
        }

        // Process resources
        let slowResources = [];
        let largeResources = [];
        let totalTransferSizeKb = 0;

        if (includeResources && perfData.resources) {
            // Calculate total transfer size
            totalTransferSizeKb = perfData.resources.reduce(
                (sum, r) => sum + (r.transferSizeKb || 0),
                0
            );

            // Find slow resources
            slowResources = perfData.resources
                .filter(r => r.durationMs > resourceThresholdMs)
                .sort((a, b) => b.durationMs - a.durationMs)
                .slice(0, 10);

            // Find large resources
            largeResources = perfData.resources
                .filter(r => r.transferSizeKb > resourceThresholdKb)
                .sort((a, b) => b.transferSizeKb - a.transferSizeKb)
                .slice(0, 10);
        }

        // Analyze and generate issues
        const issues = analyzePerformanceIssues(
            perfData.webVitals,
            perfData.navigation,
            slowResources,
            largeResources
        );

        return {
            success: true,
            webVitals: perfData.webVitals,
            navigation: perfData.navigation,
            slowResources: slowResources.length > 0 ? slowResources : undefined,
            largeResources: largeResources.length > 0 ? largeResources : undefined,
            totalResources: perfData.resources?.length ?? 0,
            totalTransferSizeKb: Math.round(totalTransferSizeKb),
            issues: issues.length > 0 ? issues : undefined,
        };
    } catch (err) {
        console.error('[Server] Performance metrics error:', err.message);
        return {
            success: false,
            error: `Failed to collect performance metrics: ${err.message}`,
        };
    }
}

/**
 * Analyzes performance data and generates issues.
 */
function analyzePerformanceIssues(webVitals, navigation, slowResources, largeResources) {
    const issues = [];

    // LCP analysis
    if (webVitals.lcp !== undefined) {
        if (webVitals.lcp >= PERF_THRESHOLDS.LCP_POOR) {
            issues.push({
                severity: 'CRITICAL',
                category: 'LCP',
                message: `Largest Contentful Paint is ${(webVitals.lcp / 1000).toFixed(2)}s (threshold: <${PERF_THRESHOLDS.LCP_POOR / 1000}s)`,
                value: webVitals.lcp,
                threshold: PERF_THRESHOLDS.LCP_POOR,
            });
        } else if (webVitals.lcp >= PERF_THRESHOLDS.LCP_GOOD) {
            issues.push({
                severity: 'HIGH',
                category: 'LCP',
                message: `Largest Contentful Paint needs improvement: ${(webVitals.lcp / 1000).toFixed(2)}s (good: <${PERF_THRESHOLDS.LCP_GOOD / 1000}s)`,
                value: webVitals.lcp,
                threshold: PERF_THRESHOLDS.LCP_GOOD,
            });
        }
    }

    // CLS analysis
    if (webVitals.cls !== undefined) {
        if (webVitals.cls >= PERF_THRESHOLDS.CLS_POOR) {
            issues.push({
                severity: 'CRITICAL',
                category: 'CLS',
                message: `Cumulative Layout Shift is ${webVitals.cls.toFixed(3)} (threshold: <${PERF_THRESHOLDS.CLS_POOR})`,
                value: webVitals.cls,
                threshold: PERF_THRESHOLDS.CLS_POOR,
            });
        } else if (webVitals.cls >= PERF_THRESHOLDS.CLS_GOOD) {
            issues.push({
                severity: 'HIGH',
                category: 'CLS',
                message: `Cumulative Layout Shift needs improvement: ${webVitals.cls.toFixed(3)} (good: <${PERF_THRESHOLDS.CLS_GOOD})`,
                value: webVitals.cls,
                threshold: PERF_THRESHOLDS.CLS_GOOD,
            });
        }
    }

    // TTFB analysis
    if (webVitals.ttfb !== undefined) {
        if (webVitals.ttfb >= PERF_THRESHOLDS.TTFB_POOR) {
            issues.push({
                severity: 'HIGH',
                category: 'TTFB',
                message: `Time to First Byte is ${webVitals.ttfb.toFixed(0)}ms (threshold: <${PERF_THRESHOLDS.TTFB_POOR}ms)`,
                value: webVitals.ttfb,
                threshold: PERF_THRESHOLDS.TTFB_POOR,
            });
        } else if (webVitals.ttfb >= PERF_THRESHOLDS.TTFB_GOOD) {
            issues.push({
                severity: 'MEDIUM',
                category: 'TTFB',
                message: `Time to First Byte needs improvement: ${webVitals.ttfb.toFixed(0)}ms (good: <${PERF_THRESHOLDS.TTFB_GOOD}ms)`,
                value: webVitals.ttfb,
                threshold: PERF_THRESHOLDS.TTFB_GOOD,
            });
        }
    }

    // Page load time analysis
    if (navigation.pageLoad !== undefined && navigation.pageLoad > 0) {
        if (navigation.pageLoad >= PERF_THRESHOLDS.LOAD_POOR) {
            issues.push({
                severity: 'CRITICAL',
                category: 'LOAD_TIME',
                message: `Page load time is ${(navigation.pageLoad / 1000).toFixed(2)}s (threshold: <${PERF_THRESHOLDS.LOAD_POOR / 1000}s)`,
                value: navigation.pageLoad,
                threshold: PERF_THRESHOLDS.LOAD_POOR,
            });
        } else if (navigation.pageLoad >= PERF_THRESHOLDS.LOAD_GOOD) {
            issues.push({
                severity: 'HIGH',
                category: 'LOAD_TIME',
                message: `Page load time needs improvement: ${(navigation.pageLoad / 1000).toFixed(2)}s (good: <${PERF_THRESHOLDS.LOAD_GOOD / 1000}s)`,
                value: navigation.pageLoad,
                threshold: PERF_THRESHOLDS.LOAD_GOOD,
            });
        }
    }

    // Slow resources
    if (slowResources.length > 0) {
        const topSlow = slowResources.slice(0, 3);
        for (const resource of topSlow) {
            const filename = resource.name.split('/').pop()?.split('?')[0] || resource.name;
            issues.push({
                severity: resource.durationMs > 2000 ? 'HIGH' : 'MEDIUM',
                category: 'RESOURCE',
                message: `Slow resource: ${filename.substring(0, 50)} took ${resource.durationMs.toFixed(0)}ms`,
                value: resource.durationMs,
            });
        }
    }

    // Large resources
    if (largeResources.length > 0) {
        const topLarge = largeResources.slice(0, 3);
        for (const resource of topLarge) {
            const filename = resource.name.split('/').pop()?.split('?')[0] || resource.name;
            issues.push({
                severity: resource.transferSizeKb > 500 ? 'HIGH' : 'MEDIUM',
                category: 'RESOURCE',
                message: `Large resource: ${filename.substring(0, 50)} is ${resource.transferSizeKb.toFixed(0)}KB`,
                value: resource.transferSizeKb,
            });
        }
    }

    return issues;
}

/**
 * Sends a JSON-RPC response.
 */
function sendResponse(response) {
    if (response) {
        const json = JSON.stringify(response);
        console.log(json);
    }
}

/**
 * Main server loop - reads JSON-RPC requests from stdin.
 */
async function main() {
    console.error('[Server] Ai2QA MCP Server starting...');

    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        terminal: false,
    });

    rl.on('line', async (line) => {
        if (!line.trim()) return;

        try {
            const request = JSON.parse(line);
            console.error(`[Server] Received: ${request.method} (id: ${request.id})`);

            const response = await handleRequest(request);
            sendResponse(response);
        } catch (error) {
            console.error('[Server] Parse error:', error.message);
            sendResponse({
                jsonrpc: '2.0',
                id: null,
                error: {
                    code: ERROR_CODES.PARSE_ERROR,
                    message: 'Failed to parse JSON',
                },
            });
        }
    });

    rl.on('close', async () => {
        console.error('[Server] Input closed, shutting down...');
        await browser.close();
        process.exit(0);
    });

    console.error('[Server] Ready for requests');
}

// Start the server
main().catch((error) => {
    console.error('[Server] Fatal error:', error);
    process.exit(1);
});
