import { render, RenderOptions } from '@testing-library/react'
import { ReactElement } from 'react'

function AllProviders({ children }: { children: React.ReactNode }) {
    return <>{children}</>
}

export function renderWithProviders(
    ui: ReactElement,
    options?: Omit<RenderOptions, 'wrapper'>,
) {
    return render(ui, { wrapper: AllProviders, ...options })
}

export { renderWithProviders as customRender }
