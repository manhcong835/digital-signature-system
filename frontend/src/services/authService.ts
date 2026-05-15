import apiClient from './api';
import type {
  LoginRequest,
  RegisterRequest,
  AuthResponse,
  ApiResponse,
  VerifyEmailRequest,
  Verify2faRequest,
  ForgotPasswordRequest,
  ResendOtpRequest,
  ChangePasswordRequest,
} from '../types';

export const authService = {
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<ApiResponse<AuthResponse>>('/auth/login', data);
    return response.data.data!;
  },

  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<ApiResponse<AuthResponse>>('/auth/register', data);
    return response.data.data!;
  },

  verifyEmail: async (data: VerifyEmailRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<ApiResponse<AuthResponse>>('/auth/verify-email', data);
    return response.data.data!;
  },

  verify2fa: async (data: Verify2faRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<ApiResponse<AuthResponse>>('/auth/verify-2fa', data);
    return response.data.data!;
  },

  forgotPassword: async (data: ForgotPasswordRequest): Promise<void> => {
    await apiClient.post<ApiResponse<void>>('/auth/forgot-password', data);
  },

  resendOtp: async (data: ResendOtpRequest): Promise<void> => {
    await apiClient.post<ApiResponse<void>>('/auth/resend-otp', data);
  },

  changePassword: async (data: ChangePasswordRequest): Promise<void> => {
    await apiClient.put<ApiResponse<void>>('/me/password', data);
  },

  logout: async (): Promise<void> => {
    try {
      await apiClient.post('/auth/logout');
    } finally {
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('user');
    }
  },

  refreshToken: async (refreshToken: string): Promise<AuthResponse> => {
    const response = await apiClient.post<ApiResponse<AuthResponse>>('/auth/refresh', { refreshToken });
    return response.data.data!;
  },
};
