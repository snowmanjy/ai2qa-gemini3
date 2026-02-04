import type { TestRun, PagedResult, TestRunStatus } from '@/types'

export function createTestRun(overrides: Partial<TestRun> = {}): TestRun {
    return {
        id: 'test-run-1',
        targetUrl: 'https://example.com',
        status: 'COMPLETED' as TestRunStatus,
        persona: 'STANDARD',
        createdAt: '2025-01-15T10:00:00Z',
        ...overrides,
    }
}

export interface ExecutedStep {
    step: {
        stepId: string
        action: string
        target: string
        selector?: string
        value?: string
        params: Record<string, string>
    }
    status: string
    executedAt: string
    durationMs: number
    selectorUsed?: string
    errorMessage?: string
    retryCount: number
    optimizationSuggestion?: string
    networkErrors: string[]
    accessibilityWarnings: string[]
    consoleErrors: string[]
    hasScreenshot?: boolean
    isHealed?: boolean
}

export function createExecutedStep(overrides: Partial<ExecutedStep> = {}): ExecutedStep {
    return {
        step: {
            stepId: 'step-1',
            action: 'CLICK',
            target: 'Submit button',
            params: {},
        },
        status: 'SUCCESS',
        executedAt: '2025-01-15T10:00:01Z',
        durationMs: 500,
        retryCount: 0,
        networkErrors: [],
        accessibilityWarnings: [],
        consoleErrors: [],
        ...overrides,
    }
}

export function createPagedResult<T>(items: T[], page = 0, size = 20): PagedResult<T> {
    return {
        content: items,
        page,
        size,
        totalElements: items.length,
        totalPages: Math.ceil(items.length / size) || 1,
    }
}
