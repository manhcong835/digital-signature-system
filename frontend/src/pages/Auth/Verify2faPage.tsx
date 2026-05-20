import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Loader2, ShieldCheck } from 'lucide-react';
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

export default function Verify2faPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);

  const state = location.state as { email?: string; challengeToken?: string } | null;
  const email = state?.email ?? '';
  const challengeToken = state?.challengeToken ?? '';

  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!challengeToken) {
      navigate('/login', { replace: true });
    }
  }, [challengeToken, navigate]);

  const schema = z.object({
    code: z
      .string()
      .min(1, t('auth.codeRequired'))
      .regex(/^\d{6}$/, t('auth.codeLength', { length: 6 })),
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
      const response = await authService.verify2fa({ challengeToken, code: data.code });
      if (response.token && response.refreshToken && response.user) {
        setAuth(response.user, response.token, response.refreshToken);
        navigate(response.user.mustChangePassword ? '/force-change-password' : '/dashboard');
      }
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
            <div className="rounded-full bg-emerald-500/10 p-3">
              <ShieldCheck className="h-6 w-6 text-emerald-600" />
            </div>
          </div>
          <CardTitle className="text-2xl text-center">{t('auth.verify2faTitle')}</CardTitle>
          <CardDescription className="text-center">
            {t('auth.verify2faSubtitle')}
            {email ? <span className="block mt-1 text-foreground/80">{email}</span> : null}
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
              <Label htmlFor="code">{t('auth.verifyOtpCode')}</Label>
              <Input
                id="code"
                inputMode="numeric"
                maxLength={6}
                placeholder={t('auth.verifyOtpCodePlaceholder')}
                className="text-center text-2xl tracking-[0.5em] font-mono"
                {...register('code')}
                disabled={isLoading}
              />
              {errors.code && (
                <p className="text-sm text-destructive">{errors.code.message}</p>
              )}
            </div>
          </CardContent>
          <CardFooter>
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t('auth.verifyOtpSubmitting')}
                </>
              ) : (
                t('auth.verify2faSubmit')
              )}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
