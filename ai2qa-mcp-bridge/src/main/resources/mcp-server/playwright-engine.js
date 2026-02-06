/**
 * Browser wrapper using Playwright for accessibility tree snapshots.
 *
 * This engine provides the same API as browser.js (Puppeteer) but uses Playwright
 * for its superior accessibility tree support via ariaSnapshot().
 *
 * Key features:
 * - Accessibility tree snapshots with ref-based element selection
 * - ~95% token savings compared to CSS selector snapshots
 * - Deterministic element interaction via refs
 *
 * Security features (SSRF/Redirect Protection):
 * - Blocks navigation to cloud metadata endpoints (169.254.x.x)
 * - Blocks navigation to internal/private networks
 * - Blocks WebSocket connections to internal hosts
 * - Validates all frame navigations
 */

import { chromium } from 'playwright';
import { existsSync } from 'fs';
import { platform } from 'os';

/**
 * Finds the Chrome executable path based on platform and environment.
 * For Playwright, we prefer using 'channel' for system Chrome.
 */
function findChromeConfig() {
    // 1. Check PUPPETEER_EXECUTABLE_PATH (set in Docker/Cloud Run)
    if (process.env.PUPPETEER_EXECUTABLE_PATH && existsSync(process.env.PUPPETEER_EXECUTABLE_PATH)) {
        console.error(`[Playwright] Using Chrome from PUPPETEER_EXECUTABLE_PATH: ${process.env.PUPPETEER_EXECUTABLE_PATH}`);
        return { executablePath: process.env.PUPPETEER_EXECUTABLE_PATH };
    }

    // 2. Check CHROME_BIN (alternative env var)
    if (process.env.CHROME_BIN && existsSync(process.env.CHROME_BIN)) {
        console.error(`[Playwright] Using Chrome from CHROME_BIN: ${process.env.CHROME_BIN}`);
        return { executablePath: process.env.CHROME_BIN };
    }

    // 3. Try 'chrome' channel to use system Chrome
    const os = platform();
    if (os === 'darwin' || os === 'linux' || os === 'win32') {
        console.error('[Playwright] Using system Chrome via channel');
        return { channel: 'chrome' };
    }

    // 4. Fall back to Playwright's bundled Chromium
    console.error('[Playwright] Using Playwright bundled Chromium');
    return {};
}

// ============================================================================
// SECURITY: Blocked hosts and URL validation
// ============================================================================

// Allow localhost for testing when ALLOW_LOCALHOST=true
const ALLOW_LOCALHOST = process.env.ALLOW_LOCALHOST === 'true' || process.env.NODE_ENV === 'test';

/**
 * Cloud metadata and internal network hosts that must always be blocked.
 * These prevent SSRF attacks that could expose cloud credentials.
 */
const BLOCKED_HOSTS = new Set([
    // AWS/GCP instance metadata (NEVER allow these)
    '169.254.169.254',
    'metadata.google.internal',
    // AWS ECS metadata
    '169.254.169.253',
    '169.254.170.2',
    // Kubernetes internal DNS
    'kubernetes.default.svc',
    'kubernetes.default',
    'kubernetes.default.svc.cluster.local',
    // Localhost variants (conditionally blocked)
    ...(ALLOW_LOCALHOST ? [] : ['localhost', '127.0.0.1', '0.0.0.0']),
]);

/**
 * Ad network domains to block (click fraud prevention).
 * These prevent test traffic from being counted as real user activity.
 */
const AD_NETWORK_DOMAINS = new Set([
    // Google Ads
    'googleads.g.doubleclick.net',
    'pagead2.googlesyndication.com',
    'adservice.google.com',
    'www.googleadservices.com',
    'googleadservices.com',
    'googlesyndication.com',
    'doubleclick.net',
    'googletag.g.doubleclick.net',
    // Facebook/Meta Ads
    'facebook.net',
    'connect.facebook.net',
    'pixel.facebook.com',
    'an.facebook.com',
    // Amazon Ads
    'amazon-adsystem.com',
    'aax.amazon-adsystem.com',
    // Other major ad networks
    'criteo.com',
    'criteo.net',
    'taboola.com',
    'outbrain.com',
    'adsrvr.org',
    'adnxs.com',
    'rubiconproject.com',
    'pubmatic.com',
    'openx.net',
    'casalemedia.com',
    'advertising.com',
    // Analytics (may track clicks)
    'analytics.google.com',
    'google-analytics.com',
    'hotjar.com',
    'fullstory.com',
]);

// Click velocity tracking
let clickCount = 0;
let clickWindowStart = Date.now();
const MAX_CLICKS_PER_TEST = 50;
const MAX_CLICKS_PER_MINUTE = 20;
const CLICK_WINDOW_MS = 60000; // 1 minute

/**
 * Checks if an IP is in a private/internal range.
 */
function isPrivateIp(ip) {
    if (!ip) return false;
    // Link-local (cloud metadata) - ALWAYS block
    if (ip.startsWith('169.254.')) return true;
    // Loopback - allow for testing when ALLOW_LOCALHOST is set
    if (ip.startsWith('127.')) return !ALLOW_LOCALHOST;
    // Private networks
    if (ip.startsWith('10.')) return true;
    if (ip.startsWith('192.168.')) return true;
    // 172.16.0.0/12
    if (ip.startsWith('172.')) {
        const parts = ip.split('.');
        if (parts.length >= 2) {
            const second = parseInt(parts[1], 10);
            if (second >= 16 && second <= 31) return true;
        }
    }
    return false;
}

/**
 * Checks if a host is an ad network domain.
 */
