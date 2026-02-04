"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { cn } from "@/lib/utils";
import { LayoutDashboard, MessageSquare, Home, FileText, ExternalLink } from "lucide-react";
import Image from "next/image";

const RUN_PATH_RE = /^\/dashboard\/runs\/([^/]+)/;

const routes = [
    {
        label: "Runs",
        icon: LayoutDashboard,
        href: "/dashboard",
        color: "text-sky-500",
    },
];

// External links (open in new tab, domain separation)
const COMMUNITY_FORUM_URL = "https://www.producthunt.com/p/ai2qa";

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
                <Link href="/" className="flex items-center justify-center mb-14" title="Back to Home">
                    <Image
                        src="/logo.png"
                        alt="Ai2QA"
                        width={240}
                        height={96}
                        className="object-contain h-24 w-auto dark:brightness-150"
                        priority
                    />
                </Link>
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

                {/* External Links Section */}
                <div className="mt-8 pt-6 border-t border-border">
                    <p className="px-3 text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">
                        Community
                    </p>
                    <a
                        href={COMMUNITY_FORUM_URL}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-base group flex p-3 w-full justify-start font-medium cursor-pointer hover:bg-accent rounded-lg transition text-muted-foreground"
                    >
                        <div className="flex items-center flex-1">
                            <MessageSquare className="h-5 w-5 mr-3 text-purple-500" aria-hidden="true" />
                            Community Forum
                            <ExternalLink className="h-3.5 w-3.5 ml-2 text-muted-foreground/50" aria-hidden="true" />
                        </div>
                    </a>
                </div>
            </div>
            <div className="px-3 py-2 border-t border-border">
                <Link
                    href="/"
                    className="text-base group flex p-3 w-full justify-start font-medium cursor-pointer hover:bg-accent rounded-lg transition text-muted-foreground"
                >
                    <div className="flex items-center flex-1">
                        <Home className="h-5 w-5 mr-3 text-blue-400" aria-hidden="true" />
                        Back to Home
                    </div>
                </Link>
            </div>
        </div>
    );
}
