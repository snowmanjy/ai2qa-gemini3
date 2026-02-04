"use client";

import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Menu, X } from "lucide-react";

export function Navbar() {
    const [mobileOpen, setMobileOpen] = useState(false);

    return (
        <nav className="fixed top-0 left-0 right-0 z-50 border-b border-border/50 bg-[var(--navbar)] backdrop-blur-xl backdrop-saturate-150">
            <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
                <Link href="/" className="flex items-center gap-3">
                  <Image
                    src="/logo.png"
                    alt="Ai2QA Logo"
                    width={320}
                    height={128}
                    className="object-contain h-32 w-auto dark:brightness-150"
                    priority
                  />
                </Link>

                {/* Desktop Navigation */}
                <div className="hidden md:flex items-center space-x-8">
                    <a href="/#features" className="text-sm text-muted-foreground hover:text-foreground transition">
                        Features
                    </a>
                    <a href="/#security" className="text-sm text-muted-foreground hover:text-foreground transition">
                        Security
                    </a>
                    <a
                        href="https://x.com/SameThoughtsAI"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-muted-foreground hover:text-foreground transition"
                        title="Follow us on X"
                    >
                        <svg
                            viewBox="0 0 24 24"
                            className="h-5 w-5 fill-current"
                            aria-label="X (Twitter)"
                        >
                            <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
                        </svg>
                    </a>
                    <Link href="/dashboard">
                        <Button variant="outline" size="sm">
                            Dashboard
                        </Button>
                    </Link>
                </div>

                {/* Mobile Hamburger Button */}
                <button
                    className="md:hidden p-2 rounded-lg hover:bg-accent transition"
                    onClick={() => setMobileOpen(!mobileOpen)}
                    aria-label={mobileOpen ? "Close menu" : "Open menu"}
                    aria-expanded={mobileOpen}
                >
                    {mobileOpen ? (
                        <X className="h-6 w-6 text-foreground" />
                    ) : (
                        <Menu className="h-6 w-6 text-foreground" />
                    )}
                </button>
            </div>

            {/* Mobile Navigation Drawer */}
            {mobileOpen && (
                <div className="md:hidden border-t border-border/50 bg-[var(--navbar)] backdrop-blur-xl">
                    <div className="px-6 py-4 space-y-3">
                        <a
                            href="/#features"
                            className="block py-2 text-sm text-muted-foreground hover:text-foreground transition"
                            onClick={() => setMobileOpen(false)}
                        >
                            Features
                        </a>
                        <a
                            href="/#security"
                            className="block py-2 text-sm text-muted-foreground hover:text-foreground transition"
                            onClick={() => setMobileOpen(false)}
                        >
                            Security
                        </a>
                        <a
                            href="https://x.com/SameThoughtsAI"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="flex items-center gap-2 py-2 text-sm text-muted-foreground hover:text-foreground transition"
                        >
                            <svg
                                viewBox="0 0 24 24"
                                className="h-4 w-4 fill-current"
                                aria-label="X (Twitter)"
                            >
                                <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
                            </svg>
                            Follow on X
                        </a>
                        <div className="pt-3 border-t border-border/50">
                            <Link href="/dashboard" onClick={() => setMobileOpen(false)}>
                                <Button variant="outline" size="sm">
                                    Dashboard
                                </Button>
                            </Link>
                        </div>
                    </div>
                </div>
            )}
        </nav>
    );
}
