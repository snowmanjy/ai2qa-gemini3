/**
 * Analytics stub - no-op implementation
 */

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export const capture = (event: string, properties?: Record<string, unknown>) => {
    // No-op
};

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export const captureException = (error: Error, properties?: Record<string, unknown>) => {
    console.error(error);
};
