"use client"

import { useState, useEffect, useRef, useCallback } from "react"
import { useRouter } from "next/navigation"
import { useGoogleReCaptcha } from 'react-google-recaptcha-v3'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Plus, Loader2, Paperclip, FileText, Lock, HelpCircle } from "lucide-react"
import { api, extractPlan } from "@/lib/api"
import { toast } from "sonner"
import { CreateTestRunRequest, Persona } from "@/types"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { PersonaSelector } from "./create-run/persona-selector"
import { capture, captureException } from "@/lib/analytics"

interface CreateRunDialogProps {
    onSuccess: () => void
}

const PERSONA_TEMPLATES: Record<Persona, string> = {
    STANDARD: "Thoroughly explore the page by scrolling, clicking tabs, expanding sections, and opening menus to discover all content. Document each state with screenshots and verify elements load correctly.",
    CHAOS: "Thoroughly explore the page by scrolling, clicking tabs, and expanding sections. Then randomly interact with every discovered element — test edge cases, rapid clicks, unusual inputs — and observe for UI errors or crashes.",
    HACKER: "Thoroughly explore the page by scrolling, clicking tabs, and expanding sections to discover all input fields. For each input found (search bars, text fields, forms), probe for XSS and injection vulnerabilities using safe test payloads. Skip any expected inputs that don't exist on the page.",
    PERFORMANCE_HAWK: "Analyze page load performance by measuring time to interactive, Core Web Vitals (LCP, CLS, INP), and resource loading. Identify large images, render-blocking resources, and excessive API calls. Report any page loads over 3 seconds or UI interactions that feel sluggish."
}

const MAX_FILE_SIZE = 2 * 1024 * 1024 // 2MB

/**
 * Dialog for creating new test runs.
 */