function isAdNetworkDomain(host) {
    if (!host) return false;
    // Direct match
    if (AD_NETWORK_DOMAINS.has(host)) return true;
    // Check if subdomain of ad network
    for (const adDomain of AD_NETWORK_DOMAINS) {
        if (host.endsWith('.' + adDomain)) return true;
    }
    return false;
}

/**
 * Validates if a URL is safe to navigate to.
 * Returns { allowed: boolean, reason?: string }
 */
function validateUrl(urlString) {
    try {
        const url = new URL(urlString);
        const host = url.hostname.toLowerCase();

        // Check against blocklist
        if (BLOCKED_HOSTS.has(host)) {
            return { allowed: false, reason: `Blocked host: ${host}` };
        }

        // Check for private IP ranges
        if (isPrivateIp(host)) {
            return { allowed: false, reason: `Private IP range: ${host}` };
        }

        // Check for link-local prefix (covers 169.254.x.x)
        if (host.startsWith('169.254.')) {
            return { allowed: false, reason: `Link-local address: ${host}` };
        }

        // Check for internal TLDs
        if (host.endsWith('.internal') || host.endsWith('.local') || host.endsWith('.localhost')) {
            return { allowed: false, reason: `Internal TLD: ${host}` };
        }

        // Check for Kubernetes service discovery
        if (host.endsWith('.svc') || host.endsWith('.cluster.local')) {
            return { allowed: false, reason: `Kubernetes internal: ${host}` };
        }

        // Check for ad network domains (click fraud prevention)
        if (isAdNetworkDomain(host)) {
            return { allowed: false, reason: `Ad network blocked: ${host}` };
        }

        return { allowed: true };
    } catch (e) {
        // Invalid URL - allow and let browser handle
        return { allowed: true };
    }
}

/**
 * Sets up navigation guards on a Playwright page.
 * Intercepts and validates all requests and frame navigations.
 */
async function setupNavigationGuards(page) {
    // Intercept all requests and block those to internal/metadata hosts
    await page.route('**/*', async (route) => {
        const request = route.request();
        const url = request.url();

        const validation = validateUrl(url);
        if (!validation.allowed) {
            console.error(`[Security] Blocked request to ${url}: ${validation.reason}`);
            await route.abort('blockedbyclient');
            return;
        }

        await route.continue();
    });

    // Also validate frame navigations (catches JavaScript redirects)
    page.on('framenavigated', (frame) => {
        const url = frame.url();
        const validation = validateUrl(url);
        if (!validation.allowed) {
            console.error(`[Security] WARNING: Frame navigated to blocked URL: ${url} - ${validation.reason}`);
            // Note: Can't abort framenavigated, but request interception should have caught it
        }
    });
}

let browser = null;
let context = null;
let page = null;
let consoleLogs = [];
let pageErrors = [];

/** Global ref map for element resolution */
let globalRefMap = {};

/**
 * Returns and clears captured logs.
 */
export function flushLogs() {
    const logs = {
        console: [...consoleLogs],
        pageErrors: [...pageErrors]
    };
    consoleLogs = [];
    pageErrors = [];
    return logs;
}

/**
 * Launches the browser with Playwright.
 */
export async function launch(options = {}) {
    if (browser) {
        return { browser, page };
    }

    // Find Chrome config (handles macOS, Linux, Docker, etc.)
    const chromeConfig = findChromeConfig();

    console.error('[Playwright] Launching with config:', JSON.stringify(chromeConfig));

    browser = await chromium.launch({
        headless: options.headless ?? true,
        ...chromeConfig, // Apply executablePath or channel
        timeout: 60000, // Increase launch timeout to 60 seconds
        // Increase CDP protocol timeout for heavy pages (default 30s is too short)
        // This affects page.evaluate(), page.waitForSelector(), etc.
        // See: https://playwright.dev/docs/api/class-browsertype#browser-type-launch-option-timeout
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox',
            '--disable-dev-shm-usage',
            '--disable-accelerated-2d-canvas',
            '--disable-gpu',
            // SECURITY: WebRTC blocking to prevent IP leakage
            '--disable-webrtc',
            '--disable-webrtc-hw-decoding',
            '--disable-webrtc-hw-encoding',
            '--force-webrtc-ip-handling-policy=disable_non_proxied_udp',
            // SECURITY: Additional hardening
            '--disable-background-networking',
            '--disable-extensions',
            '--disable-sync',
            // Note: Removed --remote-debugging-address as it may interfere with connection
        ],
    });

    // Set default timeout for all operations (navigate, evaluate, etc.)
    // This is separate from protocolTimeout and affects higher-level APIs
    browser.contexts().forEach(ctx => {
        ctx.setDefaultTimeout(60000);
    });

    console.error('[Playwright] Launched browser process');
    return { browser };
}

/**
 * Creates a new isolated browser context (Clean Room).
 */
