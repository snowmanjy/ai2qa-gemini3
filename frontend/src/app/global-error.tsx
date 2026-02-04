'use client';

/**
 * Global error boundary for the application.
 *
 * This component catches errors that occur in the root layout.
 * It must be a Client Component and include its own <html> and <body> tags.
 *
 * Note: This file fixes a Next.js 16 prerendering bug with the built-in global-error.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-background text-foreground font-sans antialiased">
        <div className="flex min-h-screen flex-col items-center justify-center px-6">
          <div className="text-center max-w-md">
            <h1 className="text-4xl font-bold text-red-500 mb-4">
              Something went wrong
            </h1>
            <p className="text-muted-foreground mb-6">
              An unexpected error occurred. Please try again.
            </p>
            {error.digest && (
              <p className="text-xs text-muted-foreground/50 mb-4">
                Error ID: {error.digest}
              </p>
            )}
            <button
              onClick={reset}
              className="px-6 py-3 bg-purple-600 hover:bg-purple-500 text-white font-semibold rounded-lg transition-colors"
            >
              Try Again
            </button>
          </div>
        </div>
      </body>
    </html>
  );
}
