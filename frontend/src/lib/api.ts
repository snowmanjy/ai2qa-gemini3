import axios from "axios";

export const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1",
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        // Log API errors
        console.error("API Error:", error.response?.status, error.message);
        return Promise.reject(error);
    }
);

/**
 * Extracts test goals from a file using AI analysis.
 * @param file The file to analyze (PDF, Excel, CSV, TXT)
 * @returns Array of extracted goal strings
 */
export const extractPlan = async (file: File): Promise<string[]> => {
    const formData = new FormData();
    formData.append("file", file);

    const response = await api.post<{ goals: string[] }>("/utils/extract-plan", formData, {
        headers: { "Content-Type": "multipart/form-data" }
    });

    return response.data.goals || [];
};

// Saved Test Plans API
import { SavedTestPlan, CreateSavedPlanRequest, UpdateSavedPlanRequest, PagedResult, TestRun } from "@/types";

export const savedPlansApi = {
    list: (page = 0, size = 20) =>
        api.get<PagedResult<SavedTestPlan>>(`/saved-plans?page=${page}&size=${size}`),

    get: (id: string) =>
        api.get<SavedTestPlan>(`/saved-plans/${id}`),

    create: (data: CreateSavedPlanRequest) =>
        api.post<SavedTestPlan>('/saved-plans', data),

    update: (id: string, data: UpdateSavedPlanRequest) =>
        api.put<SavedTestPlan>(`/saved-plans/${id}`, data),

    delete: (id: string) =>
        api.delete(`/saved-plans/${id}`),

    run: (id: string, cookiesJson?: string) =>
        api.post<TestRun>(`/saved-plans/${id}/run`, { cookiesJson }),

    /** Save an existing test run as a reusable test plan */
    saveFromTestRun: (testRunId: string, name: string, description?: string) =>
        api.post<SavedTestPlan>(`/saved-plans/from-test-run/${testRunId}`, { name, description }),
};