export function CreateRunDialog({ onSuccess }: CreateRunDialogProps) {
    const router = useRouter()
    const { executeRecaptcha } = useGoogleReCaptcha()
    const [open, setOpen] = useState(false)
    const [url, setUrl] = useState("")
    const [persona, setPersona] = useState<Persona>('STANDARD')
    const [goals, setGoals] = useState(PERSONA_TEMPLATES.STANDARD)
    const [isGoalsModified, setIsGoalsModified] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const [uploading, setUploading] = useState(false)
    const fileInputRef = useRef<HTMLInputElement>(null)

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

    // Auto-fill logic
    useEffect(() => {
        if (!isGoalsModified) {
            setGoals(PERSONA_TEMPLATES[persona])
        }
    }, [persona, isGoalsModified])

    const handleGoalsChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
        setGoals(e.target.value)
        setIsGoalsModified(true)
    }

    const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0]
        if (!file) return

        // Validate file size
        if (file.size > MAX_FILE_SIZE) {
            toast.error("File is too large. Please reduce it to under 2MB.")
            if (fileInputRef.current) fileInputRef.current.value = ""
            return
        }

        setUploading(true)
        toast.info("AI is analyzing your plan...")
        try {
            const extractedGoals = await extractPlan(file)
            if (extractedGoals && extractedGoals.length > 0) {
                // If user hasn't modified the default template, REPLACE it
                // If user has customized goals, APPEND to them
                const newGoals = extractedGoals.join("\n")
                let finalGoals: string
                if (isGoalsModified) {
                    // User has custom goals - append
                    finalGoals = goals.trim() ? `${goals}\n${newGoals}` : newGoals
                } else {
                    // Default template - replace entirely
                    finalGoals = newGoals
                }
                setGoals(finalGoals)
                setIsGoalsModified(true)

                // Count total goals and warn if exceeds 10
                const totalGoals = finalGoals.split("\n").filter(g => g.trim()).length
                if (totalGoals > 10) {
                    toast.warning(`You have ${totalGoals} goals. Consider keeping it under 10 for best results.`)
                } else {
                    toast.success(`Extracted ${extractedGoals.length} goals from file`)
                }

                // Track file upload event
                capture('test_plan_file_uploaded', {
                    file_type: file.type,
                    file_size_bytes: file.size,
                    goals_extracted_count: extractedGoals.length,
                });
            } else {
                toast.warning("No goals could be extracted from the file")
            }
        } catch (error: unknown) {
            console.error(error)
            const err = error as { response?: { data?: { detail?: string } }; message?: string }
            const message = err.response?.data?.detail || "Failed to extract goals from file"
            toast.error(message)
            captureException(error instanceof Error ? error : new Error(String(error)));
        } finally {
            setUploading(false)
            if (fileInputRef.current) fileInputRef.current.value = ""
        }
    }

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault()
        if (!url) return

        setSubmitting(true)
        try {
            // Get reCAPTCHA token (if enabled)
            const recaptchaToken = await getReCaptchaToken()

            const payload: CreateTestRunRequest = {
                targetUrl: url,
                goals: goals.split("\n").filter(g => g.trim()), // Split by lines, filter empty
                persona,
                recaptchaToken,
            }

            const res = await api.post('/test-runs', payload)

            // Track test run creation event
            capture('test_run_created', {
                target_url: url,
                persona: persona,
                goals_count: payload.goals.length,
            });

            setOpen(false)
            // Reset state
            setUrl("")
            setPersona('STANDARD')
            setIsGoalsModified(false)
            setGoals(PERSONA_TEMPLATES.STANDARD)

            toast.success("Test run started successfully")
            onSuccess()

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
        <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
                <Button>
                    <Plus className="mr-2 h-4 w-4" /> New Test
                </Button>
            </DialogTrigger>
            <DialogContent className="w-[95vw] sm:w-[90vw] md:w-[85vw] lg:w-[80vw] !max-w-5xl max-h-[85vh] flex flex-col">
                <DialogHeader>
                    <DialogTitle>Start New Test Run</DialogTitle>
                </DialogHeader>
                <form onSubmit={handleCreate} className="flex flex-col flex-1 overflow-hidden">
                    <div className="space-y-6 mt-2 overflow-y-auto flex-1 pr-2">
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
                        />
                    </div>

                    {/* Persona Selector */}
                    <PersonaSelector
                        selected={persona}
                        onChange={(val) => setPersona(val as Persona)}
                    />

                    <div className="space-y-2">
                        <div className="flex items-center justify-between">
                            <Label htmlFor="goals">Test Goals</Label>
                            <div className="flex items-center gap-2">
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept=".pdf,.xlsx,.xls,.csv,.txt,application/pdf,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,text/csv,text/plain"
                                    onChange={handleFileUpload}
                                    className="hidden"
                                    id="file-upload"
                                    aria-describedby="file-help"
                                />
                                <Button
                                    type="button"
                                    variant="secondary"
                                    size="sm"
                                    onClick={() => fileInputRef.current?.click()}
                                    disabled={uploading}
                                    aria-label="Import test goals from file (PDF, Excel, CSV, TXT)"
                                >
                                    {uploading ? (
                                        <Loader2 className="mr-2 h-3 w-3 animate-spin" aria-hidden="true" />
                                    ) : (
                                        <Paperclip className="mr-2 h-3 w-3" aria-hidden="true" />
                                    )}
                                    {uploading ? "Analyzing..." : "Import from File"}
                                </Button>
                            </div>
                        </div>
                        <Textarea
                            id="goals"
                            value={goals}
                            onChange={handleGoalsChange}
                            placeholder="Describe what to test..."
                            className="min-h-[120px]"
                            aria-describedby="file-help"
                        />
                        <p id="file-help" className="text-xs text-muted-foreground flex items-center gap-1">
                            <FileText className="h-3 w-3" aria-hidden="true" />
                            Supports PDF, Excel, CSV, TXT. Max 2MB.
                        </p>
                        <p className="text-xs text-muted-foreground italic">
                            Using Google Sheets? Download as .xlsx first.
                        </p>
                        <div className="flex items-start gap-2 mt-2 p-2 rounded-md bg-muted/50 border border-border">
                            <Lock className="h-3.5 w-3.5 text-muted-foreground mt-0.5 flex-shrink-0" />
                            <div className="flex items-center gap-1">
                                <p className="text-xs text-muted-foreground">
                                    <span className="font-medium">Security Note:</span> This file is processed in-memory for AI analysis only, and is never saved to our servers.
                                </p>
                                <TooltipProvider>
                                    <Tooltip>
                                        <TooltipTrigger asChild>
                                            <HelpCircle className="h-3 w-3 text-muted-foreground/70 cursor-help flex-shrink-0" />
                                        </TooltipTrigger>
                                        <TooltipContent side="top" className="max-w-xs">
                                            <p className="text-xs">We use a stateless extraction pipeline. Once the goals are extracted, the file binary is wiped from RAM.</p>
                                        </TooltipContent>
                                    </Tooltip>
                                </TooltipProvider>
                            </div>
                        </div>
                    </div>
                    </div>

                    <div className="flex justify-end pt-4 border-t mt-4">
                        <Button
                            type="submit"
                            disabled={submitting || uploading}
                        >
                            {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                            Start Run
                        </Button>
                    </div>
                </form>
            </DialogContent>
        </Dialog>
    )
}
