"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { cn } from "@/lib/utils";
import { LayoutDashboard, FileText, Sparkles } from "lucide-react";
import Image from "next/image";

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

    // Track the actively running test run (set when a run is created, cleared when it finishes)
    const [activeRunId, setActiveRunId] = useState<string | null>(null);

    useEffect(() => {
        const storedId = sessionStorage.getItem("activeRunId");
        setActiveRunId(storedId);
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
                    {/* Active Run link - visible only while a test run is in progress */}
                    {activeRunId && (
                        <Link
                            href={`/dashboard/runs/${activeRunId}`}
                            aria-current={pathname.startsWith(`/dashboard/runs/${activeRunId}`) ? "page" : undefined}
                            className={cn(
                                "text-base group flex p-3 w-full justify-start font-medium cursor-pointer hover:bg-accent rounded-lg transition",
                                pathname.startsWith(`/dashboard/runs/${activeRunId}`)
                                    ? "text-accent-foreground bg-accent"
                                    : "text-muted-foreground"
                            )}
                        >
                            <div className="flex items-center flex-1">
                                <FileText className="h-5 w-5 mr-3 text-amber-500" aria-hidden="true" />
                                Active Run
                            </div>
                        </Link>
                    )}
                </div>
            </div>

        </div>
    );
}
