"use client"

import { useEffect, useState, useCallback } from "react"
import { useRouter } from "next/navigation"
import { useGoogleReCaptcha } from 'react-google-recaptcha-v3'
import { api } from "@/lib/api"
import { TestRun, PagedResult, TestRunStatus, Persona, CreateTestRunRequest } from "@/types"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Loader2, RefreshCw, FileText, Sparkles, Rocket, ChevronDown, ChevronRight } from "lucide-react"
import Link from "next/link"
import { toast } from "sonner"
import { format } from "date-fns"
import { PersonaSelector } from "@/components/dashboard/create-run/persona-selector"
import { capture, captureException } from "@/lib/analytics"

const POLL_INTERVAL = 5000 // 5 seconds
const MAX_CONTEXT_LENGTH = 200

// Pure helper functions (no component state dependency)
function getStatusBadgeVariant(status: TestRunStatus) {
    switch (status) {
        case 'COMPLETED': return 'success'
        case 'FAILED': return 'destructive'
        case 'RUNNING': return 'secondary'
        default: return 'outline'
    }
}

function getStatusLabel(status: TestRunStatus) {
    switch (status) {
        case 'COMPLETED': return 'PASSED'
        case 'TIMEOUT': return 'TIMED OUT'
        default: return status
    }
}

function getPersonaBadgeVariant(persona?: Persona) {
    switch (persona) {
        case 'CHAOS': return 'destructive'
        case 'HACKER': return 'outline'
        case 'PERFORMANCE_HAWK': return 'default'
        default: return 'secondary'
    }
}

function getPersonaLabel(persona?: Persona) {
    if (!persona) return 'Standard'
    if (persona === 'PERFORMANCE_HAWK') return 'Performance'
    return persona.charAt(0) + persona.slice(1).toLowerCase()
}