export async function createContext(options = {}) {
    if (!browser) {
        await launch();
    }

    // Close existing context if any
    if (context) {
        await closeContext();
    }

    context = await browser.newContext({
        viewport: { width: 1920, height: 1080 },
        userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        ignoreHTTPSErrors: true,
    });

    page = await context.newPage();

    // CRITICAL: Set default timeout for ALL Playwright operations to prevent hangs
    // This covers screenshot, click, fill, waitForSelector, and any other operation
    // Default is 60 seconds - prevents Chrome from hanging forever while allowing slow pages
    page.setDefaultTimeout(60000);

    // SECURITY: Set up navigation guards to block SSRF/redirect attacks
    await setupNavigationGuards(page);

    // Reset logs, refMap, and click tracking for new context
    consoleLogs = [];
    pageErrors = [];
    globalRefMap = {};

    // SECURITY: Reset click velocity tracking for new test session
    clickCount = 0;
    clickWindowStart = Date.now();

    // CRITICAL: Auto-dismiss JavaScript dialogs (alert, confirm, prompt)
    // Without this handler, dialogs block all page interactions and screenshots show white/blocked state.
    // This is especially important for HACKER persona which may trigger XSS alerts.
    page.on('dialog', async dialog => {
        const dialogType = dialog.type();
        const dialogMessage = dialog.message();
        const truncatedMessage = dialogMessage.length > 200 ? dialogMessage.substring(0, 200) + '...' : dialogMessage;
        console.error(`[Playwright] Auto-dismissing ${dialogType} dialog: "${truncatedMessage}"`);

        // Log as potential security finding (XSS, etc.)
        consoleLogs.push(`[SECURITY] ${dialogType.toUpperCase()} dialog triggered: "${truncatedMessage}"`);

        // Accept all dialogs to unblock the page
        // For confirm dialogs, accept means "OK", for prompts, accept with empty string
        await dialog.accept();
    });

    // Listen for console errors
    page.on('console', msg => {
        if (msg.type() === 'error') {
            const text = msg.text();
            const truncated = text.length > 1000 ? text.substring(0, 1000) + '...' : text;
            consoleLogs.push(`[JS Console] ${truncated}`);
        }
    });

    // Listen for page errors
    page.on('pageerror', error => {
        const text = error.message || String(error);
        const truncated = text.length > 1000 ? text.substring(0, 1000) + '...' : text;
        pageErrors.push(`[Uncaught Exception] ${truncated}`);
    });

    console.error(`[Playwright] Created new context (Run ID: ${options.runId || 'N/A'})`);
    return { context, page };
}

/**
 * Closes the current browser context.
 */
export async function closeContext() {
    // Always clear state first to avoid repeated close attempts
    const pageToClose = page;
    const contextToClose = context;
    page = null;
    context = null;
    globalRefMap = {};

    if (pageToClose) {
        try {
            await pageToClose.close().catch(() => {});
        } catch (e) { /* ignore - page may already be closed */ }
    }

    if (contextToClose) {
        try {
            await contextToClose.close().catch(() => {});
            console.error('[Playwright] Context closed');
        } catch (e) {
            console.error('[Playwright] Context was already closed or crashed');
        }
    } else {
        console.error('[Playwright] closeContext called but context was already null');
    }

    return { success: true };
}

/**
 * Navigates to a URL.
 */
export async function navigateTo(url, options = {}) {
    const p = await ensurePage();

    const timeout = options.timeout ?? 60000;  // 60 seconds for slow-loading pages
    // Use 'load' instead of 'networkidle' - networkidle times out on sites with
    // continuous background requests (analytics, WebSockets, chat widgets, etc.)
    const waitUntil = options.waitUntil ?? 'load';

    await p.goto(url, { waitUntil, timeout });

    // Clear refMap on navigation (refs are page-specific)
    globalRefMap = {};

    return {
        url: p.url(),
        title: await p.title(),
    };
}

/**
 * Clicks on an element by selector or ref.
 *
 * SECURITY: Click velocity limits prevent click fraud.
 * - MAX_CLICKS_PER_TEST: Absolute limit per test session
 * - MAX_CLICKS_PER_MINUTE: Rate limit to prevent rapid clicking
 */
export async function click(selectorOrRef, options = {}) {
    const p = await ensurePage();

    // SECURITY: Check click velocity limits
    const now = Date.now();

    // Check absolute limit per test
    if (clickCount >= MAX_CLICKS_PER_TEST) {
        throw new Error(`Click limit exceeded: Maximum ${MAX_CLICKS_PER_TEST} clicks per test`);
    }

    // Check rate limit (clicks per minute)
    if (now - clickWindowStart > CLICK_WINDOW_MS) {
        // Window expired, reset
        clickWindowStart = now;
        clickCount = 0;
    } else if (clickCount >= MAX_CLICKS_PER_MINUTE) {
        const remainingMs = CLICK_WINDOW_MS - (now - clickWindowStart);
        throw new Error(`Click rate limit exceeded: Maximum ${MAX_CLICKS_PER_MINUTE} clicks per minute. Try again in ${Math.ceil(remainingMs / 1000)} seconds`);
    }

    const timeout = options.timeout ?? 10000;

    // CONSENT_FALLBACK: special selector for consent buttons when AI returns NOT_FOUND
    // Format: "CONSENT_FALLBACK:buttonText"
    if (selectorOrRef && selectorOrRef.startsWith('CONSENT_FALLBACK:')) {
        const targetText = selectorOrRef.substring('CONSENT_FALLBACK:'.length);
        console.error(`[Playwright] Using CONSENT_FALLBACK for: ${targetText}`);
        const clicked = await tryClickConsentButtonByText(p, targetText);
        if (clicked) {
            clickCount++;
            return { success: true };
        }
        throw new Error(`Consent button not found for: ${targetText}`);
    }

    // Try main frame first
    let clicked = false;
    try {
        const locator = resolveLocator(p, selectorOrRef);
        const count = await locator.count();
        if (count > 0) {
            await locator.click({ timeout: Math.min(timeout, 3000) });
            clicked = true;
        }
    } catch (mainFrameError) {
        console.error(`[Playwright] Selector not found in main frame, searching iframes: ${selectorOrRef}`);
    }

    // If not found in main frame, search iframes (for consent popups like SourcePoint)
    if (!clicked) {
        clicked = await clickInIframes(p, selectorOrRef, Math.min(timeout, 3000));
    }

    // Fallback: try clicking consent button by text
    if (!clicked) {
        console.error('[Playwright] Trying fallback: finding consent button by text');
        clicked = await tryClickConsentButtonByText(p, selectorOrRef);
    }

    if (!clicked) {
        throw new Error(`Element not found: ${selectorOrRef}`);
    }

    clickCount++;
    return { success: true };
}

