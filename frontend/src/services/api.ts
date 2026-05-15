import axios from 'axios';
import type { AxiosError, InternalAxiosRequestConfig } from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      const isAuthEndpoint = error.config?.url?.includes('/auth/');
      if (!isAuthEndpoint) {
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
      }
    } else if (error.response?.status === 403) {
      const body = error.response.data as { message?: string } | undefined;
      if (body?.message === 'MUST_CHANGE_PASSWORD') {
        const userStr = localStorage.getItem('user');
        if (userStr) {
          try {
            const user = JSON.parse(userStr);
            user.mustChangePassword = true;
            localStorage.setItem('user', JSON.stringify(user));
          } catch {
            // ignore
          }
        }
        if (window.location.pathname !== '/force-change-password') {
          window.location.href = '/force-change-password';
        }
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
