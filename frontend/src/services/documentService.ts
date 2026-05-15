import apiClient from './api';
import type { ApiResponse, Document } from '../types';

export const documentService = {
  list: async (): Promise<Document[]> => {
    const response = await apiClient.get<ApiResponse<Document[]>>('/documents');
    return response.data.data ?? [];
  },

  get: async (id: number): Promise<Document> => {
    const response = await apiClient.get<ApiResponse<Document>>(`/documents/${id}`);
    return response.data.data!;
  },

  upload: async (
    file: File,
    onProgress?: (percent: number) => void,
  ): Promise<Document> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await apiClient.post<ApiResponse<Document>>(
      '/documents/upload',
      formData,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (event) => {
          if (onProgress && event.total) {
            onProgress(Math.round((event.loaded * 100) / event.total));
          }
        },
      },
    );
    return response.data.data!;
  },

  download: async (id: number, originalName: string): Promise<void> => {
    const response = await apiClient.get(`/documents/${id}/download`, {
      responseType: 'blob',
    });
    const blob = new Blob([response.data]);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', originalName);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  },

  fetchBlob: async (id: number): Promise<Blob> => {
    const response = await apiClient.get(`/documents/${id}/download`, {
      responseType: 'blob',
    });
    return new Blob([response.data], { type: 'application/pdf' });
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/documents/${id}`);
  },
};
