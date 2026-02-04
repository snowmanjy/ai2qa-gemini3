/**
 * Browser wrapper using Puppeteer with Stealth plugin.
 *
 * Provides anti-detection capabilities for automated browser testing.
 *
 * Snapshot modes:
 * - 'legacy' (default): CSS selector-based snapshots (Puppeteer)
 * - 'aria': Accessibility tree snapshots (requires playwright-engine.js)
 *
 * Note: For aria mode, use playwright-engine.js instead as Puppeteer
 * doesn't have native ariaSnapshot() support.
 *
 * Security features (SSRF/Redirect Protection):
 * - Blocks navigation to cloud metadata endpoints (169.254.x.x)
 * - Blocks navigation to internal/private networks
 * - Blocks WebSocket connections to internal hosts
 * - Validates all frame navigations
 */

import puppeteer from 'puppeteer-extra';
import StealthPlugin from 'puppeteer-extra-plugin-stealth';
import { existsSync } from 'fs';
import { platform } from 'os';

// Enable stealth mode
// Note: "Requesting main frame too early!" warnings are a known issue with puppeteer-extra-plugin-stealth
// They are harmless - stealth evasions still apply correctly on actual page navigation
puppeteer.use(StealthPlugin());

/**
 * Finds the Chrome executable path based on platform and environment.
 * Priority: PUPPETEER_EXECUTABLE_PATH env > CHROME_BIN env > system Chrome > Puppeteer bundled
 */