/**
 * Try to click an element within iframes.
 */
async function clickInIframes(page, selector, timeout) {
    // Common iframe selectors for consent CMPs
    const iframeSelectors = [
        'iframe[src*="sourcepoint"]',
        'iframe[src*="sp-prod"]',
        'iframe[src*="privacy-mgmt"]',
        'iframe[id*="sp_message"]',
        'iframe[src*="onetrust"]',
        'iframe[src*="cookiebot"]',
        'iframe[src*="consent"]',
        'iframe[src*="truste"]',
        'iframe',  // Last resort: try all iframes
    ];

    for (const iframeSelector of iframeSelectors) {
        try {
            const iframes = await page.locator(iframeSelector).all();

            for (const iframe of iframes) {
                try {
                    const frame = await iframe.contentFrame();
                    if (!frame) continue;

                    const locator = frame.locator(selector);
                    const count = await locator.count().catch(() => 0);

                    if (count > 0) {
                        console.error(`[Playwright] Found selector in iframe: ${iframeSelector}`);
                        await locator.click({ timeout });
                        return true;
                    }
                } catch (frameError) {
                    // Not in this frame, continue
                }
            }
        } catch (e) {
            // Selector didn't match any iframes
        }
    }

    return false;
}

/**
 * Fallback: Try to click a consent button by its text content.
 */
async function tryClickConsentButtonByText(page, originalSelector) {
    // Extract potential button text from selector
    const textMatches = originalSelector.match(/['"]([^'"]+)['"]/);
    const targetText = textMatches ? textMatches[1].toLowerCase() : '';

    const consentKeywords = ['agree', 'accept', 'i agree', 'i accept', 'accept all', 'consent', 'ok', 'got it'];

    // Try main page
    try {
        const buttons = await page.locator('button, [role="button"], a[class*="btn"]').all();

        for (const btn of buttons) {
            try {
                const text = ((await btn.innerText()) || (await btn.getAttribute('aria-label')) || '').trim().toLowerCase();
                const isVisible = await btn.isVisible().catch(() => false);

                if (text && isVisible) {
                    const isMatch = (targetText && text.includes(targetText)) ||
                                   consentKeywords.some(k => text.includes(k));
                    if (isMatch) {
                        await btn.click();
                        console.error('[Playwright] Clicked consent button via text fallback');
                        return true;
                    }
                }
            } catch (e) {
                // Continue
            }
        }
    } catch (e) {
        // Continue to iframes
    }

    // Try iframes
    const iframeSelectors = ['iframe[src*="consent"]', 'iframe[src*="sp"]', 'iframe'];
    for (const iframeSelector of iframeSelectors) {
        try {
            const iframes = await page.locator(iframeSelector).all();

            for (const iframe of iframes) {
                try {
                    const frame = await iframe.contentFrame();
                    if (!frame) continue;

                    const buttons = await frame.locator('button, [role="button"], a[class*="btn"]').all();

                    for (const btn of buttons) {
                        try {
                            const text = ((await btn.innerText()) || (await btn.getAttribute('aria-label')) || '').trim().toLowerCase();
                            const isVisible = await btn.isVisible().catch(() => false);

                            if (text && isVisible) {
                                const isMatch = (targetText && text.includes(targetText)) ||
                                               consentKeywords.some(k => text.includes(k));
                                if (isMatch) {
                                    await btn.click();
                                    console.error(`[Playwright] Clicked consent button in iframe via text fallback`);
                                    return true;
                                }
                            }
                        } catch (e) {
                            // Continue
                        }
                    }
                } catch (e) {
                    // Frame might be detached
                }
            }
        } catch (e) {
            // Continue
        }
    }

    return false;
}

/**
 * Clicks on an element by ref.
 *
 * SECURITY: Click velocity limits prevent click fraud.
 */
export async function clickByRef(ref, options = {}) {
    const p = await ensurePage();

    // SECURITY: Check click velocity limits
    const now = Date.now();

    // Check absolute limit per test
    if (clickCount >= MAX_CLICKS_PER_TEST) {
        throw new Error(`Click limit exceeded: Maximum ${MAX_CLICKS_PER_TEST} clicks per test`);
    }

    // Check rate limit (clicks per minute)
    if (now - clickWindowStart > CLICK_WINDOW_MS) {
        // Window expired, reset
        clickWindowStart = now;
        clickCount = 0;
    } else if (clickCount >= MAX_CLICKS_PER_MINUTE) {
        const remainingMs = CLICK_WINDOW_MS - (now - clickWindowStart);
        throw new Error(`Click rate limit exceeded: Maximum ${MAX_CLICKS_PER_MINUTE} clicks per minute. Try again in ${Math.ceil(remainingMs / 1000)} seconds`);
    }

    const locator = resolveRef(p, ref);
    await locator.click({ timeout: options.timeout ?? 10000 });

    clickCount++;

    return { success: true };
}

/**
 * Types text into an element.
 */
export async function type(selectorOrRef, text, options = {}) {
    const p = await ensurePage();
    const timeout = options.timeout ?? 10000;
    const delay = options.delay ?? 50;

    const locator = resolveLocator(p, selectorOrRef);

    // Clear existing content if specified
    if (options.clear) {
        await locator.clear({ timeout });
    }

    await locator.fill(text, { timeout });

    return { success: true };
}

/**
 * Fills an element by ref.
 */
export async function fillByRef(ref, value, options = {}) {
    const p = await ensurePage();
    const locator = resolveRef(p, ref);

    if (options.clear) {
        await locator.clear({ timeout: options.timeout ?? 10000 });
    }

    await locator.fill(value, { timeout: options.timeout ?? 10000 });
    return { success: true };
}

