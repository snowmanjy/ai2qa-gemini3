"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { capture } from "@/lib/analytics";
import { ReactNode } from "react";

interface CTAButtonProps {
    href: string;
    ctaName: string;
    ctaLocation: string;
    children: ReactNode;
    className?: string;
    size?: "default" | "sm" | "lg" | "icon";
    variant?: "default" | "destructive" | "outline" | "secondary" | "ghost" | "link";
}

export function CTAButton({
    href,
    ctaName,
    ctaLocation,
    children,
    className,
    size = "lg",
    variant = "default",
}: CTAButtonProps) {
    const handleClick = () => {
        capture('cta_clicked', {
            cta_name: ctaName,
            cta_location: ctaLocation,
            cta_href: href,
        });
    };

    return (
        <Link href={href} onClick={handleClick}>
            <Button size={size} variant={variant} className={className}>
                {children}
            </Button>
        </Link>
    );
}
