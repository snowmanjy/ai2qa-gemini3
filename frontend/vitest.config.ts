import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src'),
        },
    },
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: ['./src/__tests__/setup.tsx'],
        include: ['src/**/*.{test,spec}.{ts,tsx}'],
        exclude: ['node_modules', '.next', 'e2e'],
        coverage: {
            provider: 'v8',
            reporter: ['text', 'lcov', 'html'],
            include: ['src/**/*.{ts,tsx}'],
            exclude: [
                'src/__tests__/**',
                'src/**/*.d.ts',
                'src/**/types/**',
            ],
            thresholds: {
                lines: 30,
                branches: 30,
                functions: 30,
                statements: 30,
            },
        },
    },
})
