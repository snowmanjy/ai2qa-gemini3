"use client";

import Image from "next/image";
import { CheckCircle2, XCircle, Bug, Zap, ChevronRight, Gauge, AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";

interface RunSummary {
    status: "SUCCESS" | "FAILURE";
    goalOverview: string;
    outcomeShort: string;
    failureAnalysis: string | null;
    actionableFix: string | null;
    keyAchievements: string[];
    healthCheck?: {
        networkIssues: { count: number; summary: string };
        consoleIssues: { count: number; summary: string };
        accessibilityScore: string;
        accessibilitySummary: string;
    };
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
    action: string;
    target: string;
    success: boolean;
    durationMs: number;
    screenshotPath?: string;
    performanceMetrics?: PerformanceMetrics;
}

interface FinalReportProps {
    runId: string;
    summary: RunSummary;
    steps: ExecutedStep[];
    createdAt: string;
}

/**
 * Enterprise-grade report component with branded layout.
 * 
 * - Green theme for SUCCESS
 * - Red theme with diagnostic sections for FAILURE
 * - React handles branding, AI fills structured content
 */
export function FinalReport({ runId, summary, steps, createdAt }: FinalReportProps) {
    const isSuccess = summary.status === "SUCCESS";
    const shortRunId = runId.substring(0, 8);

    return (
        <div className="a4-paper-shadow mx-auto max-w-4xl bg-white p-8 md:p-12 rounded-lg">
            {/* --- HEADER (Branding) --- */}
            <div className="flex items-center justify-between border-b border-slate-200 pb-6">
                <div className="flex items-center gap-3">
                    {/* Logo */}
                    <Image
                        src="/logo.png"
                        alt="Ai2QA Logo"
                        width={120}
                        height={120}
                        className="h-20 w-auto object-contain"
                        priority
                    />
                    <div className="text-center">
                        <h1 className="text-xl font-bold text-slate-900">Ai2QA™ Report</h1>
                        <p className="text-xs text-slate-500">Autonomous Test Execution</p>
                    </div>
                </div>
                <div className="text-right">
                    <p className="text-sm font-medium text-slate-700">Run #{shortRunId}</p>
                    <p className="text-xs text-slate-500">{new Date(createdAt).toLocaleDateString()}</p>
                </div>
            </div>

            {/* --- STATUS BANNER --- */}
            <div className={cn(
                "mt-8 rounded-lg p-6 border",
                isSuccess
                    ? "bg-gradient-to-r from-emerald-50 to-green-50 border-emerald-200"
                    : "bg-gradient-to-r from-red-50 to-rose-50 border-red-200"
            )}>
                <div className="flex items-center gap-3">
                    {isSuccess ? (
                        <CheckCircle2 className="h-8 w-8 text-emerald-600" />
                    ) : (
                        <XCircle className="h-8 w-8 text-red-600" />
                    )}
                    <div>
                        <h2 className={cn(
                            "text-2xl font-bold",
                            isSuccess ? "text-emerald-700" : "text-red-700"
                        )}>
                            {isSuccess ? "Test Execution Successful" : "Test Execution Failed"}
                        </h2>
                        <p className="mt-1 text-slate-700">{summary.outcomeShort}</p>
                    </div>
                </div>
            </div>

            {/* --- HEALTH CHECK SECTION --- */}
            {summary.healthCheck && (
                summary.healthCheck.networkIssues.count > 0 ||
                summary.healthCheck.consoleIssues.count > 0 ||
                (summary.healthCheck.accessibilityScore && summary.healthCheck.accessibilityScore !== "N/A")
            ) && (
                <section className="mt-6 rounded-md border-l-4 border-yellow-400 bg-yellow-50 p-4">
                    <h3 className="text-sm font-bold uppercase text-yellow-800 mb-3">
                        <AlertTriangle className="inline h-4 w-4 mr-1 -mt-0.5" />
                        Health Check
                    </h3>

                    <div className="space-y-2">
                        {/* Network Issues */}
                        {summary.healthCheck.networkIssues.count > 0 && (
                            <div className="text-sm text-yellow-800">
                                <strong>Network Issues ({summary.healthCheck.networkIssues.count}):</strong>{" "}
                                <span className="text-yellow-700">{summary.healthCheck.networkIssues.summary}</span>
                            </div>
                        )}

                        {/* Console Issues */}
                        {summary.healthCheck.consoleIssues.count > 0 && (
                            <div className="text-sm text-yellow-800">
                                <strong>Console Issues ({summary.healthCheck.consoleIssues.count}):</strong>{" "}
                                <span className="text-yellow-700">{summary.healthCheck.consoleIssues.summary}</span>
                            </div>
                        )}

                        {/* Accessibility */}
                        {summary.healthCheck.accessibilityScore && summary.healthCheck.accessibilityScore !== "N/A" && (
                            <div className="text-sm text-yellow-800">
                                <strong>Accessibility: {summary.healthCheck.accessibilityScore}</strong>
                                {summary.healthCheck.accessibilitySummary && (
                                    <span className="text-yellow-700"> — {summary.healthCheck.accessibilitySummary}</span>
                                )}
                            </div>
                        )}
                    </div>

                    {(summary.healthCheck.networkIssues.count > 0 || summary.healthCheck.consoleIssues.count > 0) && (
                        <p className="mt-2 text-xs text-yellow-600">
                            These are health observations, not test failures. Review for potential hidden bugs.
                        </p>
                    )}
                </section>
            )}

            {/* --- DYNAMIC SECTIONS --- */}
            <div className="mt-8 space-y-8">

                {/* Section 1: The Goal */}
                <section>
                    <h3 className="font-bold text-slate-900 uppercase tracking-wide text-xs mb-2">
                        Test Goal
                    </h3>
                    <p className="text-slate-700 leading-relaxed">{summary.goalOverview}</p>
                </section>

                {/* Section 2: Failure Analysis (Only if Failed) */}
                {!isSuccess && summary.failureAnalysis && (
                    <section className="rounded-lg bg-slate-50 p-5 border border-slate-200">
                        <h3 className="font-bold text-red-600 uppercase tracking-wide text-xs flex items-center gap-2 mb-3">
                            <Bug className="h-4 w-4" /> Root Cause Analysis
                        </h3>
                        <p className="text-slate-800 font-medium leading-relaxed">
                            {summary.failureAnalysis}
                        </p>

                        {summary.actionableFix && (
                            <div className="mt-4 border-t border-slate-200 pt-4">
                                <h4 className="text-sm font-bold text-slate-900 flex items-center gap-1.5 mb-2">
                                    <Zap className="h-4 w-4 text-amber-500" /> Suggested Fix
                                </h4>
                                <code className="block rounded-md bg-slate-900 p-4 text-sm text-green-400 font-mono overflow-x-auto">
                                    {summary.actionableFix}
                                </code>
                            </div>
                        )}
                    </section>
                )}

                {/* Section 3: Key Achievements */}
                {summary.keyAchievements.length > 0 && (
                    <section>
                        <h3 className="font-bold text-slate-900 uppercase tracking-wide text-xs mb-3">
                            Key Achievements
                        </h3>
                        <ul className="space-y-2">
                            {summary.keyAchievements.map((achievement, idx) => (
                                <li key={idx} className="flex items-start gap-2 text-slate-700">
                                    <ChevronRight className="h-4 w-4 text-emerald-500 mt-0.5 flex-shrink-0" />
                                    <span>{achievement}</span>
                                </li>
                            ))}
                        </ul>
                    </section>
                )}

                {/* Section 3.5: Performance Metrics (if any measure_performance steps) */}
                <PerformanceSection steps={steps} />

                {/* Section 4: Execution Steps Table */}
                <section>
                    <h3 className="font-bold text-slate-900 uppercase tracking-wide text-xs mb-3">
                        Execution Steps ({steps.length} total)
                    </h3>
                    <div className="overflow-hidden rounded-lg border border-slate-200">
                        <table className="w-full text-sm">
                            <thead className="bg-slate-50">
                                <tr>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase">#</th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase">Action</th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase">Target</th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase">Status</th>
                                    <th className="px-4 py-3 text-right text-xs font-medium text-slate-500 uppercase">Time</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {steps.map((step, idx) => (
                                    <tr key={idx} className={cn(
                                        "hover:bg-slate-50 transition-colors",
                                        !step.success && "bg-red-50/50"
                                    )}>
                                        <td className="px-4 py-3 text-slate-500 font-mono">{idx + 1}</td>
                                        <td className="px-4 py-3 font-medium text-slate-900">{step.action}</td>
                                        <td className="px-4 py-3 text-slate-600 truncate max-w-[200px]">{step.target}</td>
                                        <td className="px-4 py-3">
                                            {step.success ? (
                                                <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700 bg-emerald-100 px-2 py-0.5 rounded-full">
                                                    <CheckCircle2 className="h-3 w-3" /> Pass
                                                </span>
                                            ) : (
                                                <span className="inline-flex items-center gap-1 text-xs font-medium text-red-700 bg-red-100 px-2 py-0.5 rounded-full">
                                                    <XCircle className="h-3 w-3" /> Fail
                                                </span>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-right text-slate-500 font-mono text-xs">
                                            {step.durationMs}ms
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </section>
            </div>

            {/* --- FOOTER (Branding) --- */}
            <div className="mt-12 border-t border-slate-200 pt-6 text-center">
                <p className="text-xs text-slate-400">
                    Generated by <strong className="text-slate-600">Ai2QA™ Autonomous Agent</strong>
                </p>
                <p className="mt-1 text-xs text-slate-400">
                    {isSuccess
                        ? "✓ Review Not Required"
                        : "⚠ Action Required - Please review failure analysis"}
                </p>
            </div>
        </div>
    );
}

/**
 * Performance metrics section showing Core Web Vitals and issues.
 */
function PerformanceSection({ steps }: { steps: ExecutedStep[] }) {
    // Find all steps with performance metrics
    const performanceSteps = steps.filter(
        (step) => step.action === "measure_performance" && step.performanceMetrics
    );

    if (performanceSteps.length === 0) {
        return null;
    }

    // Aggregate metrics from the last performance measurement (most complete)
    const latestMetrics = performanceSteps[performanceSteps.length - 1].performanceMetrics!;
    const allIssues = performanceSteps.flatMap((s) => s.performanceMetrics?.issues || []);

    // Deduplicate issues by message
    const uniqueIssues = allIssues.filter(
        (issue, index, self) => index === self.findIndex((i) => i.message === issue.message)
    );

    const criticalCount = uniqueIssues.filter((i) => i.severity === "CRITICAL").length;
    const highCount = uniqueIssues.filter((i) => i.severity === "HIGH").length;

    return (
        <section>
            <h3 className="font-bold text-slate-900 uppercase tracking-wide text-xs mb-3 flex items-center gap-2">
                <Gauge className="h-4 w-4 text-blue-600" />
                Performance Metrics (Core Web Vitals)
            </h3>

            {/* Web Vitals Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
                <WebVitalCard
                    label="LCP"
                    value={latestMetrics.webVitals.lcp}
                    unit="ms"
                    thresholds={{ good: 2500, poor: 4000 }}
                    description="Largest Contentful Paint"
                    explanation="How long until the main content (largest image or text block) becomes visible. This is what users perceive as 'page loaded'."
                    impact="Slow LCP frustrates users — they see a blank or incomplete page. Google uses this for search ranking."
                />
                <WebVitalCard
                    label="CLS"
                    value={latestMetrics.webVitals.cls}
                    unit=""
                    thresholds={{ good: 0.1, poor: 0.25 }}
                    description="Cumulative Layout Shift"
                    explanation="How much the page content jumps around while loading. A score of 0 means nothing moved unexpectedly."
                    impact="High CLS causes accidental clicks when buttons shift. Very annoying for users trying to interact."
                    isDecimal
                />
                <WebVitalCard
                    label="TTFB"
                    value={latestMetrics.webVitals.ttfb}
                    unit="ms"
                    thresholds={{ good: 800, poor: 1800 }}
                    description="Time to First Byte"
                    explanation="How long the server takes to respond. This is before anything appears on screen — pure server/network delay."
                    impact="Slow TTFB means server issues (overloaded, far away, or slow database). Everything else waits on this."
                />
                <WebVitalCard
                    label="FCP"
                    value={latestMetrics.webVitals.fcp}
                    unit="ms"
                    thresholds={{ good: 1800, poor: 3000 }}
                    description="First Contentful Paint"
                    explanation="When the first text or image appears. The moment users know 'something is happening'."
                    impact="Slow FCP makes users think the site is broken. They may leave before seeing any content."
                />
            </div>

            {/* Navigation Timing - Additional Context */}
            {latestMetrics.navigation.pageLoad && (
                <div className="mb-4 rounded-md bg-slate-50 p-4 border border-slate-200">
                    <h4 className="text-sm font-bold text-slate-700 mb-3">Page Loading Timeline</h4>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                        <div>
                            <div className="text-lg font-bold text-slate-900">
                                {formatMs(latestMetrics.navigation.pageLoad)}
                            </div>
                            <div className="text-xs font-medium text-slate-600">Total Page Load</div>
                            <div className="text-xs text-slate-400">Everything finished loading</div>
                        </div>
                        {latestMetrics.navigation.domContentLoaded && (
                            <div>
                                <div className="text-lg font-bold text-slate-900">
                                    {formatMs(latestMetrics.navigation.domContentLoaded)}
                                </div>
                                <div className="text-xs font-medium text-slate-600">DOM Ready</div>
                                <div className="text-xs text-slate-400">Page structure loaded</div>
                            </div>
                        )}
                        {latestMetrics.totalResources && (
                            <div>
                                <div className="text-lg font-bold text-slate-900">
                                    {latestMetrics.totalResources}
                                </div>
                                <div className="text-xs font-medium text-slate-600">Resources</div>
                                <div className="text-xs text-slate-400">Files downloaded (images, scripts, etc.)</div>
                            </div>
                        )}
                        {latestMetrics.totalTransferSizeKb && (
                            <div>
                                <div className="text-lg font-bold text-slate-900">
                                    {latestMetrics.totalTransferSizeKb} KB
                                </div>
                                <div className="text-xs font-medium text-slate-600">Data Transfer</div>
                                <div className="text-xs text-slate-400">Total download size</div>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Performance Issues */}
            {uniqueIssues.length > 0 && (
                <div className="rounded-md border border-amber-200 bg-amber-50 p-4">
                    <h4 className="text-sm font-bold text-amber-800 flex items-center gap-1.5 mb-1">
                        <AlertTriangle className="h-4 w-4" />
                        Performance Issues Found
                    </h4>
                    <p className="text-xs text-amber-700 mb-3">
                        {criticalCount > 0 && (
                            <span className="font-medium">
                                {criticalCount} critical issue{criticalCount > 1 ? "s" : ""} affecting user experience.{" "}
                            </span>
                        )}
                        {highCount > 0 && (
                            <span>
                                {highCount} high-priority issue{highCount > 1 ? "s" : ""} to address.
                            </span>
                        )}
                    </p>
                    <ul className="space-y-2 text-sm">
                        {uniqueIssues.map((issue, idx) => (
                            <li key={idx} className="flex items-start gap-2 bg-white/50 rounded p-2">
                                <span
                                    className={cn(
                                        "inline-block px-2 py-0.5 text-xs font-bold rounded shrink-0",
                                        issue.severity === "CRITICAL" && "bg-red-600 text-white",
                                        issue.severity === "HIGH" && "bg-orange-500 text-white",
                                        issue.severity === "MEDIUM" && "bg-yellow-500 text-white",
                                        issue.severity === "LOW" && "bg-slate-400 text-white"
                                    )}
                                >
                                    {issue.severity}
                                </span>
                                <div>
                                    <span className="text-slate-800 font-medium">{issue.message}</span>
                                    {issue.severity === "CRITICAL" && (
                                        <p className="text-xs text-slate-600 mt-0.5">
                                            ⚠️ This significantly impacts user experience and may cause users to leave.
                                        </p>
                                    )}
                                </div>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </section>
    );
}

/**
 * Individual Web Vital metric card with color coding and explanations.
 */
function WebVitalCard({
    label,
    value,
    unit,
    thresholds,
    description,
    explanation,
    impact,
    isDecimal = false,
}: {
    label: string;
    value?: number;
    unit: string;
    thresholds: { good: number; poor: number };
    description: string;
    explanation: string;
    impact: string;
    isDecimal?: boolean;
}) {
    // Determine rating and colors
    const getRating = (val: number | undefined) => {
        if (val === undefined) return { label: "No Data", color: "slate" };
        if (val <= thresholds.good) return { label: "Good", color: "emerald" };
        if (val >= thresholds.poor) return { label: "Poor", color: "red" };
        return { label: "Needs Work", color: "amber" };
    };

    const rating = getRating(value);

    const colorClasses = {
        emerald: {
            bg: "bg-emerald-50 border-emerald-200",
            text: "text-emerald-700",
            badge: "bg-emerald-100 text-emerald-800",
        },
        amber: {
            bg: "bg-amber-50 border-amber-200",
            text: "text-amber-700",
            badge: "bg-amber-100 text-amber-800",
        },
        red: {
            bg: "bg-red-50 border-red-200",
            text: "text-red-700",
            badge: "bg-red-100 text-red-800",
        },
        slate: {
            bg: "bg-slate-50 border-slate-200",
            text: "text-slate-500",
            badge: "bg-slate-100 text-slate-600",
        },
    };

    const colors = colorClasses[rating.color as keyof typeof colorClasses];
    const displayValue = value === undefined
        ? "N/A"
        : isDecimal
            ? value.toFixed(3)
            : formatMs(value);

    // Format threshold for display
    const thresholdText = isDecimal
        ? `Good: ≤${thresholds.good} · Poor: ≥${thresholds.poor}`
        : `Good: ≤${formatMs(thresholds.good)} · Poor: ≥${formatMs(thresholds.poor)}`;

    return (
        <div className={cn("rounded-lg border p-4", colors.bg)}>
            {/* Header with label and rating badge */}
            <div className="flex items-center justify-between mb-2">
                <div className={cn("text-sm font-bold", colors.text)}>{label}</div>
                <span className={cn("text-xs font-medium px-2 py-0.5 rounded-full", colors.badge)}>
                    {rating.label}
                </span>
            </div>

            {/* Value */}
            <div className={cn("text-2xl font-bold mb-1", colors.text)}>
                {displayValue}
                {value !== undefined && !isDecimal && (
                    <span className="text-sm font-normal opacity-75">{unit}</span>
                )}
            </div>

            {/* Technical name */}
            <div className="text-xs font-medium text-slate-600 mb-2">{description}</div>

            {/* Plain English explanation */}
            <div className="text-xs text-slate-600 mb-2">
                <strong>What it measures:</strong> {explanation}
            </div>

            {/* Why it matters */}
            <div className="text-xs text-slate-600 mb-2">
                <strong>Why it matters:</strong> {impact}
            </div>

            {/* Thresholds */}
            <div className="text-xs text-slate-400 border-t border-slate-200 pt-2 mt-2">
                {thresholdText}
            </div>
        </div>
    );
}

/**
 * Format milliseconds to a human-readable string.
 */
function formatMs(ms: number): string {
    if (ms >= 1000) {
        return `${(ms / 1000).toFixed(2)}s`;
    }
    return `${Math.round(ms)}`;
}
