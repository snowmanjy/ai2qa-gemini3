"use client";

import { Accessibility, ChevronDown, ChevronUp } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";

interface A11yBadgeProps {
    warnings: string[];
    className?: string;
}

/**
 * Accessibility badge/accordion for displaying WCAG violations.
 * 
 * - Yellow/Orange theme (warning, not error)
 * - Collapsible list of violations
 * - Shows critical/serious counts in badge
 */
export function A11yBadge({ warnings, className }: A11yBadgeProps) {
    const [isOpen, setIsOpen] = useState(false);

    if (!warnings || warnings.length === 0) return null;

    let criticalCount = 0, seriousCount = 0;
    for (const w of warnings) {
        if (w.includes("[CRITICAL]")) criticalCount++;
        else if (w.includes("[SERIOUS]")) seriousCount++;
    }

    const badgeColor = criticalCount > 0
        ? "bg-orange-100 text-orange-800 border-orange-300"
        : "bg-amber-100 text-amber-800 border-amber-300";

    return (
        <div className={cn("rounded-lg border", badgeColor, className)}>
            {/* Header / Badge */}
            <button
                onClick={() => setIsOpen(!isOpen)}
                className="w-full flex items-center justify-between p-3 text-left hover:bg-black/5 transition-colors rounded-lg"
            >
                <div className="flex items-center gap-2">
                    <Accessibility className="h-4 w-4" />
                    <span className="text-sm font-medium">
                        Accessibility Audit
                    </span>
                    <span className="text-xs px-1.5 py-0.5 rounded bg-white/50">
                        {warnings.length} issue{warnings.length !== 1 ? 's' : ''}
                    </span>
                    {criticalCount > 0 && (
                        <span className="text-xs px-1.5 py-0.5 rounded bg-red-200 text-red-800 font-medium">
                            {criticalCount} critical
                        </span>
                    )}
                </div>
                {isOpen ? (
                    <ChevronUp className="h-4 w-4" />
                ) : (
                    <ChevronDown className="h-4 w-4" />
                )}
            </button>

            {/* Expanded Content */}
            {isOpen && (
                <div className="px-3 pb-3 space-y-2">
                    <div className="h-px bg-current opacity-20" />
                    <ul className="space-y-1.5">
                        {warnings.map((warning, idx) => (
                            <li
                                key={idx}
                                className={cn(
                                    "text-xs p-2 rounded",
                                    warning.includes("[CRITICAL]")
                                        ? "bg-red-50 border border-red-200 text-red-800"
                                        : warning.includes("[SERIOUS]")
                                            ? "bg-orange-50 border border-orange-200 text-orange-800"
                                            : "bg-white/50 text-slate-800"
                                )}
                            >
                                <code className="break-all">{warning}</code>
                            </li>
                        ))}
                    </ul>

                    {/* Help Text */}
                    <p className="text-xs opacity-70 mt-2">
                        💡 These WCAG violations may impact users with disabilities.
                    </p>
                </div>
            )}
        </div>
    );
}

/**
 * Simple inline badge for showing a11y status in tables or lists.
 */
export function A11yStatusBadge({ count, hasCritical }: { count: number; hasCritical?: boolean }) {
    if (count === 0) return null;

    return (
        <span className={cn(
            "inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full",
            hasCritical
                ? "bg-orange-100 text-orange-700"
                : "bg-amber-100 text-amber-700"
        )}>
            <Accessibility className="h-3 w-3" />
            {count}
        </span>
    );
}
