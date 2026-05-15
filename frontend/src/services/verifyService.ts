import apiClient from './api';
import type { ApiResponse, VerifyResponse } from '../types';

export const verifyService = {
  verifyDocument: async (
    documentId: number,
    signatureId?: number,
  ): Promise<VerifyResponse> => {
    const body = signatureId ? { signatureId } : {};
    const response = await apiClient.post<ApiResponse<VerifyResponse>>(
      `/documents/${documentId}/verify`,
      body,
    );
    return response.data.data!;
  },
};