/**
 * Takes a screenshot.
 */
export async function screenshot(options = {}) {
    const p = await ensurePage();

    const screenshotOptions = {
        type: options.format ?? 'png',
        fullPage: options.fullPage ?? false,
    };

    if (options.quality && screenshotOptions.type !== 'png') {
        screenshotOptions.quality = options.quality;
    }

    let buffer;
    if (options.selector) {
        const element = p.locator(options.selector);
        buffer = await element.screenshot(screenshotOptions);
    } else {
        buffer = await p.screenshot(screenshotOptions);
    }

    return {
        data: buffer.toString('base64'),
        mimeType: `image/${screenshotOptions.type}`,
    };
}

/**
 * Common consent/cookie iframe selectors to check.
 * These CMPs (Consent Management Platforms) often use iframes.
 */
const CONSENT_IFRAME_SELECTORS = [
    // SourcePoint (used by CNN and many news sites)
    'iframe[src*="sourcepoint"]',
    'iframe[src*="sp-prod"]',
    'iframe[src*="privacy-mgmt"]',
    'iframe[src*="sp_message"]',
    'iframe[id*="sp_message"]',
    'iframe[class*="sp_message"]',
    'iframe[src*="cmp."]',
    'iframe[src*="tcfapi"]',
    // OneTrust
    'iframe[id*="onetrust"]',
    'iframe[src*="onetrust"]',
    'iframe[title*="cookie"]',
    'iframe[title*="consent"]',
    'iframe[title*="privacy"]',
    // CookieBot
    'iframe[src*="cookiebot"]',
    'iframe[id*="cookiebot"]',
    // TrustArc
    'iframe[src*="truste"]',
    'iframe[src*="trustarc"]',
    // CookieYes
    'iframe[src*="cookieyes"]',
    // Quantcast
    'iframe[src*="quantcast"]',
    // Generic consent/privacy iframes
    'iframe[src*="consent"]',
    'iframe[src*="gdpr"]',
    'iframe[src*="ccpa"]',
    // SP (SourcePoint)
    'iframe[src*="sourcepoint"]',
    'iframe[id*="sp_message"]',
];

/**
 * Gets the accessibility tree snapshot with refs.
 * This is the key feature that saves ~95% tokens.
 *
 * IMPORTANT: Also captures iframe content, especially consent popups.
 */
export async function ariaSnapshot(options = {}) {
    const p = await ensurePage();

    // Get ARIA snapshot from Playwright
    const locator = options.selector ? p.locator(options.selector) : p.locator(':root');

    let ariaTree;
    try {
        ariaTree = await locator.ariaSnapshot();
    } catch (e) {
        console.error('[Playwright] ariaSnapshot failed:', e.message);
        return {
            tree: '(error: could not generate accessibility tree)',
            refs: {},
            url: p.url(),
            title: await p.title(),
        };
    }

    if (!ariaTree) {
        ariaTree = '(empty)';
    }

    // Check for consent/cookie iframes and append their content
    let iframeContent = '';
    try {
        iframeContent = await captureConsentIframes(p);
    } catch (e) {
        console.error('[Playwright] Consent iframe capture failed:', e.message);
    }

    // Combine main page tree with iframe content
    const combinedTree = iframeContent
        ? `${ariaTree}\n\n--- IFRAME CONTENT (consent/cookie popup) ---\n${iframeContent}`
        : ariaTree;

    // Process the tree and generate refs
    const { tree, refs } = processAriaTree(combinedTree, options);

    // Store refs globally for later resolution
    globalRefMap = refs;

    return {
        tree,
        refs,
        url: p.url(),
        title: await p.title(),
    };
}

/**
 * Captures accessibility tree content from consent/cookie iframes.
 * Returns empty string if no consent iframes found.
 */
