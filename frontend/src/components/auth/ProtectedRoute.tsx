import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import type { ReactNode } from 'react';

interface ProtectedRouteProps {
  children: ReactNode;
  allowMustChangePassword?: boolean;
}

export default function ProtectedRoute({
  children,
  allowMustChangePassword = false,
}: ProtectedRouteProps) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (
    user?.mustChangePassword &&
    !allowMustChangePassword &&
    location.pathname !== '/force-change-password'
  ) {
    return <Navigate to="/force-change-password" replace />;
  }

  return <>{children}</>;
}
