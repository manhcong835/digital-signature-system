import { useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import LoginPage from './pages/Auth/LoginPage';
import RegisterPage from './pages/Auth/RegisterPage';
import VerifyOtpPage from './pages/Auth/VerifyOtpPage';
import Verify2faPage from './pages/Auth/Verify2faPage';
import ForgotPasswordPage from './pages/Auth/ForgotPasswordPage';
import ForceChangePasswordPage from './pages/Auth/ForceChangePasswordPage';
import DashboardPage from './pages/Dashboard/DashboardPage';
import ProfilePage from './pages/Profile/ProfilePage';
import KeysPage from './pages/Keys/KeysPage';
import DocumentsPage from './pages/Documents/DocumentsPage';
import VerifyPage from './pages/Verify/VerifyPage';
import TrustSetupPage from './pages/TrustSetup/TrustSetupPage';
import NotFoundPage from './pages/NotFound/NotFoundPage';
import ProtectedRoute from './components/auth/ProtectedRoute';
import AppLayout from './components/layout/AppLayout';
import ErrorBoundary from './components/layout/ErrorBoundary';
import ToastViewport from './components/ui/toast';

function App() {
  const { initAuth, isAuthenticated } = useAuthStore();

  useEffect(() => {
    initAuth();
  }, [initAuth]);

  return (
    <ErrorBoundary>
      <Router>
        <Routes>
          <Route
            path="/"
            element={
              <Navigate to={isAuthenticated ? '/dashboard' : '/login'} replace />
            }
          />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/verify-otp" element={<VerifyOtpPage />} />
          <Route path="/verify-2fa" element={<Verify2faPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/verify" element={<VerifyPage />} />

          <Route
            path="/force-change-password"
            element={
              <ProtectedRoute allowMustChangePassword>
                <ForceChangePasswordPage />
              </ProtectedRoute>
            }
          />

          <Route
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/keys" element={<KeysPage />} />
            <Route path="/documents" element={<DocumentsPage />} />
            <Route path="/trust-setup" element={<TrustSetupPage />} />
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
        <ToastViewport />
      </Router>
    </ErrorBoundary>
  );
}

export default App;
