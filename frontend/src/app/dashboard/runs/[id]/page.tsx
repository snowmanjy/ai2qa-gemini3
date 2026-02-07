"use client"

import { useEffect, useState, useRef, useMemo } from "react"
import { api, API_BASE_URL } from "@/lib/api"
import { TestRun } from "@/types"
import { useParams } from "next/navigation"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Loader2, ArrowLeft, Check, X, Sparkles, Clock, Image as ImageIcon, Lightbulb, Radio, Gauge } from "lucide-react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { format } from "date-fns"
import { toast } from "sonner"
import { FinalReport } from "@/components/report/final-report"
import { StepDiagnostics } from "@/components/dashboard/run-report/step-diagnostics"
import { capture } from "@/lib/analytics"

const DATA_TESTID_RE = /(data-testid=["'][^"']+["']|data-testid=\S+)/g;

function getStatusLabel(status: string) {
    switch (status) {
        case 'COMPLETED': return 'PASSED'
        case 'TIMEOUT': return 'TIMED OUT'
        default: return status
    }
}

interface ActionStep {
    stepId: string;
    action: string;
    target: string;
    selector?: string;
    value?: string;
    params: Record<string, string>;
}

interface PerformanceMetrics {
    webVitals: Record<string, number>;
    navigation: Record<string, number>;
    totalResources?: number;
    totalTransferSizeKb?: number;
    issues?: Array<{
        severity: string;
        category: string;
        message: string;
        value?: number;
        threshold?: number;
    }>;
    summary: string;
}

interface ExecutedStep {
    step: ActionStep;
    status: string; // "SUCCESS" | "FAILED" | "SKIPPED" | "TIMEOUT"
    executedAt: string;
    durationMs: number;
    selectorUsed?: string;
    snapshotBefore?: unknown;
    snapshotAfter?: unknown;
    errorMessage?: string;
    retryCount: number;
    optimizationSuggestion?: string;
    networkErrors: string[];
    accessibilityWarnings: string[];
    consoleErrors: string[];
    performanceMetrics?: PerformanceMetrics;
    // Computed properties for UI
    hasScreenshot?: boolean;
    isHealed?: boolean;
}

