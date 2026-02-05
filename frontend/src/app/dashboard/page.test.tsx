import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '@/__tests__/mocks/server'

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

        expect(screen.getByText('Enter a URL above to start your first test.')).toBeInTheDocument()
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

    it('shows the Recent Runs heading', async () => {
        render(<DashboardPage />)

        expect(screen.getByText('Recent Runs')).toBeInTheDocument()
        expect(screen.getByText('Monitor your autonomous QA sessions')).toBeInTheDocument()
    })

    it('shows inline test creation form', async () => {
        render(<DashboardPage />)

        // Check for inline form elements
        expect(screen.getByText('Start a New Test')).toBeInTheDocument()
        expect(screen.getByLabelText(/Target URL/i)).toBeInTheDocument()
        expect(screen.getByRole('button', { name: /Start Test Run/i })).toBeInTheDocument()
    })

    it('shows persona selector in inline form', async () => {
        render(<DashboardPage />)

        // Check for persona selector
        expect(screen.getByText('Choose your Tester')).toBeInTheDocument()
        expect(screen.getByText('The Performance Hawk')).toBeInTheDocument()
        expect(screen.getByText('The Gremlin')).toBeInTheDocument()
        expect(screen.getByText('The White Hat')).toBeInTheDocument()
        expect(screen.getByText('The Auditor')).toBeInTheDocument()
    })
})
