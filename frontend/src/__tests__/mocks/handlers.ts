import { http, HttpResponse } from 'msw'

const API_BASE = 'http://localhost:8080/api/v1'

export const handlers = [
    // Test Runs - List
    http.get(`${API_BASE}/test-runs`, () => {
        return HttpResponse.json({
            content: [
                {
                    id: 'run-1',
                    targetUrl: 'https://example.com',
                    status: 'COMPLETED',
                    persona: 'STANDARD',
                    executionMode: 'CLOUD',
                    createdAt: '2025-01-15T10:00:00Z',
                    completedAt: '2025-01-15T10:05:00Z',
                },
                {
                    id: 'run-2',
                    targetUrl: 'https://test.dev',
                    status: 'RUNNING',
                    persona: 'CHAOS',
                    executionMode: 'CLOUD',
                    createdAt: '2025-01-15T11:00:00Z',
                    progressPercent: 45,
                    executedStepCount: 5,
                    totalStepCount: 11,
                },
                {
                    id: 'run-3',
                    targetUrl: 'https://staging.app',
                    status: 'FAILED',
                    persona: 'HACKER',
                    executionMode: 'LOCAL_AGENT',
                    createdAt: '2025-01-15T09:00:00Z',
                    completedAt: '2025-01-15T09:03:00Z',
                    failureReason: 'XSS vulnerability found',
                },
            ],
            page: 0,
            size: 20,
            totalElements: 3,
            totalPages: 1,
        })
    }),

    // Test Runs - Single run detail
    http.get(`${API_BASE}/test-runs/:id`, ({ params }) => {
        const id = params.id as string
        return HttpResponse.json({
            id,
            targetUrl: 'https://example.com',
            status: 'COMPLETED',
            persona: 'STANDARD',
            executionMode: 'CLOUD',
            createdAt: '2025-01-15T10:00:00Z',
            completedAt: '2025-01-15T10:05:00Z',
            summary: {
                status: 'SUCCESS',
                goalOverview: 'Verify page loads and functionality',
                outcomeShort: 'All tests passed successfully',
                failureAnalysis: null,
                actionableFix: null,
                keyAchievements: ['Page loaded', 'Form submitted', 'Data validated'],
            },
        })
    }),

    // Test Runs - Execution log
    http.get(`${API_BASE}/test-runs/:id/log`, () => {
        return HttpResponse.json([
            {
                step: {
                    stepId: 'step-1',
                    action: 'NAVIGATE',
                    target: 'https://example.com',
                    params: {},
                },
                status: 'SUCCESS',
                executedAt: '2025-01-15T10:00:01Z',
                durationMs: 1200,
                retryCount: 0,
                networkErrors: [],
                accessibilityWarnings: [],
                consoleErrors: [],
            },
            {
                step: {
                    stepId: 'step-2',
                    action: 'CLICK',
                    target: 'Submit button',
                    selector: '#submit-btn',
                    params: {},
                },
                status: 'SUCCESS',
                executedAt: '2025-01-15T10:00:03Z',
                durationMs: 350,
                retryCount: 1,
                networkErrors: [],
                accessibilityWarnings: ['Button missing aria-label'],
                consoleErrors: [],
            },
            {
                step: {
                    stepId: 'step-3',
                    action: 'ASSERT',
                    target: 'Success message visible',
                    params: {},
                },
                status: 'FAILED',
                executedAt: '2025-01-15T10:00:05Z',
                durationMs: 2000,
                retryCount: 0,
                errorMessage: 'Element not found within timeout',
                networkErrors: ['GET /api/data returned 500'],
                accessibilityWarnings: [],
                consoleErrors: ['Uncaught TypeError: Cannot read property'],
            },
        ])
    }),

    // Credits - Balance
    http.get(`${API_BASE}/credits/balance`, () => {
        return HttpResponse.json({
            balance: 42,
            expiresAt: null,
        })
    }),

    // Credits - Purchases
    http.get(`${API_BASE}/credits/purchases`, () => {
        return HttpResponse.json([
            {
                sessionId: 'sess-1',
                amountTotal: 999,
                currency: 'usd',
                credits: 10,
                purchasedAt: '2025-01-10T08:00:00Z',
                paymentStatus: 'paid',
            },
        ])
    }),

    // Admin - Skills
    http.get(`${API_BASE}/admin/skills`, () => {
        return HttpResponse.json([
            {
                id: 'skill-1',
                name: 'XSS Scanner',
                category: 'SECURITY',
                status: 'ACTIVE',
                sourceUrl: null,
                createdAt: '2025-01-01T00:00:00Z',
                updatedAt: '2025-01-10T00:00:00Z',
            },
        ])
    }),

    // Admin - Personas
    http.get(`${API_BASE}/admin/personas`, () => {
        return HttpResponse.json([
            {
                id: 'persona-1',
                name: 'STANDARD',
                displayName: 'The Auditor',
                temperature: 0.3,
                source: 'SYSTEM',
                active: true,
                skills: [],
            },
        ])
    }),

    // Test Runs - Create
    http.post(`${API_BASE}/test-runs`, () => {
        return HttpResponse.json(
            { id: 'new-run-123', status: 'PENDING' },
            { status: 201 },
        )
    }),

    // Agents - List
    http.get(`${API_BASE}/agents`, () => {
        return HttpResponse.json([])
    }),
]