export default function RunDetailPage() {
    const params = useParams()
    const id = params.id as string
    const [run, setRun] = useState<TestRun | null>(null)
    const [logs, setLogs] = useState<ExecutedStep[]>([])
    const [loading, setLoading] = useState(true)
    const [failedScreenshots, setFailedScreenshots] = useState<Set<number>>(() => new Set())
    const hasTrackedPageView = useRef(false)
    const isInitialLoad = useRef(true)

    useEffect(() => {
        if (!id) return;

        let isMounted = true;
        let pollTimer: NodeJS.Timeout;
        let currentStatus: string | null = null;
        let currentSummaryStatus: string | null = null;

        const fetchData = async () => {
            try {
                const [runRes, logRes] = await Promise.all([
                    api.get<TestRun>(`/test-runs/${id}`),
                    api.get<ExecutedStep[]>(`/test-runs/${id}/log`).catch(e => {
                        console.warn("Failed to fetch logs", e)
                        return null
                    })
                ])
                if (!isMounted) return;

                setRun(runRes.data)
                currentStatus = runRes.data.status;
                currentSummaryStatus = runRes.data.summaryStatus || null;

                if (logRes) {
                    const enrichedLogs = logRes.data.map(step => ({
                        ...step,
                        isHealed: step.retryCount > 0,
                        // Use actual hasScreenshot from API (true for non-SKIPPED steps)
                        // Falls back to true for backwards compatibility if field is missing
                        hasScreenshot: step.hasScreenshot ?? true
                    }))
                    setLogs(enrichedLogs)
                    setFailedScreenshots(new Set())
                }

            } catch (e) {
                console.error("Failed to fetch run details", e)
                // Only show toast on initial load error, not during polling
                if (isMounted && isInitialLoad.current) {
                    toast.error("Failed to load run details")
                }
            } finally {
                if (isMounted) {
                    setLoading(false)
                    isInitialLoad.current = false;
                    // Continue polling if:
                    // 1. Run is still active (PENDING/RUNNING)
                    // 2. Summary is not yet ready (PENDING or GENERATING) on a completed run
                    //    PENDING means generation hasn't kicked in yet but will soon
                    //    GENERATING means AI is actively producing the summary
                    const runActive = currentStatus === null || ['PENDING', 'RUNNING'].includes(currentStatus);
                    const summaryNotReady = currentSummaryStatus !== 'COMPLETED' && currentSummaryStatus !== 'FAILED';

                    // Clear active run from sidebar when this run finishes
                    if (!runActive && sessionStorage.getItem("activeRunId") === id) {
                        sessionStorage.removeItem("activeRunId");
                    }

                    if (runActive || summaryNotReady) {
                        pollTimer = setTimeout(fetchData, 2000);
                    }
                }
            }
        }

        fetchData()

        return () => {
            isMounted = false;
            if (pollTimer) clearTimeout(pollTimer);
        }
    }, [id])

    // Track page view once when run data is loaded
    useEffect(() => {
        if (run && !hasTrackedPageView.current) {
            hasTrackedPageView.current = true;
            capture('test_run_detail_viewed', {
                run_id: run.id,
                run_status: run.status,
                persona: run.persona,
            });
        }
    }, [run]);

    // Hooks must be called before any conditional returns (Rules of Hooks)
    const { passedCount, failedCount, healedCount } = useMemo(() => {
        let passed = 0, failed = 0, healed = 0
        for (const s of logs) {
            if (s.status === 'SUCCESS') passed++
            else if (s.status === 'FAILED') failed++
            if (s.isHealed) healed++
        }
        return { passedCount: passed, failedCount: failed, healedCount: healed }
    }, [logs])

    if (loading) return <div className="flex justify-center p-8"><Loader2 className="animate-spin" /></div>
    if (!run) return <div className="p-8">Run not found</div>

    return (
        <div className="p-8 space-y-6">
            <Link href="/dashboard">
                <Button variant="ghost" className="pl-0 text-muted-foreground hover:text-foreground"><ArrowLeft className="mr-2 h-4 w-4" /> Back to Runs</Button>
            </Link>

            <div className="flex items-center space-x-4">
                <h1 className="text-3xl font-bold">Run Details</h1>
                <Badge variant={run.status === 'COMPLETED' ? 'success' : run.status === 'FAILED' ? 'destructive' : 'secondary'}>
                    {getStatusLabel(run.status)}
                </Badge>
                {['PENDING', 'RUNNING'].includes(run.status) && (
                    <Badge variant="outline" className="border-emerald-500/50 text-emerald-400 animate-pulse">
                        <Radio className="h-3 w-3 mr-1" />
                        Live
                    </Badge>
                )}
            </div>

            {/* Progress Bar (when running or pending) */}
            {['PENDING', 'RUNNING'].includes(run.status) && (
                <Card>
                    <CardContent className="p-4">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-sm text-muted-foreground">Progress</span>
                            <span className="text-sm font-medium">
                                Step {run.executedStepCount || 0} of {run.totalStepCount || '?'}
                            </span>
                        </div>
                        <div
                            className="w-full bg-secondary rounded-full h-2"
                            role="progressbar"
                            aria-valuenow={run.progressPercent || 0}
                            aria-valuemin={0}
                            aria-valuemax={100}
                            aria-label={`Test run progress: ${run.progressPercent || 0}% complete, step ${run.executedStepCount || 0} of ${run.totalStepCount || 'unknown'}`}
                        >
                            <div
                                className="bg-emerald-500 h-2 rounded-full transition-all duration-500"
                                style={{ width: `${run.progressPercent || 0}%` }}
                            />
                        </div>
                        {run.progressPercent !== undefined && run.progressPercent > 0 && (
                            <div className="text-xs text-muted-foreground mt-2 text-right">
                                {run.progressPercent}% complete
                            </div>
                        )}
                    </CardContent>
                </Card>
            )}

            {/* Final Report (when summary available) */}
            {run.summary && (
                <FinalReport
                    runId={run.id}
                    summary={run.summary}
                    steps={logs.map(log => ({
                        action: log.step?.action || 'unknown',
                        target: log.step?.target || 'unknown',
                        success: log.status === 'SUCCESS',
                        durationMs: log.durationMs || 0,
                        performanceMetrics: log.performanceMetrics
                    }))}
                    createdAt={run.createdAt}
                />
            )}

            {/* Summary Generation Loading State - only show for GENERATING or FAILED status */}
            {!run.summary && ['COMPLETED', 'FAILED', 'TIMEOUT'].includes(run.status) &&
             (run.summaryStatus === 'GENERATING' || run.summaryStatus === 'FAILED') && (
                <Card className={run.summaryStatus === 'FAILED' ? "border-red-500/30 bg-red-500/5" : "border-sky-500/30 bg-sky-500/5"}>
                    <CardContent className="p-6">
                        <div className="flex items-center gap-4">
                            {run.summaryStatus === 'FAILED' ? (
                                <>
                                    <div className="h-10 w-10 rounded-full bg-red-500/10 flex items-center justify-center">
                                        <X className="h-5 w-5 text-red-500" />
                                    </div>
                                    <div>
                                        <h3 className="font-medium text-foreground">Summary Generation Failed</h3>
                                        <p className="text-sm text-muted-foreground">
                                            The AI summary could not be generated. The test results are still available below.
                                        </p>
                                    </div>
                                </>
                            ) : (
                                <>
                                    <div className="h-10 w-10 rounded-full bg-sky-500/10 flex items-center justify-center">
                                        <Loader2 className="h-5 w-5 text-sky-500 animate-spin" />
                                    </div>
                                    <div>
                                        <h3 className="font-medium text-foreground">Generating AI Summary</h3>
                                        <p className="text-sm text-muted-foreground">
                                            Our AI is analyzing the test results to generate insights and recommendations.
                                            This may take a few minutes depending on the complexity of the test run...
                                        </p>
                                    </div>
                                </>
                            )}
                        </div>
                    </CardContent>
                </Card>
            )}

            {/* Stats Grid */}
            <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                <Card>
                    <CardContent className="p-4 text-center">
                        <div className="text-2xl font-bold text-emerald-600 dark:text-emerald-400">{passedCount}</div>
                        <div className="text-xs text-muted-foreground">Passed</div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="p-4 text-center">
                        <div className="text-2xl font-bold text-red-600 dark:text-red-400">{failedCount}</div>
                        <div className="text-xs text-muted-foreground">Failed</div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="p-4 text-center">
                        <div className="text-2xl font-bold text-purple-600 dark:text-purple-400">{healedCount}</div>
                        <div className="text-xs text-muted-foreground">Auto-Healed</div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="p-4 text-center">
                        <div className="text-2xl font-bold text-foreground">{logs.length}</div>
                        <div className="text-xs text-muted-foreground">Total Steps</div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="p-4 text-center">
                        <div className="text-sm font-mono text-foreground truncate">{run.persona || 'STANDARD'}</div>
                        <div className="text-xs text-muted-foreground">Persona</div>
                    </CardContent>
                </Card>
            </div>

            {/* Target URL Card */}
            <Card>
                <CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Target URL</CardTitle></CardHeader>
                <CardContent className="break-all font-mono text-sm">{run.targetUrl}</CardContent>
            </Card>

            {/* Timeline View */}
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <Clock className="h-5 w-5 text-muted-foreground" />
                        Execution Timeline
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="relative">
                        {/* Timeline line */}
                        <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-border" />

                        <div className="space-y-6">
                            {logs.length === 0 ? (
                                <p className="text-muted-foreground pl-12">No steps executed yet.</p>
                            ) : (
                                logs.map((step, i) => (
                                    <div key={i} className="relative pl-12">
                                        {/* Timeline dot */}
                                        <div className={`absolute left-2 w-5 h-5 rounded-full border-2 flex items-center justify-center
                                            ${step.status === 'SUCCESS' ? 'bg-emerald-500/20 border-emerald-500' :
                                                step.status === 'FAILED' ? 'bg-red-500/20 border-red-500' :
                                                    'bg-secondary border-border'}`}>
                                            {step.status === 'SUCCESS' && <Check className="h-3 w-3 text-emerald-600 dark:text-emerald-400" />}
                                            {step.status === 'FAILED' && <X className="h-3 w-3 text-red-600 dark:text-red-400" />}
                                        </div>

                                        <div className="bg-muted/50 rounded-lg p-4 border border-border">
                                            {/* Step Header */}
                                            <div className="flex items-center justify-between mb-2">
                                                <div className="flex items-center gap-2">
                                                    <span className="text-sm font-medium text-foreground">Step {i + 1}</span>
                                                    <span className="font-bold uppercase text-xs text-emerald-600 dark:text-emerald-400">
                                                        {step.step?.action || 'action'}
                                                    </span>
                                                    {step.isHealed && (
                                                        <Badge variant="outline" className="border-purple-500/50 text-purple-400 text-xs">
                                                            <Sparkles className="h-3 w-3 mr-1" /> Auto-Healed
                                                        </Badge>
                                                    )}
                                                </div>
                                                <div className="flex items-center gap-3 text-xs text-muted-foreground">
                                                    {step.durationMs > 0 && <span>{step.durationMs}ms</span>}
                                                    {step.executedAt && <span>{format(new Date(step.executedAt), "HH:mm:ss")}</span>}
                                                </div>
                                            </div>

                                            {/* Step Target */}
                                            <div className="bg-secondary/50 p-3 rounded text-xs font-mono overflow-x-auto whitespace-pre-wrap text-foreground">
                                                <span className="text-muted-foreground">Target: </span>{step.step?.target || 'unknown'}
                                                {step.errorMessage && (
                                                    <div className="mt-2 text-red-600 dark:text-red-400">
                                                        <span className="text-red-700 dark:text-red-500">Error: </span>{step.errorMessage}
                                                    </div>
                                                )}
                                            </div>

                                            {/* Screenshot - only show if not failed to load */}
                                            {step.hasScreenshot && !failedScreenshots.has(i) && (
                                                <div className="mt-3">
                                                    <div className="flex items-center gap-2 text-xs text-muted-foreground mb-2">
                                                        <ImageIcon className="h-3 w-3" /> Screenshot
                                                    </div>
                                                    <img
                                                        src={`${API_BASE_URL}/artifacts/${id}/${i}`}
                                                        alt={`Step ${i + 1} screenshot`}
                                                        className="rounded-lg border border-border w-full object-cover object-top bg-background"
                                                        style={{ maxHeight: '600px' }}
                                                        loading="lazy"
                                                        onError={() => {
                                                            setFailedScreenshots(prev => new Set(prev).add(i))
                                                        }}
                                                    />
                                                </div>
                                            )}

                                            {/* AI Optimization Suggestion */}
                                            {step.optimizationSuggestion && (
                                                <div className="mt-3 p-3 rounded-lg bg-sky-500/10 border border-sky-500/30">
                                                    <div className="flex items-center gap-2 text-sky-600 dark:text-sky-400 mb-2">
                                                        <Lightbulb className="h-4 w-4" />
                                                        <span className="text-sm font-medium">AI Optimization Tip</span>
                                                    </div>
                                                    <p className="text-sm text-foreground">
                                                        {step.optimizationSuggestion.split(DATA_TESTID_RE).map((part, idx) =>
                                                            part.startsWith('data-testid') ? (
                                                                <code key={idx} className="bg-secondary px-1.5 py-0.5 rounded text-sky-600 dark:text-sky-300 font-mono text-xs">{part}</code>
                                                            ) : (
                                                                <span key={idx}>{part}</span>
                                                            )
                                                        )}
                                                    </p>
                                                </div>
                                            )}

                                            {/* Performance Metrics for measure_performance steps */}
                                            {step.step?.action === 'measure_performance' && step.performanceMetrics && (
                                                <div className="mt-3 p-3 rounded-lg bg-blue-500/10 border border-blue-500/30">
                                                    <div className="flex items-center gap-2 text-blue-600 dark:text-blue-400 mb-3">
                                                        <Gauge className="h-4 w-4" />
                                                        <span className="text-sm font-medium">Performance Metrics</span>
                                                    </div>

                                                    {/* Web Vitals Grid */}
                                                    <div className="grid grid-cols-2 md:grid-cols-4 gap-2 mb-3">
                                                        {step.performanceMetrics.webVitals.lcp !== undefined && (
                                                            <div className="bg-background/50 rounded p-2">
                                                                <div className="text-xs text-muted-foreground">LCP</div>
                                                                <div className="text-sm font-bold text-foreground">
                                                                    {(step.performanceMetrics.webVitals.lcp / 1000).toFixed(2)}s
                                                                </div>
                                                            </div>
                                                        )}
                                                        {step.performanceMetrics.webVitals.cls !== undefined && (
                                                            <div className="bg-background/50 rounded p-2">
                                                                <div className="text-xs text-muted-foreground">CLS</div>
                                                                <div className="text-sm font-bold text-foreground">
                                                                    {step.performanceMetrics.webVitals.cls.toFixed(3)}
                                                                </div>
                                                            </div>
                                                        )}
                                                        {step.performanceMetrics.webVitals.ttfb !== undefined && (
                                                            <div className="bg-background/50 rounded p-2">
                                                                <div className="text-xs text-muted-foreground">TTFB</div>
                                                                <div className="text-sm font-bold text-foreground">
                                                                    {step.performanceMetrics.webVitals.ttfb.toFixed(0)}ms
                                                                </div>
                                                            </div>
                                                        )}
                                                        {step.performanceMetrics.webVitals.fcp !== undefined && (
                                                            <div className="bg-background/50 rounded p-2">
                                                                <div className="text-xs text-muted-foreground">FCP</div>
                                                                <div className="text-sm font-bold text-foreground">
                                                                    {(step.performanceMetrics.webVitals.fcp / 1000).toFixed(2)}s
                                                                </div>
                                                            </div>
                                                        )}
                                                    </div>

                                                    {/* Navigation Timing */}
                                                    {step.performanceMetrics.navigation.pageLoad && (
                                                        <div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
                                                            <span>
                                                                <strong className="text-foreground">Page Load:</strong>{' '}
                                                                {(step.performanceMetrics.navigation.pageLoad / 1000).toFixed(2)}s
                                                            </span>
                                                            {step.performanceMetrics.navigation.domContentLoaded && (
                                                                <span>
                                                                    <strong className="text-foreground">DOM Ready:</strong>{' '}
                                                                    {(step.performanceMetrics.navigation.domContentLoaded / 1000).toFixed(2)}s
                                                                </span>
                                                            )}
                                                            {step.performanceMetrics.totalResources && (
                                                                <span>
                                                                    <strong className="text-foreground">Resources:</strong>{' '}
                                                                    {step.performanceMetrics.totalResources}
                                                                </span>
                                                            )}
                                                        </div>
                                                    )}

                                                    {/* Performance Issues */}
                                                    {step.performanceMetrics.issues && step.performanceMetrics.issues.length > 0 && (() => {
                                                        const issues = step.performanceMetrics!.issues!;
                                                        const critical = issues.filter(i => i.severity === 'CRITICAL').length;
                                                        const high = issues.filter(i => i.severity === 'HIGH').length;
                                                        const medium = issues.filter(i => i.severity === 'MEDIUM').length;
                                                        return (
                                                            <div className="mt-2 pt-2 border-t border-blue-500/20 space-y-1.5">
                                                                <div className="text-xs text-amber-600 dark:text-amber-400 flex flex-wrap gap-2">
                                                                    {critical > 0 && <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-red-500" />{critical} critical</span>}
                                                                    {high > 0 && <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-orange-500" />{high} high</span>}
                                                                    {medium > 0 && <span className="inline-flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-yellow-500" />{medium} medium</span>}
                                                                </div>
                                                                <ul className="space-y-1">
                                                                    {issues.map((issue, idx) => (
                                                                        <li key={idx} className="text-xs text-muted-foreground flex items-start gap-1.5">
                                                                            <span className={`mt-0.5 w-1.5 h-1.5 rounded-full shrink-0 ${
                                                                                issue.severity === 'CRITICAL' ? 'bg-red-500' :
                                                                                issue.severity === 'HIGH' ? 'bg-orange-500' :
                                                                                issue.severity === 'MEDIUM' ? 'bg-yellow-500' : 'bg-slate-400'
                                                                            }`} />
                                                                            <span className="truncate">{issue.message}</span>
                                                                        </li>
                                                                    ))}
                                                                </ul>
                                                            </div>
                                                        );
                                                    })()}
                                                </div>
                                            )}

                                            {/* Diagnostics Panel */}
                                            <StepDiagnostics
                                                networkErrors={step.networkErrors}
                                                consoleErrors={step.consoleErrors}
                                                a11yWarnings={step.accessibilityWarnings}
                                            />
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* Timestamps */}
            <div className="flex items-center gap-4 text-xs text-muted-foreground">
                <span>Created: {format(new Date(run.createdAt), "PPpp")}</span>
                <span>•</span>
                <span className="font-mono">{run.id}</span>
            </div>
        </div>
    )
}
