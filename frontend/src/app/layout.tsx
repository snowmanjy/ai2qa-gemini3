import type { Metadata } from "next";
import { Geist, Geist_Mono, Outfit } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/sonner";
import { ThemeProvider } from "@/components/theme-provider";
import { ThemeToggle } from "@/components/theme-toggle";
import { ReCaptchaProvider } from "@/components/recaptcha-provider";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

const outfit = Outfit({
  variable: "--font-outfit",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
});

export const metadata: Metadata = {
  title: "Ai2QA - AI-Powered Website Testing with Gemini",
  description: "AI-powered autonomous QA testing platform using Google Gemini.",
  manifest: '/manifest.json',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="scroll-pt-40 scroll-smooth" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} ${outfit.variable} antialiased h-full`}
        suppressHydrationWarning
      >
        <ThemeProvider
          attribute="class"
          defaultTheme="dark"
          enableSystem
          disableTransitionOnChange
        >
          <ReCaptchaProvider>
            {/* Skip link for keyboard navigation */}
            <a
              href="#main-content"
              className="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-[100] focus:px-4 focus:py-2 focus:bg-primary focus:text-primary-foreground focus:rounded-md focus:outline-none"
            >
              Skip to main content
            </a>
            <main id="main-content" className="pt-40 min-h-screen bg-background">
              {children}
            </main>
            {/* Theme toggle in bottom-right corner */}
            <div className="fixed bottom-4 right-4 z-50">
              <ThemeToggle />
            </div>
            <Toaster />
          </ReCaptchaProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
