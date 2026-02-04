import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '@/__tests__/mocks/server'

// Mocks must be set up before component import
import '@/__tests__/mocks/clerk'
import '@/__tests__/mocks/analytics'

import DashboardPage from './page'

describe('DashboardPage', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('renders the runs table with data from the API', async () => {
        render(<DashboardPage />)

        // Wait for loading to finish and data to appear
        await waitFor(() => {
            expect(screen.getByText('https://example.com')).toBeInTheDocument()
        })

        expect(screen.getByText('https://test.dev')).toBeInTheDocument()
        expect(screen.getByText('https://staging.app')).toBeInTheDocument()
    })

    it('displays correct status badges', async () => {
        render(<DashboardPage />)

        await waitFor(() => {
            // COMPLETED maps to "PASSED"
            expect(screen.getByText('PASSED')).toBeInTheDocument()
        })

        expect(screen.getByText('RUNNING')).toBeInTheDocument()
        expect(screen.getByText('FAILED')).toBeInTheDocument()
    })

    it('shows correct summary stats', async () => {
        render(<DashboardPage />)

        await waitFor(() => {
            expect(screen.getByText('https://example.com')).toBeInTheDocument()
        })

        // Stats: 3 total, 1 passed (COMPLETED), 1 failed (FAILED), 1 active (RUNNING)
        expect(screen.getByText('Total Runs')).toBeInTheDocument()
        expect(screen.getByText('Passed')).toBeInTheDocument()
        expect(screen.getByText('Failed')).toBeInTheDocument()
        expect(screen.getByText('Active')).toBeInTheDocument()
    })

    it('shows empty state when no runs exist', async () => {
        server.use(
            http.get('http://localhost:8080/api/v1/test-runs', () => {
                return HttpResponse.json({
                    content: [],
                    page: 0,
                    size: 20,
                    totalElements: 0,
                    totalPages: 0,
                })
            }),
        )

        render(<DashboardPage />)

        await waitFor(() => {
            expect(screen.getByText('No test runs yet')).toBeInTheDocument()
        })

        expect(screen.getByText('Create your first test run to get started.')).toBeInTheDocument()
    })

    it('shows loading spinner initially', () => {
        render(<DashboardPage />)

        expect(screen.getByText('Loading test runs...')).toBeInTheDocument()
    })

    it('renders persona badges correctly', async () => {
        render(<DashboardPage />)

        await waitFor(() => {
            expect(screen.getByText('Standard')).toBeInTheDocument()
        })

        expect(screen.getByText('Chaos')).toBeInTheDocument()
        expect(screen.getByText('Hacker')).toBeInTheDocument()
    })

    it('renders mode badges', async () => {
        render(<DashboardPage />)

        await waitFor(() => {
            expect(screen.getByText('https://example.com')).toBeInTheDocument()
        })

        // Two Cloud badges and one Local Agent badge
        const cloudBadges = screen.getAllByText('Cloud')
        expect(cloudBadges.length).toBeGreaterThanOrEqual(2)
        expect(screen.getByText('Local Agent')).toBeInTheDocument()
    })

    it('shows report links for completed and failed runs', async () => {
        render(<DashboardPage />)

        await waitFor(() => {
            expect(screen.getByText('https://example.com')).toBeInTheDocument()
        })

        // COMPLETED and FAILED runs should have report links
        const reportLinks = screen.getAllByLabelText(/View report for/)
        expect(reportLinks).toHaveLength(2) // run-1 (COMPLETED) and run-3 (FAILED)
    })

    it('has a refresh button', async () => {
        render(<DashboardPage />)

        const refreshButton = screen.getByRole('button', { name: /refresh test runs/i })
        expect(refreshButton).toBeInTheDocument()
    })

    it('shows the Test Runs heading', async () => {
        render(<DashboardPage />)

        expect(screen.getByText('Test Runs')).toBeInTheDocument()
        expect(screen.getByText('Manage and monitor your autonomous QA sessions.')).toBeInTheDocument()
    })
})
