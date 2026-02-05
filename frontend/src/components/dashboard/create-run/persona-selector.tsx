"use client";

import { CheckCircle2, AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";
import { capture } from "@/lib/analytics";

// Define the personas to match the backend Enum
// Order: Hawk (default, most impressive) → Gremlin → White Hat → Auditor (last, least exciting)
const PERSONAS = [
    {
        id: "PERFORMANCE_HAWK",
        name: "The Performance Hawk",
        role: "Performance Auditor",
        description: "Watches every millisecond. Tracks Core Web Vitals, page load times, and optimization opportunities.",
        avatar: "/images/persona-performance.png",
        color: "ring-orange-500",
        badge: "bg-orange-500/20 text-orange-400",
    },
    {
        id: "CHAOS",
        name: "The Gremlin",
        role: "Chaos Engineer",
        description: "A clumsy, impatient user. Rage-clicks, floods inputs, and breaks the UI state machine.",
        avatar: "/images/persona-chaos.png",
        color: "ring-purple-500",
        badge: "bg-purple-500/20 text-purple-400",
    },
    {
        id: "HACKER",
        name: "The White Hat",
        role: "Security Auditor",
        description: "Probes for XSS, SQLi, and Logic Bypasses. Trusts no input. Finds what devs hid.",
        avatar: "/images/persona-hacker.png",
        color: "ring-rose-500",
        badge: "bg-rose-500/20 text-rose-400",
        warning: "Injects test payloads into forms. Use on staging/dev only.",
    },
    {
        id: "STANDARD",
        name: "The Auditor",
        role: "Standard QA",
        description: "Obsessed with specs. Verifies business logic and data integrity with surgical precision.",
        avatar: "/images/persona-auditor.png",
        color: "ring-blue-500",
        badge: "bg-[var(--status-info-bg)] text-[var(--status-info-text)]",
    },
];

interface PersonaSelectorProps {
    selected: string;
    onChange: (value: string) => void;
}

export function PersonaSelector({ selected, onChange }: PersonaSelectorProps) {
    return (
        <div className="w-full space-y-3">
            <div className="flex items-center justify-between">
                <h3 className="text-sm font-medium text-foreground">Choose your Tester</h3>
                <span className="text-xs text-muted-foreground">Who is running this test?</span>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
                {PERSONAS.map((persona) => {
                    const isSelected = selected === persona.id;

                    return (
                        <button
                            key={persona.id}
                            type="button"
                            aria-pressed={isSelected}
                            aria-label={`${persona.name} - ${persona.role}: ${persona.description}`}
                            onClick={() => {
                                capture('persona_selected', {
                                    persona_id: persona.id,
                                    persona_name: persona.name,
                                    persona_role: persona.role,
                                });
                                onChange(persona.id);
                            }}
                            className={cn(
                                "relative flex cursor-pointer flex-col rounded-xl border-2 bg-card p-4 text-left transition-all hover:border-muted-foreground/50 hover:bg-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 focus-visible:ring-offset-background",
                                isSelected
                                    ? "border-blue-500"
                                    : "border-border"
                            )}
                        >
                            {/* Avatar Area */}
                            <div className="mb-3 flex h-20 w-full items-center justify-center">
                                <img
                                    src={persona.avatar}
                                    alt=""
                                    aria-hidden="true"
                                    className="h-16 w-16 object-contain rounded-2xl"
                                />
                            </div>

                            {/* Content */}
                            <div className="flex flex-1 flex-col">
                                <div className="flex items-center justify-between">
                                    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${persona.badge}`}>
                                        {persona.role}
                                    </span>
                                    {isSelected && <CheckCircle2 className="h-4 w-4 text-emerald-400" aria-hidden="true" />}
                                </div>

                                <span className="mt-2 block text-sm font-bold text-foreground">
                                    {persona.name}
                                </span>

                                <span className="mt-1 block text-xs leading-relaxed text-muted-foreground">
                                    {persona.description}
                                </span>

                                {/* Warning for personas that inject payloads */}
                                {"warning" in persona && persona.warning && (
                                    <span className="mt-2 flex items-center gap-1 text-[10px] text-amber-400/80">
                                        <AlertTriangle className="h-3 w-3 flex-shrink-0" aria-hidden="true" />
                                        {persona.warning}
                                    </span>
                                )}
                            </div>
                        </button>
                    );
                })}
            </div>
        </div>
    );
}
