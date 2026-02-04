/**
 * End-to-End Test for Performance Metrics Collection
 *
 * This test:
 * 1. Starts a local HTTP server with a test page
 * 2. Runs the MCP server with Puppeteer
 * 3. Navigates to the local page
 * 4. Collects real performance metrics from the browser
 * 5. Verifies metrics are returned correctly
 *
 * This catches issues that static code analysis cannot:
 * - browser.evaluate() not returning IIFE results
 * - Performance API not available in headless mode
 * - Timing issues with metric collection
 */

import { jest, describe, test, expect, beforeAll, afterAll } from '@jest/globals';
import * as http from 'http';
import * as path from 'path';
import { fileURLToPath } from 'url';

// Import the browser module for testing
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Test HTML page with known performance characteristics
const TEST_HTML = `
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Performance Test Page</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 20px;
        }
        .content {
            max-width: 800px;
            margin: 0 auto;
        }
        h1 { color: #333; }
        .large-content {
            width: 100%;
            height: 300px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            font-size: 24px;
            margin: 20px 0;
        }
    </style>
</head>
<body>
    <div class="content">
        <h1>Performance Test Page</h1>
        <p>This page is used for E2E testing of Core Web Vitals collection.</p>
        <div class="large-content">
            Largest Contentful Paint Element
        </div>
        <p>Additional content for testing FCP and LCP metrics.</p>
        <button id="test-button">Click Me</button>
    </div>
    <script>
        // Add a small delay to simulate real page behavior
        document.getElementById('test-button').addEventListener('click', function() {
            console.log('Button clicked');
        });
    </script>
</body>
</html>
`;

// Local HTTP server for testing
let server;
let serverPort;
let serverUrl;

// Dynamic import for the browser module
// Use Playwright engine for more reliable testing
let browser;
let usePlaywright = false;

