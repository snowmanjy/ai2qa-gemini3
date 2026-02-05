"use client";

import { useState } from "react";
import { Sidebar } from "@/components/sidebar";
import { Menu, Sparkles } from "lucide-react";
import Image from "next/image";

export default function DashboardLayout({
    children,
}: {
    children: React.ReactNode;
}) {
    const [sidebarOpen, setSidebarOpen] = useState(false);

    return (
        <div className="h-full relative">
            {/* Mobile sidebar overlay */}
            {sidebarOpen && (
                <div
                    className="fixed inset-0 z-[70] bg-black/50 md:hidden"
                    onClick={() => setSidebarOpen(false)}
                    aria-hidden="true"
                />
            )}

            {/* Mobile sidebar drawer */}
            <div
                className={`fixed inset-y-0 left-0 z-[80] w-72 transform transition-transform duration-200 ease-in-out md:hidden ${
                    sidebarOpen ? "translate-x-0" : "-translate-x-full"
                }`}
            >
                <Sidebar />
            </div>

            {/* Desktop sidebar */}
            <div className="hidden h-full md:flex md:w-72 md:flex-col md:fixed md:inset-y-0 z-[80]">
                <Sidebar />
            </div>

            {/* Mobile top bar */}
            <div className="md:hidden fixed top-0 left-0 right-0 z-[60] flex items-center justify-between h-14 px-4 border-b border-border bg-card">
                <div className="flex items-center">
                    <button
                        onClick={() => setSidebarOpen(true)}
                        className="p-2 rounded-lg hover:bg-accent transition"
                        aria-label="Open sidebar"
                    >
                        <Menu className="h-5 w-5 text-foreground" />
                    </button>
                    <Image
                        src="/logo.png"
                        alt="Ai2QA"
                        width={100}
                        height={40}
                        className="ml-3 object-contain h-8 w-auto dark:brightness-150"
                    />
                </div>
                {/* Gemini badge for mobile */}
                <div className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-blue-500/20 border border-blue-500/30">
                    <Sparkles className="h-3 w-3 text-blue-400" />
                    <span className="text-[10px] font-medium text-blue-300">Gemini 3</span>
                </div>
            </div>

            <main className="md:pl-72 h-full dashboard-bg text-foreground pt-14 md:pt-0">
                {children}
            </main>
        </div>
    );
}
