import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from '@/__tests__/mocks/server'

import { CreateRunDialog } from './create-run-dialog'

const mockOnSuccess = vi.fn()

describe('CreateRunDialog', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('renders the trigger button', () => {
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        expect(screen.getByRole('button', { name: /new test/i })).toBeInTheDocument()
    })

    it('opens dialog when trigger is clicked', async () => {
        const user = userEvent.setup()
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        await user.click(screen.getByRole('button', { name: /new test/i }))

        expect(screen.getByText('Start New Test Run')).toBeInTheDocument()
    })

    it('shows the URL input field as required', async () => {
        const user = userEvent.setup()
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        await user.click(screen.getByRole('button', { name: /new test/i }))

        const urlInput = screen.getByLabelText(/target url/i)
        expect(urlInput).toBeInTheDocument()
        expect(urlInput).toHaveAttribute('required')
    })

    it('shows the test goals textarea with default template', async () => {
        const user = userEvent.setup()
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        await user.click(screen.getByRole('button', { name: /new test/i }))

        const goalsTextarea = screen.getByRole('textbox', { name: /test goals/i })
        expect(goalsTextarea).toBeInTheDocument()
        expect(goalsTextarea).toHaveValue(
            'Verify the page loads successfully and document the current page state with screenshots.',
        )
    })

    it('shows file import button', async () => {
        const user = userEvent.setup()
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        await user.click(screen.getByRole('button', { name: /new test/i }))

        expect(
            screen.getByRole('button', { name: /import test goals from file/i }),
        ).toBeInTheDocument()
    })

    it('shows Start Run submit button', async () => {
        const user = userEvent.setup()
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        await user.click(screen.getByRole('button', { name: /new test/i }))

        expect(screen.getByRole('button', { name: /start run/i })).toBeInTheDocument()
    })

    it('shows the notification checkbox', async () => {
        const user = userEvent.setup()
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        await user.click(screen.getByRole('button', { name: /new test/i }))

        expect(screen.getByText('Notify me when test completes')).toBeInTheDocument()
    })

    it('shows security note about file processing', async () => {
        const user = userEvent.setup()
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        await user.click(screen.getByRole('button', { name: /new test/i }))

        expect(screen.getByText(/security note/i)).toBeInTheDocument()
    })

    it('shows file format support info', async () => {
        const user = userEvent.setup()
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        await user.click(screen.getByRole('button', { name: /new test/i }))

        expect(screen.getByText(/supports pdf, excel, csv, txt/i)).toBeInTheDocument()
    })

    it('submits the form and calls onSuccess', async () => {
        const user = userEvent.setup()
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        // Open the dialog
        await user.click(screen.getByRole('button', { name: /new test/i }))

        // Fill in the URL
        const urlInput = screen.getByLabelText(/target url/i)
        await user.type(urlInput, 'https://mysite.com')

        // Submit
        await user.click(screen.getByRole('button', { name: /start run/i }))

        await waitFor(() => {
            expect(mockOnSuccess).toHaveBeenCalled()
        })
    })

    it('shows persona selector with three options', async () => {
        const user = userEvent.setup()
        render(<CreateRunDialog onSuccess={mockOnSuccess} />)

        await user.click(screen.getByRole('button', { name: /new test/i }))

        // Persona selector should show three personas
        expect(screen.getByText('The Auditor')).toBeInTheDocument()
        expect(screen.getByText('The Gremlin')).toBeInTheDocument()
        expect(screen.getByText('The White Hat')).toBeInTheDocument()
    })
})
