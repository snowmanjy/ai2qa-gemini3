"use client"

import { GoogleReCaptchaProvider } from 'react-google-recaptcha-v3'

interface ReCaptchaProviderProps {
    children: React.ReactNode
}

/**
 * Wrapper for Google reCAPTCHA v3 provider.
 * Only renders the provider if a site key is configured.
 * Set NEXT_PUBLIC_RECAPTCHA_SITE_KEY in .env.local to enable.
 */
export function ReCaptchaProvider({ children }: ReCaptchaProviderProps) {
    const siteKey = process.env.NEXT_PUBLIC_RECAPTCHA_SITE_KEY

    // If no site key is configured, skip reCAPTCHA (local dev mode)
    if (!siteKey) {
        return <>{children}</>
    }

    return (
        <GoogleReCaptchaProvider
            reCaptchaKey={siteKey}
            scriptProps={{
                async: true,
                defer: true,
                appendTo: 'head',
            }}
        >
            {children}
        </GoogleReCaptchaProvider>
    )
}
