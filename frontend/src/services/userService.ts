import apiClient from './api';
import type { ApiResponse, User } from '../types';

export interface UpdateProfileRequest {
  fullName: string;
  phone?: string;
  organization?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export const userService = {
  getCurrentUser: async (): Promise<User> => {
    const response = await apiClient.get<ApiResponse<User>>('/me');
    return response.data.data!;
  },

  updateProfile: async (data: UpdateProfileRequest): Promise<User> => {
    const response = await apiClient.put<ApiResponse<User>>('/me', data);
    return response.data.data!;
  },

  changePassword: async (data: ChangePasswordRequest): Promise<void> => {
    await apiClient.put('/me/password', data);
  },
};
