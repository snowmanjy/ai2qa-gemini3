export type TestRunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'TIMEOUT';

export type SummaryStatus = 'PENDING' | 'GENERATING' | 'COMPLETED' | 'FAILED';

export type Persona = 'STANDARD' | 'CHAOS' | 'HACKER' | 'PERFORMANCE_HAWK';

export interface RunSummary {
    status: 'SUCCESS' | 'FAILURE';
    goalOverview: string;
    outcomeShort: string;
    failureAnalysis: string | null;
    actionableFix: string | null;
    keyAchievements: string[];
}

export interface TestRun {
    id: string;
    targetUrl: string;
    status: TestRunStatus;
    persona?: Persona;
    createdAt: string;
    completedAt?: string;
    failureReason?: string;
    summary?: RunSummary;
    summaryStatus?: SummaryStatus;
    progressPercent?: number;
    executedStepCount?: number;
    totalStepCount?: number;
}

export interface PagedResult<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface CreateTestRunRequest {
    targetUrl: string;
    goals: string[];
    persona: Persona;
    cookiesJson?: string;
    recaptchaToken?: string;
}

// Saved Test Plans
export interface SavedTestPlan {
    id: string;
    name: string;
    description?: string;
    targetUrl: string;
    goals: string[];
    persona: Persona;
    createdAt: string;
    updatedAt: string;
}

export interface CreateSavedPlanRequest {
    name: string;
    description?: string;
    targetUrl: string;
    goals: string[];
    persona: Persona;
}

export interface UpdateSavedPlanRequest {
    name?: string;
    description?: string;
    targetUrl?: string;
    goals?: string[];
    persona?: Persona;
}

export interface ExecutedStepInfo {
    stepNumber: number;
    action: string;
    target: string;
    passed: boolean;
    status: string;
    errorMessage: string | null;
    durationMs: number;
    executedAt: string;
}
