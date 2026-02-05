"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { cn } from "@/lib/utils";
import { LayoutDashboard, FileText, Sparkles } from "lucide-react";
import Image from "next/image";

const RUN_PATH_RE = /^\/dashboard\/runs\/([^/]+)/;

const routes = [
    {
        label: "Test Runs",
        icon: LayoutDashboard,
        href: "/dashboard",
        color: "text-sky-500",
    },
];

export function Sidebar() {
    const pathname = usePathname();

    // Track current run ID - persist in sessionStorage so it survives navigation
    const [currentRunId, setCurrentRunId] = useState<string | null>(null);

    useEffect(() => {
        // Check if viewing a specific run
        const runMatch = pathname.match(RUN_PATH_RE);
        if (runMatch) {
            const runId = runMatch[1];
            setCurrentRunId(runId);
            sessionStorage.setItem("currentRunId", runId);
        } else {
            // Not on a run page - restore from session storage
            const storedRunId = sessionStorage.getItem("currentRunId");
            setCurrentRunId(storedRunId);
        }
    }, [pathname]);

    return (
        <div className="space-y-4 py-4 flex flex-col h-full sidebar-cosmic text-card-foreground">
            <div className="px-3 py-2 flex-1">
                <div className="flex items-center justify-center mb-8">
                    <Image
                        src="/logo.png"
                        alt="Ai2QA"
                        width={200}
                        height={80}
                        className="object-contain h-20 w-auto dark:brightness-150"
                        priority
                    />
                </div>

                {/* Powered by Gemini 3 Badge */}
                <div className="flex items-center justify-center mb-8">
                    <div className="inline-flex items-center gap-2 px-3 py-2 rounded-full bg-gradient-to-r from-blue-500/20 to-purple-500/20 border border-blue-500/30">
                        <Sparkles className="h-4 w-4 text-blue-400" />
                        <span className="text-xs font-medium text-blue-300">Powered by Gemini 3</span>
                    </div>
                </div>

                <div className="space-y-1">
                    {routes.map((route) => (
                        <Link
                            key={route.href}
                            href={route.href}
                            aria-current={pathname === route.href ? "page" : undefined}
                            className={cn(
                                "text-base group flex p-3 w-full justify-start font-medium cursor-pointer hover:bg-accent rounded-lg transition",
                                pathname === route.href
                                    ? "text-accent-foreground bg-accent"
                                    : "text-muted-foreground"
                            )}
                        >
                            <div className="flex items-center flex-1">
                                <route.icon className={cn("h-5 w-5 mr-3", route.color)} aria-hidden="true" />
                                {route.label}
                            </div>
                        </Link>
                    ))}
                    {/* Current Run link - always visible when a run ID exists */}
                    {currentRunId && (
                        <Link
                            href={`/dashboard/runs/${currentRunId}`}
                            aria-current={pathname.startsWith(`/dashboard/runs/${currentRunId}`) ? "page" : undefined}
                            className={cn(
                                "text-base group flex p-3 w-full justify-start font-medium cursor-pointer hover:bg-accent rounded-lg transition",
                                pathname.startsWith(`/dashboard/runs/${currentRunId}`)
                                    ? "text-accent-foreground bg-accent"
                                    : "text-muted-foreground"
                            )}
                        >
                            <div className="flex items-center flex-1">
                                <FileText className="h-5 w-5 mr-3 text-amber-500" aria-hidden="true" />
                                Current Run
                            </div>
                        </Link>
                    )}
                </div>
            </div>

        </div>
    );
}