async function captureConsentIframes(page) {
    const iframeContents = [];

    for (const selector of CONSENT_IFRAME_SELECTORS) {
        try {
            const frameLocator = page.frameLocator(selector);
            // Check if iframe exists by trying to get its content
            const exists = await frameLocator.locator(':root').count().catch(() => 0);

            if (exists > 0) {
                try {
                    const iframeTree = await frameLocator.locator(':root').ariaSnapshot();
                    if (iframeTree && iframeTree.trim() && !iframeTree.includes('(empty)')) {
                        console.error(`[Playwright] Found consent iframe: ${selector}`);
                        iframeContents.push(iframeTree);
                    }
                } catch (e) {
                    // Ignore errors for individual iframes
                }
            }
        } catch (e) {
            // Selector didn't match, continue to next
        }
    }

    // Check for common CMP (Consent Management Platform) containers by ID/class
    const cmpSelectors = [
        // OneTrust (very common, used by CNN, many news sites)
        '#onetrust-consent-sdk',
        '#onetrust-banner-sdk',
        '[class*="onetrust"]',
        // SourcePoint
        '[id^="sp_message_container"]',
        '[class*="sp_message"]',
        'div[data-sp-message]',
        // TrustArc
        '#truste-consent-track',
        '[class*="truste"]',
        // Quantcast
        '#qc-cmp2-container',
        '.qc-cmp2-container',
        // CookieBot
        '#CybotCookiebotDialog',
        // Generic consent/cookie containers
        '[class*="consent-banner"]',
        '[class*="cookie-banner"]',
        '[class*="privacy-banner"]',
        '[class*="gdpr-banner"]',
        '[id*="consent-banner"]',
        '[id*="cookie-banner"]',
        // ARIA dialogs
        'div[role="dialog"]',
        'div[role="alertdialog"]',
        '[aria-modal="true"]',
    ];

    for (const selector of cmpSelectors) {
        try {
            const locator = page.locator(selector);
            const count = await locator.count().catch(() => 0);

            for (let i = 0; i < Math.min(count, 2); i++) {
                try {
                    const element = locator.nth(i);
                    const isVisible = await element.isVisible().catch(() => false);
                    if (isVisible) {
                        const elementTree = await element.ariaSnapshot();
                        if (elementTree && elementTree.trim()) {
                            const lowerTree = elementTree.toLowerCase();
                            // Check if it contains consent-related keywords
                            if (lowerTree.includes('agree') ||
                                lowerTree.includes('accept') ||
                                lowerTree.includes('consent') ||
                                lowerTree.includes('cookie') ||
                                lowerTree.includes('privacy') ||
                                lowerTree.includes('reject')) {
                                console.error(`[Playwright] Found consent element: ${selector}`);
                                iframeContents.push(elementTree);
                            }
                        }
                    }
                } catch (e) {
                    // Ignore individual element errors
                }
            }
        } catch (e) {
            // Selector didn't match, continue
        }
    }

    // AGGRESSIVE SCAN: Look for ANY visible button with consent-related text anywhere on the page
    // This catches consent popups that don't match specific selectors
    try {
        const aggressiveContent = await page.evaluate(() => {
            const consentKeywords = ['agree', 'accept', 'i agree', 'i accept', 'accept all', 'agree all', 'consent', 'got it', 'ok', 'continue', 'dismiss'];
            const allButtons = document.querySelectorAll('button, [role="button"], a[class*="btn"], span[class*="btn"], div[class*="btn"]');
            const foundButtons = [];

            for (const btn of allButtons) {
                try {
                    const text = (btn.innerText || btn.getAttribute('aria-label') || '').trim().toLowerCase();
                    const style = window.getComputedStyle(btn);

                    // Check if visible
                    if (text && style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0') {
                        const isConsentButton = consentKeywords.some(keyword => text.includes(keyword));
                        if (isConsentButton) {
                            const id = btn.id;
                            const className = btn.className;
                            const originalText = (btn.innerText || btn.getAttribute('aria-label') || '').trim();
                            const tagName = btn.tagName.toLowerCase();

                            // Create ARIA-like format
                            let ariaRole = btn.getAttribute('role') || tagName;
                            if (tagName === 'button' || className.includes('btn')) ariaRole = 'button';

                            const idInfo = id ? ` [id="${id}"]` : '';
                            const classInfo = className ? ` [class="${String(className).slice(0, 50)}"]` : '';

                            foundButtons.push(`- ${ariaRole} "${originalText.slice(0, 50)}"${idInfo}${classInfo}`);
                        }
                    }
                } catch (e) {
                    // Ignore errors for individual buttons
                }
            }

            return foundButtons.length > 0 ? `[CONSENT BUTTONS FOUND ON PAGE]\n${foundButtons.join('\n')}` : '';
        });

        if (aggressiveContent && aggressiveContent.trim()) {
            console.error('[Playwright] Aggressive scan found consent buttons');
            iframeContents.push(aggressiveContent);
        }
    } catch (e) {
        console.error('[Playwright] Aggressive consent scan failed:', e.message);
    }

    return iframeContents.join('\n\n');
}

/**
 * Gets the DOM snapshot (legacy CSS selector mode for fallback).
 */
