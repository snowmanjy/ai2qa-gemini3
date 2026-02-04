-- V25: Prepend same-page exploration and adaptive targeting instructions to skills
-- Context: Personas were too rigid — scrolling up/down without discovering tabs, modals, or hidden content.
-- This update makes skills smarter about exploring what's ON the page without navigating away.

UPDATE skill
SET instructions = $$**ADAPTIVE SCANNING:**
Before running security scans, explore the current page thoroughly — scroll, click tabs, expand sections, open menus.
Probe whatever input fields you discover. If an expected input doesn't exist, skip it and move on.
Never fail because a specific element is missing — adapt to the actual page structure.

$$ || instructions,
    updated_at = NOW()
WHERE name = 'security-scanning';

UPDATE skill
SET instructions = $$**THOROUGH PAGE EXPLORATION:**
Before testing elements, explore the current page fully — scroll, click tabs, expand sections, open menus.
Discover all interactive content including lazy-loaded elements, modals, and progressive forms.
Test everything you find, not just what's visible on initial load.

$$ || instructions,
    updated_at = NOW()
WHERE name = 'webapp-testing';
