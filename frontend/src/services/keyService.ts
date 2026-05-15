import apiClient from './api';
import type { ApiResponse, KeyPair } from '../types';

export interface CreateKeyRequest {
  name: string;
  password: string;
}

export const keyService = {
  list: async (): Promise<KeyPair[]> => {
    const response = await apiClient.get<ApiResponse<KeyPair[]>>('/keys');
    return response.data.data ?? [];
  },

  get: async (id: number): Promise<KeyPair> => {
    const response = await apiClient.get<ApiResponse<KeyPair>>(`/keys/${id}`);
    return response.data.data!;
  },

  create: async (data: CreateKeyRequest): Promise<KeyPair> => {
    const response = await apiClient.post<ApiResponse<KeyPair>>('/keys', data);
    return response.data.data!;
  },

  setDefault: async (id: number): Promise<KeyPair> => {
    const response = await apiClient.put<ApiResponse<KeyPair>>(`/keys/${id}/default`);
    return response.data.data!;
  },

  revoke: async (id: number): Promise<KeyPair> => {
    const response = await apiClient.put<ApiResponse<KeyPair>>(`/keys/${id}/revoke`);
    return response.data.data!;
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/keys/${id}`);
  },

  downloadPublicKey: async (id: number, name: string): Promise<void> => {
    const response = await apiClient.get(`/keys/${id}/public-key`, {
      responseType: 'blob',
    });
    const blob = new Blob([response.data], { type: 'application/octet-stream' });
    const safeName = name.replace(/[^a-zA-Z0-9._-]/g, '_');
    download(blob, `${safeName}_public.pem`);
  },

  downloadCertificate: async (id: number, name: string): Promise<void> => {
    const response = await apiClient.get(`/keys/${id}/certificate`, {
      responseType: 'blob',
    });
    const blob = new Blob([response.data], { type: 'application/x-pem-file' });
    const safeName = name.replace(/[^a-zA-Z0-9._-]/g, '_');
    download(blob, `${safeName}.pem`);
  },
};

function download(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
