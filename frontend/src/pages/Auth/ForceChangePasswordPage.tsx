import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Loader2, ShieldAlert } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '../../components/ui/card';
import { authService } from '../../services/authService';
import { useAuthStore } from '../../store/authStore';
import { localizeError } from '../../utils/errorMapper';

export default function ForceChangePasswordPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const updateUser = useAuthStore((s) => s.updateUser);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const schema = z
    .object({
      currentPassword: z.string().min(1, t('auth.passwordRequired')),
      newPassword: z
        .string()
        .min(8, t('auth.passwordMinLength'))
        .regex(/[A-Z]/, t('auth.passwordUppercase'))
        .regex(/\d/, t('auth.passwordDigit')),
      confirmPassword: z.string(),
    })
    .refine((d) => d.newPassword === d.confirmPassword, {
      message: t('auth.passwordsNoMatch'),
      path: ['confirmPassword'],
    });

  type FormData = z.infer<typeof schema>;
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: FormData) => {
    setError(null);
    setIsLoading(true);
    try {
      await authService.changePassword({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      });
      if (user) {
        updateUser({ ...user, mustChangePassword: false });
      }
      // Force a fresh login so the new JWT (without the mustChangePassword claim) is used.
      clearAuth();
      navigate('/login', { replace: true });
    } catch (err: any) {
      setError(localizeError(err?.response?.data?.message, 'auth.loginFailed'));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="space-y-1">
          <div className="flex items-center justify-center mb-4">
            <div className="rounded-full bg-red-500/10 p-3">
              <ShieldAlert className="h-6 w-6 text-red-600" />
            </div>
          </div>
          <CardTitle className="text-2xl text-center">{t('auth.forceChangeTitle')}</CardTitle>
          <CardDescription className="text-center">
            {t('auth.forceChangeSubtitle')}
          </CardDescription>
        </CardHeader>
        <form onSubmit={handleSubmit(onSubmit)}>
          <CardContent className="space-y-4">
            {error && (
              <div className="rounded-md bg-destructive/10 border border-destructive/30 p-3 text-sm text-destructive">
                {error}
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor="currentPassword">{t('auth.forceChangeCurrent')}</Label>
              <Input
                id="currentPassword"
                type="password"
                placeholder="••••••••"
                {...register('currentPassword')}
                disabled={isLoading}
              />
              {errors.currentPassword && (
                <p className="text-sm text-destructive">{errors.currentPassword.message}</p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="newPassword">{t('auth.forceChangeNew')}</Label>
              <Input
                id="newPassword"
                type="password"
                placeholder="••••••••"
                {...register('newPassword')}
                disabled={isLoading}
              />
              {errors.newPassword && (
                <p className="text-sm text-destructive">{errors.newPassword.message}</p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">{t('auth.confirmNewPassword')}</Label>
              <Input
                id="confirmPassword"
                type="password"
                placeholder="••••••••"
                {...register('confirmPassword')}
                disabled={isLoading}
              />
              {errors.confirmPassword && (
                <p className="text-sm text-destructive">{errors.confirmPassword.message}</p>
              )}
            </div>
          </CardContent>
          <CardFooter>
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t('auth.forceChangeSubmitting')}
                </>
              ) : (
                t('auth.forceChangeSubmit')
              )}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
