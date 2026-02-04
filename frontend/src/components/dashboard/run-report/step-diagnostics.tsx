import { AlertTriangle, WifiOff, TerminalSquare, Accessibility } from "lucide-react";
import { Accordion, AccordionItem, AccordionTrigger, AccordionContent } from "@/components/ui/accordion";

interface StepDiagnosticsProps {
    networkErrors?: string[];
    consoleErrors?: string[];
    a11yWarnings?: string[];
}

export function StepDiagnostics({ networkErrors, consoleErrors, a11yWarnings }: StepDiagnosticsProps) {
    // If clean, render nothing
    if (!networkErrors?.length && !consoleErrors?.length && !a11yWarnings?.length) {
        return null;
    }

    return (
        <div className="mt-4 space-y-3 rounded-md border border-slate-200 bg-slate-50 p-4">
            <h4 className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-slate-500">
                <AlertTriangle className="h-4 w-4" />
                Diagnostics & Health
            </h4>

            <div className="grid gap-3 md:grid-cols-1">

                {/* 1. NETWORK ERRORS (The Backend Blame) */}
                {networkErrors && networkErrors.length > 0 && (
                    <div className="rounded border border-red-200 bg-red-50 p-3">
                        <div className="flex items-center gap-2 text-sm font-semibold text-red-800">
                            <WifiOff className="h-4 w-4" />
                            <span>Network Failures ({networkErrors.length})</span>
                        </div>
                        <ul className="mt-2 space-y-1 pl-6">
                            {networkErrors.map((err, i) => (
                                <li key={i} className="list-disc font-mono text-xs text-red-700">{err}</li>
                            ))}
                        </ul>
                    </div>
                )}

                {/* 2. CONSOLE ERRORS (The Frontend Blame) */}
                {consoleErrors && consoleErrors.length > 0 && (
                    <div className="rounded border border-orange-200 bg-orange-50 p-3">
                        <div className="flex items-center gap-2 text-sm font-semibold text-orange-800">
                            <TerminalSquare className="h-4 w-4" />
                            <span>Console Exceptions ({consoleErrors.length})</span>
                        </div>
                        <code className="mt-2 block max-h-32 overflow-y-auto whitespace-pre-wrap rounded bg-orange-100 p-2 text-xs text-orange-900">
                            {consoleErrors.join("\n")}
                        </code>
                    </div>
                )}

                {/* 3. ACCESSIBILITY (The Compliance Blame) */}
                {a11yWarnings && a11yWarnings.length > 0 && (
                    <Accordion type="single" collapsible className="w-full rounded border border-yellow-200 bg-yellow-50">
                        <AccordionItem value="a11y" className="border-0">
                            <AccordionTrigger className="px-3 py-2 text-sm font-semibold text-yellow-800 hover:no-underline">
                                <div className="flex items-center gap-2">
                                    <Accessibility className="h-4 w-4" />
                                    <span>Accessibility Violations ({a11yWarnings.length})</span>
                                </div>
                            </AccordionTrigger>
                            <AccordionContent className="px-3 pb-3">
                                <ul className="space-y-1">
                                    {a11yWarnings.map((warn, i) => (
                                        <li key={i} className="text-xs text-yellow-700">{warn}</li>
                                    ))}
                                </ul>
                            </AccordionContent>
                        </AccordionItem>
                    </Accordion>
                )}
            </div>
        </div>
    );
}