function findChromeExecutable() {
    // 1. Check PUPPETEER_EXECUTABLE_PATH (set in Docker/Cloud Run)
    if (process.env.PUPPETEER_EXECUTABLE_PATH && existsSync(process.env.PUPPETEER_EXECUTABLE_PATH)) {
        console.error(`[Browser] Using Chrome from PUPPETEER_EXECUTABLE_PATH: ${process.env.PUPPETEER_EXECUTABLE_PATH}`);
        return process.env.PUPPETEER_EXECUTABLE_PATH;
    }

    // 2. Check CHROME_BIN (alternative env var)
    if (process.env.CHROME_BIN && existsSync(process.env.CHROME_BIN)) {
        console.error(`[Browser] Using Chrome from CHROME_BIN: ${process.env.CHROME_BIN}`);
        return process.env.CHROME_BIN;
    }

    // 3. Platform-specific system Chrome paths
    const os = platform();
    const systemPaths = {
        darwin: [
            '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
            '/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary',
            '/Applications/Chromium.app/Contents/MacOS/Chromium',
        ],
        linux: [
            '/usr/bin/google-chrome-stable',
            '/usr/bin/google-chrome',
            '/usr/bin/chromium-browser',
            '/usr/bin/chromium',
        ],
        win32: [
            'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
            'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
        ],
    };

    const paths = systemPaths[os] || [];
    for (const chromePath of paths) {
        if (existsSync(chromePath)) {
            console.error(`[Browser] Using system Chrome: ${chromePath}`);
            return chromePath;
        }
    }

    // 4. Fall back to Puppeteer's bundled Chromium (requires `npx puppeteer browsers install chrome`)
    console.error('[Browser] No system Chrome found, using Puppeteer bundled Chromium');
    return undefined;
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
 * Validates if a URL is safe to navigate to.
 * Returns { allowed: boolean, reason?: string }
 */
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
 * Sets up navigation guards on a Puppeteer page.
 * Intercepts and validates all requests and frame navigations.
 */
async function setupNavigationGuards(page) {
    // Enable request interception
    await page.setRequestInterception(true);

    // Intercept all requests and block those to internal/metadata hosts
    page.on('request', async (request) => {
        const url = request.url();
        const validation = validateUrl(url);

        if (!validation.allowed) {
            console.error(`[Security] Blocked request to ${url}: ${validation.reason}`);
            await request.abort('blockedbyclient');
            return;
        }

        await request.continue();
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
let page = null;
let consoleLogs = [];
let pageErrors = [];

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
 * Launches the browser with stealth configuration.
 */
export async function launch(options = {}) {
    if (browser) {
        return { browser, page };
    }

    // Find Chrome executable (handles macOS, Linux, Docker, etc.)
    const executablePath = findChromeExecutable();

    const defaultOptions = {
        headless: options.headless ?? true, // Use boolean true for better compatibility
        executablePath, // Use detected Chrome path (undefined = Puppeteer bundled)
        dumpio: true, // Pipe Chrome's stdout/stderr for debugging
        timeout: 60000, // Increase launch timeout to 60 seconds
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox',
            '--disable-dev-shm-usage',
            '--disable-accelerated-2d-canvas',
            '--disable-gpu',
            '--window-size=1920,1080',
            '--disable-web-security',
            '--disable-features=IsolateOrigins,site-per-process',
            // SECURITY: WebRTC blocking to prevent IP leakage
            '--disable-webrtc',
            '--disable-webrtc-hw-decoding',
            '--disable-webrtc-hw-encoding',
            '--force-webrtc-ip-handling-policy=disable_non_proxied_udp',
            // SECURITY: Additional hardening
            '--disable-background-networking',
            '--disable-extensions',
            '--disable-sync',
            // Note: Removed --remote-debugging-address as it interferes with Puppeteer's CDP connection
        ],
        defaultViewport: {
            width: 1920,
            height: 1080,
        },
        ignoreHTTPSErrors: true,
    };

    console.error('[Browser] Launching with options:', JSON.stringify({
        headless: defaultOptions.headless,
        executablePath: defaultOptions.executablePath,
        timeout: defaultOptions.timeout,
    }));

    browser = await puppeteer.launch({
        ...defaultOptions,
        ...options,
    });

    console.error('[Browser] Launched browser process');
    return { browser };
}

let context = null;

/**
 * Creates a new isolated browser context (Clean Room).
 *
 * Note: "Requesting main frame too early!" errors are a known race condition
 * in puppeteer-extra-plugin-stealth where evasion scripts try to inject
 * before the main frame is fully initialized. We use retry logic to handle this.
 */
export async function createContext(options = {}) {
    if (!browser) {
        await launch();
    }

    // Close existing context if any
    if (context) {
        await closeContext();
    }

    // Retry logic for stealth plugin race condition
    const MAX_RETRIES = 3;
    const INITIAL_DELAY_MS = 200;

    for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        try {
            context = await browser.createBrowserContext();

            // Add delay BEFORE creating new page to let browser context stabilize
            // This is critical for avoiding the stealth plugin race condition
            await new Promise(resolve => setTimeout(resolve, 100));

            page = await context.newPage();

            // Additional delay AFTER page creation to ensure stealth evasions complete
            await new Promise(resolve => setTimeout(resolve, 50));

            // Success - break out of retry loop
            console.error(`[Browser] Context created successfully on attempt ${attempt}`);
            break;

        } catch (e) {
            const isMainFrameError = e.message && e.message.includes('main frame');

            if (isMainFrameError && attempt < MAX_RETRIES) {
                // Exponential backoff for stealth plugin race condition
                const delayMs = INITIAL_DELAY_MS * Math.pow(2, attempt - 1);
                console.error(`[Browser] Stealth plugin race condition on attempt ${attempt}/${MAX_RETRIES}, retrying in ${delayMs}ms...`);

                // Clean up failed context before retry
                if (context) {
                    try {
                        await context.close();
                    } catch (closeErr) {
                        // Ignore close errors
                    }
                    context = null;
                    page = null;
                }

                await new Promise(resolve => setTimeout(resolve, delayMs));
            } else {
                // Either not a main frame error or max retries reached
                console.error(`[Browser] Context creation failed after ${attempt} attempt(s): ${e.message}`);
                throw e;
            }
        }
    }

    // CRITICAL: Set default timeout for ALL Puppeteer operations to prevent hangs
    // This covers screenshot, click, type, waitForSelector, and any other operation
    // Default is 60 seconds - prevents Chrome from hanging forever while allowing slow pages
    page.setDefaultTimeout(60000);

    // Reset logs and click tracking for new context
    consoleLogs = [];
    pageErrors = [];

    // SECURITY: Reset click velocity tracking for new test session
    clickCount = 0;
    clickWindowStart = Date.now();

    // 1. Listen for explicit console.error() calls
    page.on('console', msg => {
        if (msg.type() === 'error') {
            const text = msg.text();
            // Simple truncation to avoid huge logs
            const truncated = text.length > 1000 ? text.substring(0, 1000) + '...' : text;
            consoleLogs.push(`[JS Console] ${truncated}`);
        }
    });

    // 2. Listen for Uncaught Exceptions (The "Crash" events)
    page.on('pageerror', error => {
        const text = error.message || String(error);
        const truncated = text.length > 1000 ? text.substring(0, 1000) + '...' : text;
        pageErrors.push(`[Uncaught Exception] ${truncated}`);
    });

    // Set a realistic user agent
    await page.setUserAgent(
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    );

    // SECURITY: Set up navigation guards to block SSRF/redirect attacks
    await setupNavigationGuards(page);

    // Provide video recording context if requested (options.video)
    // For now, handling basic isolation.

    console.error(`[Browser] Created new context (Run ID: ${options.runId || 'N/A'})`);
    return { context, page };
}

/**
 * Closes the current browser context.
 */
export async function closeContext() {
    if (page) {
        try {
            await page.close();
        } catch (e) { /* ignore */ }
        page = null;
    }

    if (context) {
        try {
            await context.close();
            console.error('[Browser] Context closed');
        } catch (e) {
            console.error('[Browser] Error closing context:', e.message);
        }
        context = null;
    }
    return { success: true };
}

/**
 * Navigates to a URL.
 */
export async function navigateTo(url, options = {}) {
    const { page } = await ensureBrowser();

    const timeout = options.timeout ?? 60000;  // 60 seconds for slow-loading pages
    const waitUntil = options.waitUntil ?? 'networkidle2';

    await page.goto(url, { waitUntil, timeout });

    return {
        url: page.url(),
        title: await page.title(),
    };
}

// Invalid jQuery-style pseudo-selectors that are NOT valid CSS
const INVALID_JQUERY_PATTERNS = /:contains\(|:has\(|:first(?!-)|:last(?!-)|:eq\(|:gt\(|:lt\(|:even|:odd/i;

/**
 * Sanitizes a selector by detecting and handling jQuery-style patterns.
 * Returns { selector: string, textFallback: string|null }
 */
function sanitizeSelector(selector) {
    if (!selector || typeof selector !== 'string') {
        return { selector, textFallback: null };
    }

    // Check if selector uses invalid jQuery patterns
    if (!INVALID_JQUERY_PATTERNS.test(selector)) {
        return { selector, textFallback: null };
    }

    console.error(`[Browser] Detected invalid jQuery selector: ${selector}`);

    // Extract text from :contains('text') or :contains("text")
    const containsMatch = selector.match(/:contains\(['"]([^'"]+)['"]\)/i);
    const textFallback = containsMatch ? containsMatch[1] : null;

    // Try to extract element type (button, a, div, etc.) before :contains
    let elementType = 'button';
    const colonIndex = selector.indexOf(':');
    if (colonIndex > 0) {
        const prefix = selector.substring(0, colonIndex).trim();
        if (prefix) {
            elementType = prefix;
        }
    }

    // Convert to aria-label selector as best effort
    if (textFallback) {
        const sanitized = `${elementType}[aria-label*="${textFallback}"]`;
        console.error(`[Browser] Sanitized selector: ${selector} -> ${sanitized}`);
        return { selector: sanitized, textFallback };
    }

    // Can't sanitize - return original and let it fail, then use text fallback
    return { selector, textFallback: null };
}

/**
 * Clicks on an element.
 *
 * SECURITY: Click velocity limits prevent click fraud.
 * - MAX_CLICKS_PER_TEST: Absolute limit per test session
 * - MAX_CLICKS_PER_MINUTE: Rate limit to prevent rapid clicking
 */
export async function click(selector, options = {}) {
    const { page } = await ensureBrowser();

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

    // CONSENT_FALLBACK: special selector for consent buttons when AI returns NOT_FOUND
    // Format: "CONSENT_FALLBACK:buttonText"
    if (selector && selector.startsWith('CONSENT_FALLBACK:')) {
        const targetText = selector.substring('CONSENT_FALLBACK:'.length);
        console.error(`[Browser] Using CONSENT_FALLBACK for: ${targetText}`);
        const clicked = await tryClickConsentButtonByText(page, targetText);
        if (clicked) {
            clickCount++;
            return { success: true };
        }
        throw new Error(`Consent button not found for: ${targetText}`);
    }

    // Sanitize jQuery-style selectors before use
    const { selector: sanitizedSelector, textFallback } = sanitizeSelector(selector);

    const timeout = options.timeout ?? 10000;

    // Try main page first
    let clicked = false;
    try {
        await page.waitForSelector(sanitizedSelector, { timeout: Math.min(timeout, 3000) });

        if (options.dblClick) {
            await page.click(sanitizedSelector, { clickCount: 2 });
            clickCount += 2;
        } else {
            await page.click(sanitizedSelector);
            clickCount++;
        }
        clicked = true;
    } catch (mainFrameError) {
        // Element not found in main frame - try iframes (for consent popups)
        console.error(`[Browser] Selector not found in main frame, searching iframes: ${sanitizedSelector}`);

        const frames = page.frames();
        for (const frame of frames) {
            if (frame === page.mainFrame()) continue;

            try {
                await frame.waitForSelector(sanitizedSelector, { timeout: Math.min(timeout, 2000) });
                console.error(`[Browser] Found selector in iframe: ${frame.url()}`);

                if (options.dblClick) {
                    await frame.click(sanitizedSelector, { clickCount: 2 });
                    clickCount += 2;
                } else {
                    await frame.click(sanitizedSelector);
                    clickCount++;
                }
                clicked = true;
                break;
            } catch (frameError) {
                // Not in this frame, continue searching
            }
        }

        if (!clicked) {
            // If still not found, try clicking consent buttons by text content
            // Use textFallback from sanitization if available, otherwise extract from selector
            const fallbackText = textFallback || selector;
            console.error(`[Browser] Trying fallback: finding button by text (fallback: ${fallbackText})`);
            const consentClicked = await tryClickConsentButtonByText(page, fallbackText);
            if (consentClicked) {
                clicked = true;
                clickCount++;
            }
        }
    }

    if (!clicked) {
        throw new Error(`Element not found: ${sanitizedSelector} (original: ${selector})`);
    }

    return { success: true };
}

/**
 * Fallback: Try to click a consent button by its text content.
 * Used when CSS selectors fail (e.g., for SourcePoint consent popups).
 *
 * @param {Page} page - Puppeteer page
 * @param {string} textOrSelector - Either direct text to find, or a selector to extract text from
 */
async function tryClickConsentButtonByText(page, textOrSelector) {
    // If it looks like a selector (contains brackets or special chars), extract text
    // Otherwise use it as direct text
    let targetText = '';
    if (textOrSelector.includes('[') || textOrSelector.includes(':') || textOrSelector.includes('#')) {
        // Looks like a selector - try to extract text from quotes
        const textMatches = textOrSelector.match(/['"]([^'"]+)['"]/);
        targetText = textMatches ? textMatches[1].toLowerCase() : '';
    } else {
        // Use directly as text
        targetText = textOrSelector.toLowerCase();
    }

    // Also check for common consent keywords
    const consentKeywords = ['agree', 'accept', 'i agree', 'i accept', 'accept all', 'consent', 'ok', 'got it'];

    // Try main page first
    try {
        const clicked = await page.evaluate((targetText, keywords) => {
            const allButtons = document.querySelectorAll('button, [role="button"], a[class*="btn"], span[class*="btn"]');

            for (const btn of allButtons) {
                const text = (btn.innerText || btn.getAttribute('aria-label') || '').trim().toLowerCase();
                const style = window.getComputedStyle(btn);

                if (text && style.display !== 'none' && style.visibility !== 'hidden') {
                    // Check if matches target text or is a consent keyword
                    const isMatch = (targetText && text.includes(targetText)) ||
                                   keywords.some(k => text.includes(k));
                    if (isMatch) {
                        btn.click();
                        return true;
                    }
                }
            }
            return false;
        }, targetText, consentKeywords);

        if (clicked) {
            console.error('[Browser] Clicked consent button via text fallback');
            return true;
        }
    } catch (e) {
        // Continue to iframes
    }

    // Try iframes
    const frames = page.frames();
    for (const frame of frames) {
        if (frame === page.mainFrame()) continue;

        try {
            const clicked = await frame.evaluate((targetText, keywords) => {
                const allButtons = document.querySelectorAll('button, [role="button"], a[class*="btn"], span[class*="btn"]');

                for (const btn of allButtons) {
                    const text = (btn.innerText || btn.getAttribute('aria-label') || '').trim().toLowerCase();
                    const style = window.getComputedStyle(btn);

                    if (text && style.display !== 'none' && style.visibility !== 'hidden') {
                        const isMatch = (targetText && text.includes(targetText)) ||
                                       keywords.some(k => text.includes(k));
                        if (isMatch) {
                            btn.click();
                            return true;
                        }
                    }
                }
                return false;
            }, targetText, consentKeywords);

            if (clicked) {
                console.error(`[Browser] Clicked consent button in iframe via text fallback: ${frame.url()}`);
                return true;
            }
        } catch (e) {
            // Frame might be detached
        }
    }

    return false;
}

/**
 * Types text into an element.
 */
export async function type(selector, text, options = {}) {
    const { page } = await ensureBrowser();

    const timeout = options.timeout ?? 10000;
    const delay = options.delay ?? 50; // Humanlike typing speed

    await page.waitForSelector(selector, { timeout });

    // Clear existing content if specified
    if (options.clear) {
        await page.click(selector, { clickCount: 3 });
        await page.keyboard.press('Backspace');
    }

    await page.type(selector, text, { delay });

    return { success: true };
}

/**
 * Takes a screenshot.
 */
export async function screenshot(options = {}) {
    const { page } = await ensureBrowser();

    const screenshotOptions = {
        type: options.format ?? 'png',
        fullPage: options.fullPage ?? false,
        encoding: 'base64',
    };

    if (options.quality && screenshotOptions.type !== 'png') {
        screenshotOptions.quality = options.quality;
    }

    if (options.selector) {
        const element = await page.$(options.selector);
        if (!element) {
            throw new Error(`Element not found: ${options.selector}`);
        }
        const data = await element.screenshot(screenshotOptions);
        return {
            data,
            mimeType: `image/${screenshotOptions.type}`,
        };
    }

    const data = await page.screenshot(screenshotOptions);
    return {
        data,
        mimeType: `image/${screenshotOptions.type}`,
    };
}

/**
 * Gets the DOM snapshot.
 *
 * @param {Object} options
 * @param {string} [options.mode='legacy'] - Snapshot mode: 'legacy' | 'aria'
 * @param {boolean} [options.verbose=false] - Include all elements (not just interactive)
 *
 * Note: 'aria' mode is not natively supported in Puppeteer.
 * For aria snapshots, use playwright-engine.js instead.
 * If aria mode is requested here, it falls back to legacy with a warning.
 */
export async function snapshot(options = {}) {
    const { page } = await ensureBrowser();

    // Warn if aria mode is requested (not available in Puppeteer)
    if (options.mode === 'aria') {
        console.error('[Browser] Warning: aria snapshot mode requires Playwright engine. Falling back to legacy mode.');
    }

    const nodes = await page.evaluate((verbose) => {
        // COST OPTIMIZATION: Reduced from 500 to 300 to minimize token usage
        // while still capturing most interactive elements
        const MAX_NODES = 300;
        const elements = [];

        const escapeValue = (value) => {
            if (!value) return '';
            return value.replace(/"/g, '\\"');
        };

        // COST OPTIMIZATION: Skip hidden elements and non-visible content
        const isVisible = (el) => {
            try {
                const style = window.getComputedStyle(el);
                if (style.display === 'none') return false;
                if (style.visibility === 'hidden') return false;
                if (style.opacity === '0') return false;
                // Skip elements with zero dimensions
                const rect = el.getBoundingClientRect();
                if (rect.width === 0 && rect.height === 0) return false;
                return true;
            } catch (e) {
                return true; // Include on error to be safe
            }
        };

        // COST OPTIMIZATION: Skip non-content elements
        const shouldSkipElement = (el) => {
            const tag = el.tagName.toLowerCase();
            // Skip script, style, noscript, template, svg internals
            if (['script', 'style', 'noscript', 'template', 'path', 'defs', 'clippath', 'lineargradient'].includes(tag)) {
                return true;
            }
            // Skip if inside script/style
            let parent = el.parentElement;
            while (parent) {
                const parentTag = parent.tagName.toLowerCase();
                if (['script', 'style', 'noscript'].includes(parentTag)) {
                    return true;
                }
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

            // COST OPTIMIZATION: Skip hidden and non-content elements early
            if (shouldSkipElement(el)) {
                current = walker.nextNode();
                continue;
            }

            // COST OPTIMIZATION: Only include visible elements (unless verbose mode)
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

    let mainContent = lines.join('\n');

    // Also capture consent/cookie iframe content
    const iframeContent = await captureConsentIframes(page);
    if (iframeContent) {
        mainContent += '\n\n--- IFRAME CONTENT (consent/cookie popup) ---\n' + iframeContent;
    }

    return {
        text: mainContent,
        url: page.url(),
        title: await page.title(),
    };
}

/**
 * Common consent/cookie iframe URL patterns to check.
 */
const CONSENT_IFRAME_PATTERNS = [
    'onetrust',
    'cookiebot',
    'truste',
    'trustarc',
    'cookieyes',
    'quantcast',
    'consent',
    'gdpr',
    'ccpa',
    'sourcepoint',
    'sp_message',
    'sp-prod',
    'privacy',
    'privacymanager',
    'cmp',
    'tcfapi',
];

/**
 * Captures content from consent/cookie iframes (Puppeteer version).
 */
async function captureConsentIframes(page) {
    const frames = page.frames();
    const consentFrames = frames.filter(frame => {
        const url = frame.url().toLowerCase();
        return CONSENT_IFRAME_PATTERNS.some(pattern => url.includes(pattern));
    });

    const contents = [];

    // Always check main page dialogs FIRST (OneTrust and other CMPs often use divs, not iframes)
    const dialogContent = await captureConsentDialogs(page);
    if (dialogContent && dialogContent.trim()) {
        contents.push(dialogContent);
    }

    // Then also check any consent iframes
    for (const frame of consentFrames) {
        try {
            const frameContent = await frame.evaluate(() => {
                const elements = [];
                const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT);
                let current = walker.currentNode;
                let count = 0;

                while (current && count < 100) {
                    const el = current;
                    const tag = el.tagName.toLowerCase();
                    const role = el.getAttribute('role') || tag;
                    const ariaLabel = el.getAttribute('aria-label');
                    const text = (el.innerText || '').trim().slice(0, 50);
                    const name = ariaLabel || text;

                    if (['button', 'a', 'input'].includes(tag) || role || name) {
                        elements.push(`  [${role}] "${name}"`);
                        count++;
                    }
                    current = walker.nextNode();
                }
                return elements.join('\n');
            });

            if (frameContent && frameContent.trim()) {
                console.error(`[Browser] Found consent iframe: ${frame.url()}`);
                contents.push(frameContent);
            }
        } catch (e) {
            // Frame may have been navigated or removed
        }
    }

    return contents.join('\n\n');
}

/**
 * Captures content from consent dialogs/modals in main page.
 * Includes specific selectors for common CMPs (OneTrust, SourcePoint, etc.)
 */
async function captureConsentDialogs(page) {
    try {
        return await page.evaluate(() => {
            // Comprehensive selector list for common CMPs
            const selectors = [
                // OneTrust (very common - CNN, many news sites)
                '#onetrust-consent-sdk',
                '#onetrust-banner-sdk',
                '#onetrust-policy',
                // SourcePoint
                '[id^="sp_message_container"]',
                '[class*="sp_message"]',
                '[data-sp-message]',
                // TrustArc
                '#truste-consent-track',
                '[class*="truste"]',
                // Quantcast
                '#qc-cmp2-container',
                '.qc-cmp2-container',
                // CookieBot
                '#CybotCookiebotDialog',
                // Generic ARIA
                'div[role="dialog"]',
                'div[role="alertdialog"]',
                '[aria-modal="true"]',
                // Generic class-based
                '.cookie-banner',
                '.consent-banner',
                '.privacy-banner',
                '.gdpr-banner',
                '[class*="cookie-consent"]',
                '[class*="consent-banner"]',
                '[class*="privacy-notice"]',
                // Generic ID-based
                '[id*="cookie-banner"]',
                '[id*="consent-banner"]',
                '[id*="privacy-banner"]',
            ];

            const contents = [];
            const processedElements = new Set();

            for (const selector of selectors) {
                try {
                    const elements = document.querySelectorAll(selector);
                    for (const dialog of elements) {
                        // Avoid processing same element twice
                        if (processedElements.has(dialog)) continue;
                        processedElements.add(dialog);

                        const style = window.getComputedStyle(dialog);
                        if (style.display === 'none' || style.visibility === 'hidden') continue;

                        // Get all interactive elements
                        const buttons = dialog.querySelectorAll(
                            'button, a[role="button"], [role="button"], ' +
                            'input[type="submit"], input[type="button"], ' +
                            '[class*="btn"], [class*="button"]'
                        );

                        const elementInfo = [];
                        for (const btn of buttons) {
                            const text = (btn.innerText || btn.getAttribute('aria-label') || '').trim();
                            const id = btn.id;
                            const btnStyle = window.getComputedStyle(btn);
                            if (text && btnStyle.display !== 'none' && btnStyle.visibility !== 'hidden') {
                                const idInfo = id ? ` id="${id}"` : '';
                                elementInfo.push(`  [button${idInfo}] "${text.slice(0, 50)}"`);
                            }
                        }

                        if (elementInfo.length > 0) {
                            contents.push(elementInfo.join('\n'));
                        }
                    }
                } catch (e) {
                    // Selector might be invalid, continue
                }
            }

            // AGGRESSIVE SCAN: Look for ANY visible button with consent-related text
            // This catches consent popups that don't match specific selectors
            const consentKeywords = ['agree', 'accept', 'i agree', 'i accept', 'accept all', 'agree all', 'consent', 'got it', 'ok', 'continue'];
            const allButtons = document.querySelectorAll('button, [role="button"], a[class*="btn"], span[class*="btn"]');

            for (const btn of allButtons) {
                try {
                    const text = (btn.innerText || btn.getAttribute('aria-label') || '').trim().toLowerCase();
                    const btnStyle = window.getComputedStyle(btn);

                    // Check if visible and contains consent keyword
                    if (text && btnStyle.display !== 'none' && btnStyle.visibility !== 'hidden' && btnStyle.opacity !== '0') {
                        const isConsentButton = consentKeywords.some(keyword => text.includes(keyword));
                        if (isConsentButton && !processedElements.has(btn)) {
                            processedElements.add(btn);
                            const id = btn.id;
                            const className = btn.className;
                            const idInfo = id ? ` id="${id}"` : '';
                            const classInfo = className ? ` class="${String(className).slice(0, 50)}"` : '';
                            const originalText = (btn.innerText || btn.getAttribute('aria-label') || '').trim();
                            contents.push(`  [CONSENT BUTTON${idInfo}${classInfo}] "${originalText.slice(0, 50)}"`);
                        }
                    }
                } catch (e) {
                    // Ignore errors for individual buttons
                }
            }

            return contents.join('\n\n');
        });
    } catch (e) {
        return '';
    }
}

/**
 * Hovers over an element.
 */
export async function hover(selector, options = {}) {
    const { page } = await ensureBrowser();

    const timeout = options.timeout ?? 10000;

    await page.waitForSelector(selector, { timeout });
    await page.hover(selector);

    return { success: true };
}

/**
 * Presses a key or key combination.
 */
export async function pressKey(key) {
    const { page } = await ensureBrowser();

    // Handle key combinations like "Control+A"
    if (key.includes('+')) {
        const parts = key.split('+');
        const modifiers = parts.slice(0, -1);
        const mainKey = parts[parts.length - 1];

        for (const mod of modifiers) {
            await page.keyboard.down(mod);
        }
        await page.keyboard.press(mainKey);
        for (const mod of modifiers.reverse()) {
            await page.keyboard.up(mod);
        }
    } else {
        await page.keyboard.press(key);
    }

    return { success: true };
}

/**
 * Waits for an element or condition.
 */
export async function waitFor(options = {}) {
    const { page } = await ensureBrowser();

    const timeout = options.timeout ?? 60000;  // 60 seconds for slow pages

    if (options.selector) {
        await page.waitForSelector(options.selector, { timeout });
    } else if (options.text) {
        await page.waitForFunction(
            (text) => document.body.innerText.includes(text),
            { timeout },
            options.text
        );
    } else if (options.navigation) {
        await page.waitForNavigation({ timeout });
    } else if (options.ms) {
        await new Promise(resolve => setTimeout(resolve, options.ms));
    }

    return { success: true };
}

/**
 * Evaluates JavaScript in the page context.
 */
export async function evaluate(script, args = []) {
    const { page } = await ensureBrowser();

    // Create function from script string
    // Wrap script in 'return (...)' to ensure expression results are returned
    // This handles both IIFE scripts and regular expressions
    const wrappedScript = 'return (' + script + ')';
    const fn = new Function('...args', wrappedScript);
    const result = await page.evaluate(fn, ...args);

    return { result };
}

/**
 * Gets the current page URL and title.
 */
export async function getPageInfo() {
    const { page } = await ensureBrowser();

    return {
        url: page.url(),
        title: await page.title(),
    };
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
        console.error('[Browser] Closed');
    }
    return { success: true };
}

/**
 * Ensures browser is launched.
 */
async function ensureBrowser() {
    if (!browser) {
        await launch();
    }
    if (!context || !page) {
        await createContext();
    }
    return { browser, page };
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