export async function snapshot(options = {}) {
    // If aria mode is requested, use ariaSnapshot
    if (options.mode === 'aria') {
        return ariaSnapshot(options);
    }

    // Legacy CSS selector snapshot (same as browser.js)
    const p = await ensurePage();

    const nodes = await p.evaluate((verbose) => {
        // COST OPTIMIZATION: Reduced from 500 to 300 to minimize token usage
        const MAX_NODES = 300;
        const elements = [];

        const escapeValue = (value) => {
            if (!value) return '';
            return value.replace(/"/g, '\\"');
        };

        // COST OPTIMIZATION: Skip hidden elements
        const isVisible = (el) => {
            try {
                const style = window.getComputedStyle(el);
                if (style.display === 'none') return false;
                if (style.visibility === 'hidden') return false;
                if (style.opacity === '0') return false;
                const rect = el.getBoundingClientRect();
                if (rect.width === 0 && rect.height === 0) return false;
                return true;
            } catch (e) {
                return true;
            }
        };

        // COST OPTIMIZATION: Skip non-content elements
        const shouldSkipElement = (el) => {
            const tag = el.tagName.toLowerCase();
            if (['script', 'style', 'noscript', 'template', 'path', 'defs', 'clippath'].includes(tag)) {
                return true;
            }
            let parent = el.parentElement;
            while (parent) {
                const parentTag = parent.tagName.toLowerCase();
                if (['script', 'style', 'noscript'].includes(parentTag)) return true;
                parent = parent.parentElement;
            }
            return false;
        };

        const isInteractive = (el) => {
            const tag = el.tagName.toLowerCase();
            if (['button', 'a', 'input', 'select', 'textarea', 'option'].includes(tag)) {
                return true;
            }
            if (el.getAttribute('role')) {
                return true;
            }
            if (el.tabIndex >= 0) {
                return true;
            }
            if (typeof el.onclick === 'function') {
                return true;
            }
            return false;
        };

        const getName = (el) => {
            const ariaLabel = el.getAttribute('aria-label');
            if (ariaLabel) return ariaLabel.trim();
            const placeholder = el.getAttribute('placeholder');
            if (placeholder) return placeholder.trim();
            const alt = el.getAttribute('alt');
            if (alt) return alt.trim();
            const text = (el.innerText || '').trim();
            if (text) return text.slice(0, 80);
            return '';
        };

        const uniqueSelector = (el) => {
            const tag = el.tagName.toLowerCase();
            const id = el.getAttribute('id');
            if (id) {
                const selector = `#${CSS.escape(id)}`;
                if (document.querySelectorAll(selector).length === 1) {
                    return selector;
                }
            }

            const testId = el.getAttribute('data-testid');
            if (testId) {
                const selector = `[data-testid="${escapeValue(testId)}"]`;
                if (document.querySelectorAll(selector).length === 1) {
                    return selector;
                }
            }

            const name = el.getAttribute('name');
            if (name) {
                const selector = `${tag}[name="${escapeValue(name)}"]`;
                if (document.querySelectorAll(selector).length === 1) {
                    return selector;
                }
            }

            const ariaLabel = el.getAttribute('aria-label');
            if (ariaLabel) {
                const selector = `${tag}[aria-label="${escapeValue(ariaLabel)}"]`;
                if (document.querySelectorAll(selector).length === 1) {
                    return selector;
                }
            }

            const parts = [];
            let current = el;
            while (current && current.nodeType === 1 && current.tagName.toLowerCase() !== 'html') {
                let part = current.tagName.toLowerCase();
                const parent = current.parentElement;
                if (parent) {
                    const siblings = Array.from(parent.children)
                        .filter((sibling) => sibling.tagName === current.tagName);
                    if (siblings.length > 1) {
                        const index = siblings.indexOf(current) + 1;
                        part += `:nth-of-type(${index})`;
                    }
                }
                parts.unshift(part);
                current = parent;
            }
            return parts.join(' > ');
        };

        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT);
        let current = walker.currentNode;

        while (current && elements.length < MAX_NODES) {
            const el = current;

            // COST OPTIMIZATION: Skip hidden and non-content elements
            if (shouldSkipElement(el)) {
                current = walker.nextNode();
                continue;
            }
            if (!verbose && !isVisible(el)) {
                current = walker.nextNode();
                continue;
            }

            const name = getName(el);
            const include = verbose || isInteractive(el) || name.length > 0;
            if (include) {
                const role = el.getAttribute('role') || el.tagName.toLowerCase();
                const selector = uniqueSelector(el);
                const depth = (() => {
                    let count = 0;
                    let parent = el.parentElement;
                    while (parent) {
                        count += 1;
                        parent = parent.parentElement;
                    }
                    return count;
                })();
                elements.push({
                    role,
                    name,
                    selector,
                    value: el.value ? String(el.value) : '',
                    depth,
                });
            }
            current = walker.nextNode();
        }

        return elements;
    }, Boolean(options.verbose));

    const lines = nodes.map((node, index) => {
        const indent = '  '.repeat(node.depth);
        const name = node.name ? `"${node.name}"` : '';
        const role = node.role || 'element';
        const selector = node.selector ? ` selector="${node.selector}"` : '';
        const value = node.value ? ` value="${node.value}"` : '';
        return `${indent}[s${index}] ${role} ${name}${selector}${value}`;
    });

    return {
        text: lines.join('\n'),
        url: p.url(),
        title: await p.title(),
    };
}

/**
 * Hovers over an element.
 */
export async function hover(selector, options = {}) {
    const p = await ensurePage();
    const timeout = options.timeout ?? 10000;

    const locator = resolveLocator(p, selector);
    await locator.hover({ timeout });

    return { success: true };
}

/**
 * Presses a key or key combination.
 */
export async function pressKey(key) {
    const p = await ensurePage();

    // Playwright uses different key names for some keys
    const keyMap = {
        'Control': 'Control',
        'Shift': 'Shift',
        'Alt': 'Alt',
        'Meta': 'Meta',
    };

    await p.keyboard.press(key);

    return { success: true };
}

/**
 * Waits for an element or condition.
 */
export async function waitFor(options = {}) {
    try {
        const p = await ensurePage();
        const timeout = options.timeout ?? 60000;  // 60 seconds for slow pages

        if (options.selector) {
            console.error(`[Playwright] Waiting for selector: ${options.selector}`);
            await p.waitForSelector(options.selector, { timeout });
        } else if (options.text) {
            console.error(`[Playwright] Waiting for text: ${options.text}`);
            await p.waitForFunction(
                (text) => document.body.innerText.includes(text),
                options.text,
                { timeout }
            );
        } else if (options.navigation) {
            console.error('[Playwright] Waiting for navigation');
            await p.waitForNavigation({ timeout });
        } else if (options.ms) {
            console.error(`[Playwright] Waiting for ${options.ms}ms`);
            await p.waitForTimeout(options.ms);
        } else {
            // No specific wait condition - just ensure page is ready
            console.error('[Playwright] waitFor called with no specific condition, waiting for load state');
            await p.waitForLoadState('domcontentloaded', { timeout: Math.min(timeout, 10000) });
        }

        return { success: true };
    } catch (e) {
        console.error(`[Playwright] waitFor failed: ${e.message}`);
        throw e;
    }
}

/**
 * Evaluates JavaScript in the page context.
 */
export async function evaluate(script, args = []) {
    const p = await ensurePage();

    // Try expression-style first (handles IIFEs and simple expressions),
    // fall back to statement-style for scripts with const/let/var declarations.
    let fn;
    try {
        fn = new Function('args', 'return (' + script + ')');
    } catch {
        // Script contains statements (const, let, var, etc.) - wrap as function body
        fn = new Function('args', script);
    }
    const result = await p.evaluate(fn, args);

    return { result };
}

/**
 * Gets the current page URL and title.
 */
export async function getPageInfo() {
    const p = await ensurePage();

    return {
        url: p.url(),
        title: await p.title(),
    };
}

/**
 * Gets the current ref map.
 */
export function getRefMap() {
    return { ...globalRefMap };
}

/**
 * Clears the ref map (e.g., after navigation).
 */