export default function DashboardPage() {
    const router = useRouter()
    const { executeRecaptcha } = useGoogleReCaptcha()

    // Test runs state
    const [runs, setRuns] = useState<TestRun[]>([])
    const [loading, setLoading] = useState(true)

    // Inline form state (default to PERFORMANCE_HAWK)
    const [url, setUrl] = useState("")
    const [persona, setPersona] = useState<Persona>('PERFORMANCE_HAWK')
    const [additionalContext, setAdditionalContext] = useState("")
    const [showContext, setShowContext] = useState(false)
    const [submitting, setSubmitting] = useState(false)

    const fetchRuns = useCallback(async () => {
        try {
            const res = await api.get<PagedResult<TestRun>>('/test-runs')
            setRuns(res.data.content)
        } catch (error) {
            console.error(error)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        fetchRuns()
    }, [fetchRuns])

    useEffect(() => {
        const hasActiveRuns = runs.some((run) =>
            run.status === 'PENDING' || run.status === 'RUNNING'
        )

        if (!hasActiveRuns) return

        const intervalId = setInterval(fetchRuns, POLL_INTERVAL)
        return () => clearInterval(intervalId)
    }, [runs, fetchRuns])

    const handleManualRefresh = async () => {
        setLoading(true)
        await fetchRuns()
        toast.success("Refreshed")
    }

    // Get reCAPTCHA token (returns undefined if reCAPTCHA is not configured)
    const getReCaptchaToken = useCallback(async (): Promise<string | undefined> => {
        if (!executeRecaptcha) {
            return undefined // reCAPTCHA not available (local dev mode)
        }
        try {
            return await executeRecaptcha('start_test')
        } catch (error) {
            console.warn('reCAPTCHA token generation failed:', error)
            return undefined
        }
    }, [executeRecaptcha])

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault()
        if (!url) return

        setSubmitting(true)
        try {
            // Get reCAPTCHA token (if enabled)
            const recaptchaToken = await getReCaptchaToken()

            const payload: CreateTestRunRequest = {
                targetUrl: url,
                goals: [], // No longer using goals array
                persona,
                recaptchaToken,
                additionalContext: additionalContext.trim() || undefined,
            }

            const res = await api.post('/test-runs', payload)

            // Track test run creation event
            capture('test_run_created', {
                target_url: url,
                persona: persona,
                has_additional_context: !!additionalContext.trim(),
            });

            // Reset form state
            setUrl("")
            setPersona('PERFORMANCE_HAWK')
            setAdditionalContext("")
            setShowContext(false)

            toast.success("Test run started successfully")
            fetchRuns()

            // Navigate to the new test run page for live updates
            if (res.data?.id) {
                router.push(`/dashboard/runs/${res.data.id}`)
            }
        } catch (error: unknown) {
            console.error(error)
            const err = error as {
                response?: {
                    status?: number;
                    data?: {
                        error?: string;
                        detail?: string;
                        title?: string;
                        message?: string;
                        errors?: Array<{ defaultMessage?: string }>;
                        remaining?: number;
                        dailyCap?: number;
                    }
                };
                message?: string
            }

            // Handle specific error types
            if (err.response?.data?.error === 'recaptcha_failed') {
                toast.error("Security verification failed. Please try again.")
            } else if (err.response?.data?.error === 'demo_limit_reached') {
                toast.error(
                    `Daily demo limit reached (${err.response.data.dailyCap} runs/day). Please try again tomorrow!`,
                    { duration: 5000 }
                )
            } else {
                // Extract meaningful error message from API response
                const errorMessage = err.response?.data?.detail
                    || err.response?.data?.title
                    || err.response?.data?.message
                    || err.response?.data?.errors?.[0]?.defaultMessage
                    || err.message
                    || "Failed to create run"
                toast.error(errorMessage)
            }
            captureException(error instanceof Error ? error : new Error(String(error)));
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="p-6 md:p-8 space-y-8">
            {/* Hackathon Header */}
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 pb-4 border-b border-border">
                <div>
                    <h1 className="text-2xl md:text-3xl font-bold text-foreground">AI-Powered QA Testing</h1>
                    <p className="text-muted-foreground mt-1">Enter a URL, choose a persona, watch AI find bugs</p>
                </div>
                <div className="flex items-center gap-3">
                    <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-gradient-to-r from-blue-600/30 to-purple-600/30 border border-blue-500/50 shadow-lg shadow-blue-500/20">
                        <Sparkles className="h-5 w-5 text-blue-400" />
                        <span className="text-sm font-semibold bg-gradient-to-r from-blue-400 to-purple-400 bg-clip-text text-transparent">
                            Powered by Gemini 3 Flash
                        </span>
                    </div>
                </div>
            </div>

            {/* Inline Test Creation Form */}
            <div className="rounded-xl border border-border bg-card p-6">
                <h2 className="text-xl font-semibold text-foreground mb-4">Start a New Test</h2>
                <form onSubmit={handleSubmit} className="space-y-6">
                    {/* URL Input */}
                    <div className="space-y-2">
                        <Label htmlFor="url">
                            Target URL <span className="text-destructive" aria-label="required">*</span>
                        </Label>
                        <Input
                            id="url"
                            placeholder="https://example.com"
                            value={url}
                            onChange={e => setUrl(e.target.value)}
                            required
                            aria-required="true"
                            className="max-w-2xl"
                        />
                    </div>

                    {/* Persona Selector */}
                    <PersonaSelector
                        selected={persona}
                        onChange={(val) => setPersona(val as Persona)}
                    />

                    {/* Collapsible Additional Context */}
                    <div className="space-y-2">
                        <button
                            type="button"
                            onClick={() => setShowContext(!showContext)}
                            className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
                        >
                            {showContext ? (
                                <ChevronDown className="h-4 w-4" />
                            ) : (
                                <ChevronRight className="h-4 w-4" />
                            )}
                            Additional Context (optional)
                        </button>
                        {showContext && (
                            <div className="space-y-1 max-w-2xl">
                                <Input
                                    id="additionalContext"
                                    value={additionalContext}
                                    onChange={(e) => setAdditionalContext(e.target.value.slice(0, MAX_CONTEXT_LENGTH))}
                                    placeholder="e.g., Focus on checkout flow, test mobile viewport..."
                                    maxLength={MAX_CONTEXT_LENGTH}
                                />
                                <p className="text-xs text-muted-foreground text-right">
                                    {additionalContext.length}/{MAX_CONTEXT_LENGTH}
                                </p>
                            </div>
                        )}
                    </div>

                    {/* Submit Button */}
                    <div className="flex justify-end">
                        <Button
                            type="submit"
                            disabled={submitting || !url}
                            size="lg"
                            className="bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700"
                        >
                            {submitting ? (
                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            ) : (
                                <Rocket className="mr-2 h-4 w-4" />
                            )}
                            Start Test Run
                        </Button>
                    </div>
                </form>
            </div>

            {/* Test Runs Section */}
            <div className="space-y-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h2 className="text-xl font-semibold text-foreground">Recent Runs</h2>
                        <p className="text-muted-foreground text-sm mt-1">Monitor your autonomous QA sessions</p>
                    </div>
                    <Button
                        variant="outline"
                        size="icon"
                        onClick={handleManualRefresh}
                        disabled={loading}
                        aria-label="Refresh test runs"
                    >
                        <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
                    </Button>
                </div>

                <div className="border border-border rounded-xl bg-card">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead scope="col">Status</TableHead>
                                <TableHead scope="col">Target URL</TableHead>
                                <TableHead scope="col">Persona</TableHead>
                                <TableHead scope="col">Created</TableHead>
                                <TableHead scope="col" className="w-20 text-center">Report</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {loading && runs.length === 0 ? (
                                <TableRow>
                                    <TableCell colSpan={5} className="h-24 text-center">
                                        <Loader2 className="h-6 w-6 animate-spin mx-auto text-muted-foreground" aria-hidden="true" />
                                        <span className="sr-only">Loading test runs...</span>
                                    </TableCell>
                                </TableRow>
                            ) : runs.length === 0 ? (
                                <TableRow>
                                    <TableCell colSpan={5} className="h-40 text-center">
                                        <div className="flex flex-col items-center gap-3">
                                            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-muted">
                                                <Rocket className="h-7 w-7 text-muted-foreground" />
                                            </div>
                                            <div>
                                                <p className="font-medium text-foreground">No test runs yet</p>
                                                <p className="text-sm text-muted-foreground mt-1">Enter a URL above to start your first test.</p>
                                            </div>
                                        </div>
                                    </TableCell>
                                </TableRow>
                            ) : (
                                runs.map((run) => (
                                    <TableRow key={run.id} className="hover:bg-accent/50 transition-colors">
                                        <TableCell>
                                            <Badge variant={getStatusBadgeVariant(run.status)}>
                                                {run.status === 'RUNNING' && (
                                                    <Loader2 className="mr-1 h-3 w-3 animate-spin" />
                                                )}
                                                {getStatusLabel(run.status)}
                                            </Badge>
                                        </TableCell>
                                        <TableCell>
                                            <a href={run.targetUrl} target="_blank" rel="noopener noreferrer" className="font-medium hover:underline text-[var(--link)]">
                                                {run.targetUrl}
                                            </a>
                                        </TableCell>
                                        <TableCell>
                                            <Badge variant={getPersonaBadgeVariant(run.persona)}>
                                                {getPersonaLabel(run.persona)}
                                            </Badge>
                                        </TableCell>
                                        <TableCell className="text-muted-foreground">
                                            {format(new Date(run.createdAt), "PP p")}
                                        </TableCell>
                                        <TableCell className="text-center">
                                            {(run.status === 'COMPLETED' || run.status === 'FAILED') ? (
                                                <Link href={`/dashboard/runs/${run.id}`} aria-label={`View report for ${run.targetUrl}`}>
                                                    <FileText className="h-5 w-5 text-muted-foreground hover:text-foreground inline-block transition-colors" aria-hidden="true" />
                                                </Link>
                                            ) : (
                                                <span className="text-muted-foreground text-xs">—</span>
                                            )}
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </div>
            </div>
        </div>
    )
}