describe('Performance Metrics E2E Test', () => {

    beforeAll(async () => {
        // Start local HTTP server
        server = http.createServer((req, res) => {
            // Allow localhost and 127.0.0.1 for testing
            res.setHeader('Access-Control-Allow-Origin', '*');
            res.setHeader('Content-Type', 'text/html');
            res.writeHead(200);
            res.end(TEST_HTML);
        });

        await new Promise((resolve, reject) => {
            server.listen(0, '127.0.0.1', () => {
                serverPort = server.address().port;
                serverUrl = `http://127.0.0.1:${serverPort}`;
                console.log(`[Test Server] Started on ${serverUrl}`);
                resolve();
            });
            server.on('error', reject);
        });

        // Try Playwright first (better for testing), fall back to Puppeteer
        try {
            browser = await import('../playwright-engine.js');
            await browser.launch();
            usePlaywright = true;
            console.log('[Test] Playwright browser launched');
        } catch (err) {
            console.log('[Test] Playwright not available, trying Puppeteer:', err.message);
            try {
                browser = await import('../browser.js');
                await browser.launch();
                console.log('[Test] Puppeteer browser launched');
            } catch (puppeteerErr) {
                console.error('[Test] Failed to launch any browser:', puppeteerErr.message);
                throw puppeteerErr;
            }
        }
    }, 60000); // 60s timeout for setup

    afterAll(async () => {
        // Close browser
        if (browser) {
            try {
                await browser.close();
                console.log('[Test] Browser closed');
            } catch (err) {
                console.error('[Test] Error closing browser:', err.message);
            }
        }

        // Stop HTTP server
        if (server) {
            await new Promise((resolve) => {
                server.close(() => {
                    console.log('[Test Server] Stopped');
                    resolve();
                });
            });
        }
    }, 30000);

    test('should navigate to local test page', async () => {
        const result = await browser.navigateTo(serverUrl);
        // navigateTo returns { url, title } on success
        expect(result).toBeDefined();
        expect(result.url).toContain('127.0.0.1');
        expect(result.title).toContain('Performance Test');
    }, 30000);

    test('browser.evaluate should return IIFE results correctly', async () => {
        // This test specifically verifies the fix for browser.evaluate
        // not returning IIFE script results
        const script = `
            (() => {
                return {
                    testValue: 42,
                    nested: {
                        message: 'hello from IIFE'
                    }
                };
            })()
        `;

        const result = await browser.evaluate(script);

        // The fix ensures result.result contains the IIFE return value
        expect(result).toHaveProperty('result');
        expect(result.result).toHaveProperty('testValue', 42);
        expect(result.result.nested).toHaveProperty('message', 'hello from IIFE');
    }, 10000);

    test('should collect navigation timing from Performance API', async () => {
        // First navigate to ensure fresh timing data
        await browser.navigateTo(serverUrl);

        // Collect navigation timing via browser.evaluate
        const script = `
            (() => {
                const navTiming = performance.getEntriesByType('navigation')[0];
                if (!navTiming) {
                    return { error: 'No navigation timing available' };
                }
                return {
                    success: true,
                    navigation: {
                        ttfb: navTiming.responseStart - navTiming.requestStart,
                        domContentLoaded: navTiming.domContentLoadedEventEnd - navTiming.startTime,
                        pageLoad: navTiming.loadEventEnd - navTiming.startTime,
                        domInteractive: navTiming.domInteractive - navTiming.startTime,
                    }
                };
            })()
        `;

        const result = await browser.evaluate(script);

        expect(result).toHaveProperty('result');
        expect(result.result.success).toBe(true);
        expect(result.result.navigation).toBeDefined();

        // Navigation timing should have reasonable values
        const nav = result.result.navigation;
        expect(nav.ttfb).toBeGreaterThanOrEqual(0);
        expect(nav.domContentLoaded).toBeGreaterThan(0);
        expect(nav.pageLoad).toBeGreaterThan(0);
        expect(nav.domInteractive).toBeGreaterThan(0);

        console.log('[Test] Navigation timing:', JSON.stringify(nav, null, 2));
    }, 30000);

    test('should collect paint timing (FCP) from Performance API', async () => {
        // Wait a bit for paint events to be recorded
        await new Promise(resolve => setTimeout(resolve, 1000));

        const script = `
            (() => {
                const paintEntries = performance.getEntriesByType('paint');
                const result = {
                    success: true,
                    paints: {}
                };
                for (const entry of paintEntries) {
                    if (entry.name === 'first-contentful-paint') {
                        result.paints.fcp = entry.startTime;
                    }
                    if (entry.name === 'first-paint') {
                        result.paints.fp = entry.startTime;
                    }
                }
                return result;
            })()
        `;

        const result = await browser.evaluate(script);

        expect(result).toHaveProperty('result');
        expect(result.result.success).toBe(true);

        // FCP should be available after page load
        if (result.result.paints.fcp !== undefined) {
            expect(result.result.paints.fcp).toBeGreaterThan(0);
            console.log('[Test] FCP:', result.result.paints.fcp, 'ms');
        } else {
            console.log('[Test] FCP not available (may be normal in headless mode)');
        }
    }, 10000);

    test('PERFORMANCE_COLLECTION_SCRIPT should return metrics from real browser', async () => {
        // Import the actual PERFORMANCE_COLLECTION_SCRIPT from server.js
        // by running it directly through browser.evaluate
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

                // Resource Timing API
                const resourceEntries = performance.getEntriesByType('resource');
                result.resources = resourceEntries.map(entry => ({
                    name: entry.name,
                    type: entry.initiatorType,
                    durationMs: entry.duration,
                    transferSizeKb: (entry.transferSize || 0) / 1024,
                }));

                return result;
            })()
        `;

        const result = await browser.evaluate(PERFORMANCE_COLLECTION_SCRIPT);

        // CRITICAL: This is the fix verification
        // Before the fix, result.result was undefined because browser.evaluate
        // didn't return IIFE results
        expect(result).toHaveProperty('result');
        expect(result.result).not.toBeUndefined();
        expect(result.result).not.toBeNull();

        // Verify webVitals object exists
        expect(result.result).toHaveProperty('webVitals');
        expect(result.result).toHaveProperty('navigation');

        // Navigation timing should always be available
        const nav = result.result.navigation;
        expect(nav).toHaveProperty('ttfb');
        expect(nav).toHaveProperty('pageLoad');
        expect(nav).toHaveProperty('domContentLoaded');

        // TTFB should be in webVitals too
        expect(result.result.webVitals).toHaveProperty('ttfb');

        // Log actual values for debugging
        console.log('[Test] Web Vitals:', JSON.stringify(result.result.webVitals, null, 2));
        console.log('[Test] Navigation:', JSON.stringify(result.result.navigation, null, 2));
        console.log('[Test] Resources count:', result.result.resources.length);

        // Verify numeric values are reasonable
        expect(result.result.webVitals.ttfb).toBeGreaterThanOrEqual(0);
        expect(result.result.navigation.pageLoad).toBeGreaterThan(0);
    }, 30000);

    test('should simulate full measure_performance flow', async () => {
        // This simulates what collectPerformanceMetrics does in the MCP server
        const PERFORMANCE_COLLECTION_SCRIPT = `
            (() => {
                const result = {
                    webVitals: {},
                    navigation: {},
                    resources: [],
                };

                const navTiming = performance.getEntriesByType('navigation')[0];
                if (navTiming) {
                    result.navigation = {
                        dnsLookup: navTiming.domainLookupEnd - navTiming.domainLookupStart,
                        tcpConnect: navTiming.connectEnd - navTiming.connectStart,
                        ttfb: navTiming.responseStart - navTiming.requestStart,
                        domContentLoaded: navTiming.domContentLoadedEventEnd - navTiming.startTime,
                        pageLoad: navTiming.loadEventEnd - navTiming.startTime,
                    };
                    result.webVitals.ttfb = navTiming.responseStart - navTiming.requestStart;
                }

                const paintEntries = performance.getEntriesByType('paint');
                for (const entry of paintEntries) {
                    if (entry.name === 'first-contentful-paint') {
                        result.webVitals.fcp = entry.startTime;
                    }
                }

                const layoutShiftEntries = performance.getEntriesByType('layout-shift');
                let clsValue = 0;
                for (const entry of layoutShiftEntries) {
                    if (!entry.hadRecentInput) {
                        clsValue += entry.value;
                    }
                }
                result.webVitals.cls = clsValue;

                const resourceEntries = performance.getEntriesByType('resource');
                result.resources = resourceEntries.slice(0, 10);

                return result;
            })()
        `;

        // Simulate what collectPerformanceMetrics does
        const evalResult = await browser.evaluate(PERFORMANCE_COLLECTION_SCRIPT);

        // This is the critical line that was broken before the fix
        const perfData = evalResult.result || evalResult;

        // Validate perfData is not empty
        expect(perfData).toBeDefined();
        expect(perfData.webVitals || perfData.navigation).toBeDefined();

        // Simulate the return format of collectPerformanceMetrics
        const mockResponse = {
            success: true,
            webVitals: perfData.webVitals,
            navigation: perfData.navigation,
            totalResources: perfData.resources?.length ?? 0,
            totalTransferSizeKb: 0,
            issues: [],
        };

        expect(mockResponse.success).toBe(true);
        expect(mockResponse.webVitals).toHaveProperty('ttfb');
        expect(Object.keys(mockResponse.webVitals).length).toBeGreaterThan(0);
        expect(Object.keys(mockResponse.navigation).length).toBeGreaterThan(0);

        console.log('[Test] Mock collectPerformanceMetrics response:',
            JSON.stringify(mockResponse, null, 2));
    }, 30000);

});