export function clearRefMap() {
    globalRefMap = {};
}

/**
 * Closes the browser.
 */
export async function close() {
    if (browser) {
        await browser.close();
        browser = null;
        context = null;
        page = null;
        globalRefMap = {};
        console.error('[Playwright] Closed');
    }
    return { success: true };
}

// ============================================================================
// Internal helpers
// ============================================================================

/**
 * Ensures page is ready.
 */
async function ensurePage() {
    if (!browser) {
        await launch();
    }
    if (!context || !page) {
        await createContext();
    }
    return page;
}

/**
 * Resolves a selector or ref to a Playwright locator.
 */
function resolveLocator(page, selectorOrRef) {
    // Check if it's a ref (starts with @ or matches e\d+ pattern)
    if (selectorOrRef.startsWith('@')) {
        return resolveRef(page, selectorOrRef);
    }
    if (/^e\d+$/.test(selectorOrRef)) {
        return resolveRef(page, selectorOrRef);
    }

    // Otherwise treat as CSS selector
    return page.locator(selectorOrRef);
}

/**
 * Resolves a ref to a Playwright locator using the cached refMap.
 */
function resolveRef(page, ref) {
    const refId = ref.replace('@', '');
    const refData = globalRefMap[refId];

    if (!refData) {
        throw new Error(`Ref ${ref} not found in current refMap. Available refs: ${Object.keys(globalRefMap).join(', ') || 'none'}`);
    }

    // Build locator from role and name
    const options = {};
    if (refData.name) {
        options.name = refData.name;
        options.exact = true;
    }

    let locator = page.getByRole(refData.role, options);

    // Handle nth disambiguation for duplicates
    if (refData.nth !== undefined && refData.nth > 0) {
        locator = locator.nth(refData.nth);
    }

    return locator;
}

// ============================================================================
// ARIA Tree Processing (ported from agent-browser)
// ============================================================================

/**
 * Interactive roles that get refs.
 * COST OPTIMIZATION: Only these roles are assigned refs to minimize token usage.
 */
const INTERACTIVE_ROLES = new Set([
    'button',
    'link',
    'textbox',
    'checkbox',
    'radio',
    'combobox',
    'listbox',
    'menuitem',
    'menuitemcheckbox',
    'menuitemradio',
    'option',
    'searchbox',
    'slider',
    'spinbutton',
    'switch',
    'tab',
    'treeitem',
]);

/**
 * Content roles that get refs when named
 */
const CONTENT_ROLES = new Set([
    'heading',
    'cell',
    'gridcell',
    'columnheader',
    'rowheader',
    'listitem',
    'article',
    'region',
    'main',
    'navigation',
]);

let refCounter = 0;

/**
 * Process ARIA tree and generate refs
 */
function processAriaTree(ariaTree, options = {}) {
    refCounter = 0;
    const refs = {};
    const lines = ariaTree.split('\n');
    const result = [];

    // Track role+name for nth disambiguation
    const roleNameCounts = new Map();

    function getKey(role, name) {
        return `${role}:${name ?? ''}`;
    }

    // First pass: count occurrences
    for (const line of lines) {
        const match = line.match(/^(\s*-\s*)(\w+)(?:\s+"([^"]*)")?(.*)$/);
        if (!match) continue;

        const [, , role, name] = match;
        const roleLower = role.toLowerCase();

        if (INTERACTIVE_ROLES.has(roleLower) || (CONTENT_ROLES.has(roleLower) && name)) {
            const key = getKey(roleLower, name);
            roleNameCounts.set(key, (roleNameCounts.get(key) ?? 0) + 1);
        }
    }

    // Second pass: generate refs with nth when needed
    const currentCounts = new Map();

    for (const line of lines) {
        const match = line.match(/^(\s*-\s*)(\w+)(?:\s+"([^"]*)")?(.*)$/);
        if (!match) {
            // Keep non-matching lines (metadata, text content)
            if (!options.interactive) {
                result.push(line);
            }
            continue;
        }

        const [, prefix, role, name, suffix] = match;
        const roleLower = role.toLowerCase();

        const isInteractive = INTERACTIVE_ROLES.has(roleLower);
        const isNamedContent = CONTENT_ROLES.has(roleLower) && name;

        // In interactive-only mode, skip non-interactive elements
        if (options.interactive && !isInteractive) {
            continue;
        }

        if (isInteractive || isNamedContent) {
            const ref = `e${++refCounter}`;
            const key = getKey(roleLower, name);

            // Get nth index for this element
            const currentIndex = currentCounts.get(key) ?? 0;
            currentCounts.set(key, currentIndex + 1);

            // Only store nth if there are duplicates
            const hasDuplicates = (roleNameCounts.get(key) ?? 0) > 1;

            refs[ref] = {
                role: roleLower,
                name,
                ...(hasDuplicates ? { nth: currentIndex } : {}),
            };

            // Build enhanced line
            let enhanced = `${prefix}${role}`;
            if (name) enhanced += ` "${name}"`;
            enhanced += ` [ref=${ref}]`;
            if (hasDuplicates && currentIndex > 0) {
                enhanced += ` [nth=${currentIndex}]`;
            }
            if (suffix && suffix.includes('[')) {
                enhanced += suffix;
            }

            result.push(enhanced);
        } else if (!options.interactive) {
            // Keep structural elements in non-interactive mode
            result.push(line);
        }
    }

    return {
        tree: result.join('\n') || (options.interactive ? '(no interactive elements)' : '(empty)'),
        refs,
    };
}

// Handle process termination
process.on('SIGINT', async () => {
    await close();
    process.exit(0);
});

process.on('SIGTERM', async () => {
    await close();
    process.exit(0);
});
