import { useState } from 'react';
import { Link, useLocation, useNavigate, Outlet } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  FileSignature,
  LayoutDashboard,
  User as UserIcon,
  Key,
  FileText,
  ShieldCheck,
  LogOut,
  Menu,
  X,
} from 'lucide-react';
import { Button } from '../ui/button';
import LanguageSwitcher from '../ui/LanguageSwitcher';
import { useAuthStore } from '../../store/authStore';
import { authService } from '../../services/authService';
import { cn } from '../../lib/utils';

export default function AppLayout() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const { user, clearAuth } = useAuthStore();
  const [mobileOpen, setMobileOpen] = useState(false);

  const navItems = [
    { path: '/dashboard', label: t('sidebar.dashboard'), icon: LayoutDashboard },
    { path: '/profile', label: t('sidebar.profile'), icon: UserIcon },
    { path: '/keys', label: t('sidebar.keys'), icon: Key },
    { path: '/documents', label: t('sidebar.documents'), icon: FileText },
    { path: '/verify', label: t('sidebar.verify'), icon: ShieldCheck },
  ];

  const handleLogout = async () => {
    await authService.logout();
    clearAuth();
    navigate('/login');
  };

  const SidebarContent = (
    <>
      <div className="flex h-16 items-center gap-2 border-b px-6">
        <FileSignature className="h-6 w-6 text-primary" />
        <span className="font-bold">{t('sidebar.appName')}</span>
      </div>
      <nav className="flex-1 space-y-1 p-4">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname === item.path;
          return (
            <Link
              key={item.path}
              to={item.path}
              onClick={() => setMobileOpen(false)}
              className={cn(
                'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-primary text-primary-foreground'
                  : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
              )}
            >
              <Icon className="h-4 w-4" />
              {item.label}
            </Link>
          );
        })}
      </nav>
      <div className="border-t p-4">
        <div className="mb-3 px-2">
          <p className="text-sm font-medium truncate">{user?.fullName}</p>
          <p className="text-xs text-muted-foreground truncate">{user?.email}</p>
        </div>
        <div className="mb-3 flex justify-center">
          <LanguageSwitcher />
        </div>
        <Button
          variant="outline"
          size="sm"
          className="w-full"
          onClick={handleLogout}
        >
          <LogOut className="mr-2 h-4 w-4" />
          {t('sidebar.logout')}
        </Button>
      </div>
    </>
  );

  return (
    <div className="min-h-screen bg-slate-50 flex">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex w-64 border-r bg-white flex-col shrink-0">
        {SidebarContent}
      </aside>

      {/* Mobile off-canvas sidebar */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 md:hidden"
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 w-64 border-r bg-white flex flex-col transition-transform duration-200 md:hidden',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <button
          onClick={() => setMobileOpen(false)}
          className="absolute right-3 top-3 rounded-md p-1 text-muted-foreground hover:bg-accent"
          aria-label={t('documents.close')}
        >
          <X className="h-5 w-5" />
        </button>
        {SidebarContent}
      </aside>

      <div className="flex flex-1 min-w-0 flex-col">
        {/* Mobile top bar */}
        <header className="flex h-14 items-center gap-2 border-b bg-white px-4 md:hidden">
          <button
            onClick={() => setMobileOpen(true)}
            className="rounded-md p-2 text-muted-foreground hover:bg-accent"
            aria-label={t('sidebar.dashboard')}
          >
            <Menu className="h-5 w-5" />
          </button>
          <div className="flex items-center gap-2">
            <FileSignature className="h-5 w-5 text-primary" />
            <span className="font-semibold">{t('sidebar.appName')}</span>
          </div>
          <div className="ml-auto">
            <LanguageSwitcher />
          </div>
        </header>

        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
