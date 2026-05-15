import axios from 'axios';
import apiClient from './api';
import type {
  ApiResponse,
  PadesVerifyResponse,
  Signature,
  SignatureRect,
  VerificationHistory,
} from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

export interface SignDocumentRequest {
  keyId: number;
  password: string;
  signaturePage?: number;
  signatureRect?: SignatureRect;
  reason?: string;
  location?: string;
  visible?: boolean;
}

export const signatureService = {
  sign: async (documentId: number, data: SignDocumentRequest): Promise<Signature> => {
    const response = await apiClient.post<ApiResponse<Signature>>(
      `/documents/${documentId}/sign`,
      data,
    );
    return response.data.data!;
  },

  listForDocument: async (documentId: number): Promise<Signature[]> => {
    const response = await apiClient.get<ApiResponse<Signature[]>>(
      `/documents/${documentId}/signatures`,
    );
    return response.data.data ?? [];
  },

  listForUser: async (): Promise<Signature[]> => {
    const response = await apiClient.get<ApiResponse<Signature[]>>('/signatures');
    return response.data.data ?? [];
  },

  getVerificationHistory: async (documentId: number): Promise<VerificationHistory[]> => {
    const response = await apiClient.get<ApiResponse<VerificationHistory[]>>(
      `/documents/${documentId}/verifications`,
    );
    return response.data.data ?? [];
  },

  downloadPadesPdf: async (signatureId: number, fallbackFilename: string): Promise<void> => {
    const response = await apiClient.get(`/signatures/${signatureId}/pades-pdf`, {
      responseType: 'blob',
    });
    const fromHeader = parseContentDispositionFilename(
      response.headers['content-disposition'] as string | undefined,
    );
    triggerDownload(response.data as Blob, fromHeader ?? fallbackFilename);
  },

  verifyPadesPdf: async (file: File): Promise<PadesVerifyResponse> => {
    const form = new FormData();
    form.append('file', file);
    // Public endpoint — use raw axios (skip auth interceptor) so guests can call it.
    const response = await axios.post<ApiResponse<PadesVerifyResponse>>(
      `${API_BASE_URL}/verify/pades-pdf`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return response.data.data!;
  },
};

function triggerDownload(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}

// Parses RFC 6266 Content-Disposition headers. Prefers filename*=UTF-8''...
// (handles Vietnamese / non-ASCII), falls back to filename="..." (ASCII).
function parseContentDispositionFilename(header: string | undefined): string | null {
  if (!header) return null;
  const utf8Match = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header);
  if (utf8Match) {
    try {
      return decodeURIComponent(utf8Match[1].trim());
    } catch {
      // fall through to plain filename
    }
  }
  const plainMatch = /filename\s*=\s*(?:"([^"]+)"|([^;]+))/i.exec(header);
  if (plainMatch) {
    return (plainMatch[1] ?? plainMatch[2] ?? '').trim() || null;
  }
  return null;
}
