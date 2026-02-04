"use client"

import { useState, useCallback } from "react"
import { useRouter } from "next/navigation"
import { useGoogleReCaptcha } from 'react-google-recaptcha-v3'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Plus, Loader2, ChevronDown, ChevronRight } from "lucide-react"
import { api } from "@/lib/api"
import { toast } from "sonner"
import { CreateTestRunRequest, Persona } from "@/types"
import { PersonaSelector } from "./create-run/persona-selector"
import { capture, captureException } from "@/lib/analytics"

interface CreateRunDialogProps {
    onSuccess: () => void
}

const MAX_CONTEXT_LENGTH = 200

/**
 * Dialog for creating new test runs.
 */
export function CreateRunDialog({ onSuccess }: CreateRunDialogProps) {
    const router = useRouter()
    const { executeRecaptcha } = useGoogleReCaptcha()
    const [open, setOpen] = useState(false)
    const [url, setUrl] = useState("")
    const [persona, setPersona] = useState<Persona>('STANDARD')
    const [additionalContext, setAdditionalContext] = useState("")
    const [showContext, setShowContext] = useState(false)
    const [submitting, setSubmitting] = useState(false)

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

    const handleCreate = async (e: React.FormEvent) => {
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

            setOpen(false)
            // Reset state
            setUrl("")
            setPersona('STANDARD')
            setAdditionalContext("")
            setShowContext(false)

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
                            <div className="space-y-1">
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
                    </div>

                    <div className="flex justify-end pt-4 border-t mt-4">
                        <Button
                            type="submit"
                            disabled={submitting}
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
