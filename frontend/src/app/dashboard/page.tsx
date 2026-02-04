"use client"

import { useEffect, useState, useCallback, useMemo } from "react"
import { api } from "@/lib/api"
import { TestRun, PagedResult, TestRunStatus, Persona } from "@/types"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Loader2, RefreshCw, FileText, CheckCircle2, XCircle, Clock, Zap } from "lucide-react"
import Link from "next/link"
import { toast } from "sonner"
import { format } from "date-fns"
import { CreateRunDialog } from "@/components/dashboard/create-run-dialog"
import { Button } from "@/components/ui/button"

const POLL_INTERVAL = 5000 // 5 seconds

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
    const [runs, setRuns] = useState<TestRun[]>([])
    const [loading, setLoading] = useState(true)

    // Derive all stats in a single pass
    const stats = useMemo(() => {
        let passed = 0, failed = 0, active = 0
        for (const r of runs) {
            if (r.status === 'COMPLETED') passed++
            else if (r.status === 'FAILED' || r.status === 'TIMEOUT') failed++
            else if (r.status === 'RUNNING' || r.status === 'PENDING') active++
        }
        return { total: runs.length, passed, failed, active }
    }, [runs])

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

    return (
        <div className="p-6 md:p-8 space-y-8">
            {/* Summary Stats */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="rounded-xl border border-border bg-card p-4">
                    <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                            <Zap className="h-5 w-5 text-primary" />
                        </div>
                        <div>
                            <p className="text-sm text-muted-foreground">Total Runs</p>
                            <p className="text-2xl font-bold text-foreground">{stats.total}</p>
                        </div>
                    </div>
                </div>
                <div className="rounded-xl border border-border bg-card p-4">
                    <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-500/10">
                            <CheckCircle2 className="h-5 w-5 text-emerald-500" />
                        </div>
                        <div>
                            <p className="text-sm text-muted-foreground">Passed</p>
                            <p className="text-2xl font-bold text-foreground">{stats.passed}</p>
                        </div>
                    </div>
                </div>
                <div className="rounded-xl border border-border bg-card p-4">
                    <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-destructive/10">
                            <XCircle className="h-5 w-5 text-destructive" />
                        </div>
                        <div>
                            <p className="text-sm text-muted-foreground">Failed</p>
                            <p className="text-2xl font-bold text-foreground">{stats.failed}</p>
                        </div>
                    </div>
                </div>
                <div className="rounded-xl border border-border bg-card p-4">
                    <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-500/10">
                            <Clock className="h-5 w-5 text-amber-500" />
                        </div>
                        <div>
                            <p className="text-sm text-muted-foreground">Active</p>
                            <p className="text-2xl font-bold text-foreground">{stats.active}</p>
                        </div>
                    </div>
                </div>
            </div>

            {/* Test Runs Section */}
            <div className="space-y-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h2 className="text-3xl font-bold tracking-tight">Test Runs</h2>
                        <p className="text-muted-foreground mt-2">Manage and monitor your autonomous QA sessions.</p>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="outline"
                            size="icon"
                            onClick={handleManualRefresh}
                            disabled={loading}
                            aria-label="Refresh test runs"
                        >
                            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
                        </Button>
                        <CreateRunDialog onSuccess={fetchRuns} />
                    </div>
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
                                                <Zap className="h-7 w-7 text-muted-foreground" />
                                            </div>
                                            <div>
                                                <p className="font-medium text-foreground">No test runs yet</p>
                                                <p className="text-sm text-muted-foreground mt-1">Create your first test run to get started.</p>
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
