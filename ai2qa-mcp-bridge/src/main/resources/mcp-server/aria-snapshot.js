/**
 * ARIA Snapshot Module - Accessibility tree processing with ref-based element selection.
 *
 * Ported from agent-browser (Vercel Labs) for ~95% token savings.
 *
 * This module provides:
 * - ARIA tree processing with ref generation
 * - Interactive element filtering
 * - Nth disambiguation for duplicate role+name combinations
 * - RefMap caching for deterministic element selection
 *
 * Example output:
 *   - button "Login" [ref=e1]
 *   - textbox "Email" [ref=e2]
 *   - link "Forgot password?" [ref=e3]
 *
 * Usage:
 *   const { tree, refs } = processAriaTree(ariaTreeString, { interactive: true });
 *   // Use refs['e1'] to get role+name for locator resolution
 */

/**
 * Interactive roles that should get refs.
 * These are elements users typically interact with.
 */
export const INTERACTIVE_ROLES = new Set([
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
 * Content roles that get refs when they have a name.
 * These provide context and text content.
 */
export const CONTENT_ROLES = new Set([
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

/**
 * Structural roles that are typically filtered in compact mode.
 */
export const STRUCTURAL_ROLES = new Set([
    'generic',
    'group',
    'list',
    'table',
    'row',
    'rowgroup',
    'grid',
    'treegrid',
    'menu',
    'menubar',
    'toolbar',
    'tablist',
    'tree',
    'directory',
    'document',
    'application',
    'presentation',
    'none',
]);

/**
 * @typedef {Object} RefData
 * @property {string} role - ARIA role of the element
 * @property {string} [name] - Accessible name of the element
 * @property {number} [nth] - Index for disambiguation (only present for duplicates)
 */

/**
 * @typedef {Object.<string, RefData>} RefMap
 */

/**
 * @typedef {Object} ProcessedSnapshot
 * @property {string} tree - The processed ARIA tree with refs
 * @property {RefMap} refs - Map of ref IDs to element data
 */

/**
 * @typedef {Object} SnapshotOptions
 * @property {boolean} [interactive=false] - Only include interactive elements
 * @property {boolean} [compact=false] - Remove empty structural elements
 * @property {number} [maxDepth] - Maximum depth to include
 */

let refCounter = 0;

/**
 * Reset the ref counter (call at start of each snapshot).
 */
export function resetRefCounter() {
    refCounter = 0;
}

/**
 * Get the next ref ID.
 * @returns {string}
 */
function nextRef() {
    return `e${++refCounter}`;
}

/**
 * Get a key for role+name combination.
 * @param {string} role
 * @param {string} [name]
 * @returns {string}
 */
function getRoleNameKey(role, name) {
    return `${role}:${name ?? ''}`;
}

/**
 * Process an ARIA tree string and generate refs.
 *
 * @param {string} ariaTree - Raw ARIA tree from Playwright's ariaSnapshot()
 * @param {SnapshotOptions} [options={}]
 * @returns {ProcessedSnapshot}
 */
export function processAriaTree(ariaTree, options = {}) {
    refCounter = 0;
    const refs = {};
    const lines = ariaTree.split('\n');
    const result = [];

    // Track role+name occurrences for nth disambiguation
    const roleNameCounts = new Map();

    // First pass: count occurrences of each role+name
    for (const line of lines) {
        const match = line.match(/^(\s*-\s*)(\w+)(?:\s+"([^"]*)")?(.*)$/);
        if (!match) continue;

        const [, , role, name] = match;
        const roleLower = role.toLowerCase();

        if (INTERACTIVE_ROLES.has(roleLower) || (CONTENT_ROLES.has(roleLower) && name)) {
            const key = getRoleNameKey(roleLower, name);
            roleNameCounts.set(key, (roleNameCounts.get(key) ?? 0) + 1);
        }
    }

    // Second pass: process lines and generate refs
    const currentCounts = new Map();

    for (const line of lines) {
        const processed = processLine(
            line,
            refs,
            options,
            roleNameCounts,
            currentCounts
        );

        if (processed !== null) {
            result.push(processed);
        }
    }

    const tree = result.join('\n') ||
        (options.interactive ? '(no interactive elements)' : '(empty)');

    return { tree, refs };
}

/**
 * Process a single line of the ARIA tree.
 *
 * @param {string} line
 * @param {RefMap} refs
 * @param {SnapshotOptions} options
 * @param {Map<string, number>} totalCounts - Total count for each role+name
 * @param {Map<string, number>} currentCounts - Current index for each role+name
 * @returns {string|null}
 */
function processLine(line, refs, options, totalCounts, currentCounts) {
    // Check depth limit
    if (options.maxDepth !== undefined) {
        const depth = getIndentLevel(line);
        if (depth > options.maxDepth) {
            return null;
        }
    }

    // Match ARIA tree lines like:
    //   - button "Submit"
    //   - heading "Title" [level=1]
    //   - link "Click me":
    const match = line.match(/^(\s*-\s*)(\w+)(?:\s+"([^"]*)")?(.*)$/);

    if (!match) {
        // Non-matching lines (metadata, text content)
        if (options.interactive) {
            return null; // Filter out in interactive mode
        }
        return line;
    }

    const [, prefix, role, name, suffix] = match;
    const roleLower = role.toLowerCase();

    // Skip metadata lines (like /url:)
    if (role.startsWith('/')) {
        return line;
    }

    const isInteractive = INTERACTIVE_ROLES.has(roleLower);
    const isNamedContent = CONTENT_ROLES.has(roleLower) && name;
    const isStructural = STRUCTURAL_ROLES.has(roleLower);

    // In interactive-only mode, filter non-interactive elements
    if (options.interactive && !isInteractive) {
        return null;
    }

    // In compact mode, skip unnamed structural elements
    if (options.compact && isStructural && !name) {
        return null;
    }

    // Should this element get a ref?
    const shouldHaveRef = isInteractive || isNamedContent;

    if (shouldHaveRef) {
        const ref = nextRef();
        const key = getRoleNameKey(roleLower, name);

        // Get nth index for this element
        const currentIndex = currentCounts.get(key) ?? 0;
        currentCounts.set(key, currentIndex + 1);

        // Only include nth if there are duplicates
        const hasDuplicates = (totalCounts.get(key) ?? 0) > 1;

        refs[ref] = {
            role: roleLower,
            name,
            ...(hasDuplicates ? { nth: currentIndex } : {}),
        };

        // Build enhanced line with ref
        let enhanced = `${prefix}${role}`;
        if (name) enhanced += ` "${name}"`;
        enhanced += ` [ref=${ref}]`;
        if (hasDuplicates && currentIndex > 0) {
            enhanced += ` [nth=${currentIndex}]`;
        }
        // Preserve existing attributes (like [level=1])
        if (suffix && suffix.includes('[')) {
            enhanced += suffix;
        }

        return enhanced;
    }

    return line;
}

/**
 * Get the indentation level of a line.
 * @param {string} line
 * @returns {number}
 */
function getIndentLevel(line) {
    const match = line.match(/^(\s*)/);
    return match ? Math.floor(match[1].length / 2) : 0;
}

/**
 * Parse a ref from a command argument.
 *
 * Accepts formats:
 * - "@e1"
 * - "ref=e1"
 * - "e1"
 *
 * @param {string} arg
 * @returns {string|null}
 */
export function parseRef(arg) {
    if (!arg) return null;

    if (arg.startsWith('@')) {
        return arg.slice(1);
    }
    if (arg.startsWith('ref=')) {
        return arg.slice(4);
    }
    if (/^e\d+$/.test(arg)) {
        return arg;
    }
    return null;
}

/**
 * Check if a string is a ref format.
 * @param {string} str
 * @returns {boolean}
 */
export function isRef(str) {
    return parseRef(str) !== null;
}

/**
 * Build a Playwright-style selector string from ref data.
 *
 * @param {RefData} refData
 * @returns {string}
 */
export function buildSelector(refData) {
    if (refData.name) {
        const escapedName = refData.name.replace(/"/g, '\\"');
        return `getByRole('${refData.role}', { name: "${escapedName}", exact: true })`;
    }
    return `getByRole('${refData.role}')`;
}

/**
 * Get statistics about a processed snapshot.
 *
 * @param {string} tree
 * @param {RefMap} refs
 * @returns {{lines: number, chars: number, tokens: number, refs: number, interactive: number}}
 */
export function getSnapshotStats(tree, refs) {
    const interactiveCount = Object.values(refs)
        .filter(r => INTERACTIVE_ROLES.has(r.role))
        .length;

    return {
        lines: tree.split('\n').length,
        chars: tree.length,
        tokens: Math.ceil(tree.length / 4), // Rough estimate
        refs: Object.keys(refs).length,
        interactive: interactiveCount,
    };
}

/**
 * Create a compact version of the tree by removing empty structural branches.
 *
 * @param {string} tree
 * @returns {string}
 */
export function compactTree(tree) {
    const lines = tree.split('\n');
    const result = [];

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];

        // Always keep lines with refs
        if (line.includes('[ref=')) {
            result.push(line);
            continue;
        }

        // Keep lines with text content (after :)
        if (line.includes(':') && !line.endsWith(':')) {
            result.push(line);
            continue;
        }

        // Check if this structural element has children with refs
        const currentIndent = getIndentLevel(line);
        let hasRelevantChildren = false;

        for (let j = i + 1; j < lines.length; j++) {
            const childIndent = getIndentLevel(lines[j]);
            if (childIndent <= currentIndent) break;
            if (lines[j].includes('[ref=')) {
                hasRelevantChildren = true;
                break;
            }
        }

        if (hasRelevantChildren) {
            result.push(line);
        }
    }

    return result.join('\n');
}
