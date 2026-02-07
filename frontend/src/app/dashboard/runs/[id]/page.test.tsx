import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '@/__tests__/mocks/server'
import { useParams } from 'next/navigation'

import RunDetailPage from './page'

vi.mocked(useParams).mockReturnValue({ id: 'run-1' })

describe('RunDetailPage', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.mocked(useParams).mockReturnValue({ id: 'run-1' })
    })

    it('shows loading state initially', () => {
        render(<RunDetailPage />)

        expect(document.querySelector('.animate-spin')).toBeInTheDocument()
    })

    it('renders run details after loading', async () => {
        render(<RunDetailPage />)

        await waitFor(() => {
            expect(screen.getByText('Run Details')).toBeInTheDocument()
        })

        // PASSED status badge exists (may appear multiple times due to FinalReport)
        expect(screen.getAllByText('PASSED').length).toBeGreaterThanOrEqual(1)
        // Target URL appears in the card and possibly in steps
        expect(screen.getAllByText('https://example.com').length).toBeGreaterThanOrEqual(1)
    })

    it('renders execution timeline with steps', async () => {
        render(<RunDetailPage />)

        await waitFor(() => {
            expect(screen.getByText('Execution Timeline')).toBeInTheDocument()
        })

        expect(screen.getByText('Step 1')).toBeInTheDocument()
        expect(screen.getByText('Step 2')).toBeInTheDocument()
        expect(screen.getByText('Step 3')).toBeInTheDocument()
    })

    it('shows step actions in the timeline', async () => {
        render(<RunDetailPage />)

        await waitFor(() => {
            expect(screen.getByText('Execution Timeline')).toBeInTheDocument()
        })

        // Actions may appear in both the timeline and the FinalReport table
        expect(screen.getAllByText('NAVIGATE').length).toBeGreaterThanOrEqual(1)
        expect(screen.getAllByText('CLICK').length).toBeGreaterThanOrEqual(1)
        expect(screen.getAllByText('ASSERT').length).toBeGreaterThanOrEqual(1)
    })

    it('shows stats grid labels', async () => {
        render(<RunDetailPage />)

        await waitFor(() => {
            expect(screen.getByText('Run Details')).toBeInTheDocument()
        })

        // Stats grid labels — some may appear in multiple places
        expect(screen.getAllByText('Passed').length).toBeGreaterThanOrEqual(1)
        expect(screen.getAllByText('Failed').length).toBeGreaterThanOrEqual(1)
        expect(screen.getAllByText('Auto-Healed').length).toBeGreaterThanOrEqual(1)
        expect(screen.getByText('Total Steps')).toBeInTheDocument()
        expect(screen.getByText('Persona')).toBeInTheDocument()
    })

    it('shows error message for failed steps', async () => {
        render(<RunDetailPage />)

        await waitFor(() => {
            expect(screen.getByText('Element not found within timeout')).toBeInTheDocument()
        })
    })

    it('shows auto-healed badge for retried steps', async () => {
        render(<RunDetailPage />)

        await waitFor(() => {
            // Auto-Healed badge appears both in stats grid and step badge
            expect(screen.getAllByText('Auto-Healed').length).toBeGreaterThanOrEqual(1)
        })
    })

    it('shows export buttons', async () => {
        render(<RunDetailPage />)

        await waitFor(() => {
            expect(screen.getByText('PDF')).toBeInTheDocument()
        })
    })

    it('shows Back to Runs link', async () => {
        render(<RunDetailPage />)

        await waitFor(() => {
            expect(screen.getByText('Back to Runs')).toBeInTheDocument()
        })
    })

    it('shows "Run not found" when API returns 404', async () => {
        server.use(
            http.get('http://localhost:8080/api/v1/test-runs/:id', () => {
                return HttpResponse.json(null, { status: 404 })
            }),
            http.get('http://localhost:8080/api/v1/test-runs/:id/log', () => {
                return HttpResponse.json(null, { status: 404 })
            }),
        )

        render(<RunDetailPage />)

        await waitFor(() => {
            expect(screen.getByText('Run not found')).toBeInTheDocument()
        })
    })

    it('shows progress bar for running test', async () => {
        server.use(
            http.get('http://localhost:8080/api/v1/test-runs/:id', () => {
                return HttpResponse.json({
                    id: 'run-1',
                    targetUrl: 'https://example.com',
                    status: 'RUNNING',
                    persona: 'STANDARD',
                    createdAt: '2025-01-15T10:00:00Z',
                    progressPercent: 60,
                    executedStepCount: 6,
                    totalStepCount: 10,
                })
            }),
        )

        render(<RunDetailPage />)

        await waitFor(() => {
            expect(screen.getByText('Progress')).toBeInTheDocument()
        })

        expect(screen.getByText('Step 6 of 10')).toBeInTheDocument()
        expect(screen.getByRole('progressbar')).toBeInTheDocument()
    })
})
