import { create } from 'zustand';
import type { User } from '../types';

interface AuthState {
  user: User | null;
  token: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  setAuth: (user: User, token: string, refreshToken: string) => void;
  updateUser: (user: User) => void;
  clearAuth: () => void;
  initAuth: () => void;
}

const readInitialAuth = () => {
  try {
    const token = localStorage.getItem('token');
    const refreshToken = localStorage.getItem('refreshToken');
    const userStr = localStorage.getItem('user');
    if (token && userStr) {
      return {
        user: JSON.parse(userStr) as User,
        token,
        refreshToken,
        isAuthenticated: true,
      };
    }
  } catch {
    // fall through to unauthenticated
  }
  return { user: null, token: null, refreshToken: null, isAuthenticated: false };
};

const initial = readInitialAuth();

export const useAuthStore = create<AuthState>((set) => ({
  user: initial.user,
  token: initial.token,
  refreshToken: initial.refreshToken,
  isAuthenticated: initial.isAuthenticated,

  setAuth: (user, token, refreshToken) => {
    localStorage.setItem('token', token);
    localStorage.setItem('refreshToken', refreshToken);
    localStorage.setItem('user', JSON.stringify(user));
    set({ user, token, refreshToken, isAuthenticated: true });
  },

  updateUser: (user) => {
    localStorage.setItem('user', JSON.stringify(user));
    set({ user });
  },

  clearAuth: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    set({ user: null, token: null, refreshToken: null, isAuthenticated: false });
  },

  initAuth: () => {
    const token = localStorage.getItem('token');
    const refreshToken = localStorage.getItem('refreshToken');
    const userStr = localStorage.getItem('user');
    if (token && userStr) {
      const user = JSON.parse(userStr);
      set({ user, token, refreshToken, isAuthenticated: true });
    }
  },
}));
