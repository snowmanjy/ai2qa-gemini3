import '@testing-library/jest-dom'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, vi } from 'vitest'
import { server } from './mocks/server'

// --- MSW Server Lifecycle ---
beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => {
    cleanup()
    server.resetHandlers()
})
afterAll(() => server.close())

// --- Browser API Mocks ---

// matchMedia
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
})

// IntersectionObserver
class MockIntersectionObserver {
    observe = vi.fn()
    unobserve = vi.fn()
    disconnect = vi.fn()
}
Object.defineProperty(window, 'IntersectionObserver', {
    writable: true,
    value: MockIntersectionObserver,
})

// ResizeObserver
class MockResizeObserver {
    observe = vi.fn()
    unobserve = vi.fn()
    disconnect = vi.fn()
}
Object.defineProperty(window, 'ResizeObserver', {
    writable: true,
    value: MockResizeObserver,
})

// sessionStorage
const sessionStorageMap = new Map<string, string>()
Object.defineProperty(window, 'sessionStorage', {
    writable: true,
    value: {
        getItem: vi.fn((key: string) => sessionStorageMap.get(key) ?? null),
        setItem: vi.fn((key: string, value: string) => sessionStorageMap.set(key, value)),
        removeItem: vi.fn((key: string) => sessionStorageMap.delete(key)),
        clear: vi.fn(() => sessionStorageMap.clear()),
        get length() { return sessionStorageMap.size },
        key: vi.fn((index: number) => Array.from(sessionStorageMap.keys())[index] ?? null),
    },
})

// --- Next.js Mocks ---

// next/navigation
const mockPush = vi.fn()
const mockReplace = vi.fn()
const mockBack = vi.fn()

vi.mock('next/navigation', () => ({
    useRouter: vi.fn(() => ({
        push: mockPush,
        replace: mockReplace,
        back: mockBack,
        prefetch: vi.fn(),
        refresh: vi.fn(),
    })),
    usePathname: vi.fn(() => '/dashboard'),
    useSearchParams: vi.fn(() => new URLSearchParams()),
    useParams: vi.fn(() => ({})),
}))

// next/image
vi.mock('next/image', () => ({
    default: (props: Record<string, unknown>) => {
        // eslint-disable-next-line @next/next/no-img-element, jsx-a11y/alt-text
        const { fill, priority, ...rest } = props
        return <img {...(rest as React.ImgHTMLAttributes<HTMLImageElement>)} />
    },
}))

// next/link
vi.mock('next/link', () => ({
    default: ({ children, href, ...props }: { children: React.ReactNode; href: string; [key: string]: unknown }) => (
        <a href={href} {...props}>{children}</a>
    ),
}))

// sonner (toast)
vi.mock('sonner', () => ({
    toast: {
        success: vi.fn(),
        error: vi.fn(),
        info: vi.fn(),
        warning: vi.fn(),
    },
    Toaster: () => null,
}))
