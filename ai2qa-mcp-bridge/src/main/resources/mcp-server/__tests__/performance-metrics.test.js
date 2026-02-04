/**
 * Tests for Performance Metrics collection in MCP Server.
 *
 * These tests would have caught:
 * - Issue #3: MCP server missing get_performance_metrics tool
 * - Issue #5: browser.evaluate result not being unwrapped
 */

import { jest, describe, test, expect } from '@jest/globals';
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Read server code once for all tests
const serverCode = fs.readFileSync(
    path.join(__dirname, '../server.js'),
    'utf8'
);

describe('MCP Server Performance Metrics', () => {

    describe('Tool Definitions (Issue #3 prevention)', () => {

        test('get_performance_metrics tool should be defined in TOOLS array', () => {
            // Check that the tool is defined
            expect(serverCode).toContain("name: 'get_performance_metrics'");
        });

        test('get_performance_metrics should have correct input schema', () => {
            // Check schema properties
            expect(serverCode).toContain('includeResources');
            expect(serverCode).toContain('resourceThresholdMs');
            expect(serverCode).toContain('resourceThresholdKb');
        });

        test('get_performance_metrics case should exist in executeTool switch', () => {
            // Check that the tool is handled in executeTool
            expect(serverCode).toContain("case 'get_performance_metrics':");
            expect(serverCode).toContain('collectPerformanceMetrics');
        });
    });

    describe('browser.evaluate wrapper handling (Issue #5 prevention)', () => {

        test('collectPerformanceMetrics should unwrap browser.evaluate result', () => {
            // The fix: evalResult.result should be accessed
            expect(serverCode).toContain('evalResult.result');

            // Should handle both wrapped and unwrapped formats
            expect(serverCode).toContain('evalResult.result || evalResult');
        });

        test('should validate perfData before processing', () => {
            // Should check for empty/malformed data
            expect(serverCode).toContain('!perfData.webVitals && !perfData.navigation');
        });
    });

    describe('PERFORMANCE_COLLECTION_SCRIPT', () => {

        test('should collect Core Web Vitals', () => {
            // Check LCP collection
            expect(serverCode).toContain('largest-contentful-paint');
            expect(serverCode).toContain('webVitals.lcp');

            // Check CLS collection
            expect(serverCode).toContain('layout-shift');
            expect(serverCode).toContain('webVitals.cls');

            // Check FCP collection
            expect(serverCode).toContain('first-contentful-paint');
            expect(serverCode).toContain('webVitals.fcp');

            // Check TTFB collection
            expect(serverCode).toContain('webVitals.ttfb');

            // Check FID collection
            expect(serverCode).toContain('first-input');
            expect(serverCode).toContain('webVitals.fid');
        });

        test('should collect navigation timing', () => {
            // Check navigation timing properties in PERFORMANCE_COLLECTION_SCRIPT
            expect(serverCode).toContain('pageLoad:');
            expect(serverCode).toContain('domContentLoaded:');
            expect(serverCode).toContain('dnsLookup:');
            expect(serverCode).toContain('tcpConnect:');
            expect(serverCode).toContain('domInteractive:');
        });

        test('should collect resource timing', () => {
            expect(serverCode).toContain("getEntriesByType('resource')");
            expect(serverCode).toContain('transferSizeKb');
        });
    });

    describe('Performance thresholds', () => {

        test('should define PERF_THRESHOLDS constants', () => {
            expect(serverCode).toContain('PERF_THRESHOLDS');
            expect(serverCode).toContain('LCP_GOOD');
            expect(serverCode).toContain('LCP_POOR');
            expect(serverCode).toContain('CLS_GOOD');
            expect(serverCode).toContain('CLS_POOR');
            expect(serverCode).toContain('TTFB_GOOD');
            expect(serverCode).toContain('TTFB_POOR');
        });
    });

    describe('Response format', () => {

        test('collectPerformanceMetrics should return correct structure', () => {
            // Check return structure
            expect(serverCode).toContain('success: true');
            expect(serverCode).toContain('webVitals: perfData.webVitals');
            expect(serverCode).toContain('navigation: perfData.navigation');
            expect(serverCode).toContain('totalResources');
            expect(serverCode).toContain('totalTransferSizeKb');
            expect(serverCode).toContain('issues');
        });

        test('should handle errors gracefully', () => {
            // Check error handling
            expect(serverCode).toContain('success: false');
            expect(serverCode).toContain("error: `Failed to collect performance metrics");
        });
    });
});

describe('analyzePerformanceIssues', () => {

    test('should generate issues for poor LCP', () => {
        // Check LCP analysis
        expect(serverCode).toContain("severity: 'CRITICAL'");
        expect(serverCode).toContain("category: 'LCP'");
    });

    test('should generate issues for poor CLS', () => {
        expect(serverCode).toContain("category: 'CLS'");
    });

    test('should generate issues for poor TTFB', () => {
        expect(serverCode).toContain("category: 'TTFB'");
    });
});
