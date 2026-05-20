import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Loader2, MailCheck } from 'lucide-react';
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

export default function VerifyOtpPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);
  const email = (location.state as { email?: string } | null)?.email ?? '';

  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [cooldown, setCooldown] = useState(60);

  useEffect(() => {
    if (!email) {
      navigate('/register', { replace: true });
    }
  }, [email, navigate]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

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
    setInfo(null);
    setIsLoading(true);
    try {
      const response = await authService.verifyEmail({ email, code: data.code });
      if (response.token && response.refreshToken && response.user) {
        setAuth(response.user, response.token, response.refreshToken);
        navigate('/dashboard');
      }
    } catch (err: any) {
      setError(localizeError(err?.response?.data?.message, 'auth.loginFailed'));
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    setError(null);
    setInfo(null);
    try {
      await authService.resendOtp({ email, type: 'REGISTRATION' });
      setInfo(t('auth.verifyOtpResent'));
      setCooldown(60);
    } catch (err: any) {
      setError(localizeError(err?.response?.data?.message, 'auth.loginFailed'));
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="space-y-1">
          <div className="flex items-center justify-center mb-4">
            <div className="rounded-full bg-primary/10 p-3">
              <MailCheck className="h-6 w-6 text-primary" />
            </div>
          </div>
          <CardTitle className="text-2xl text-center">{t('auth.verifyOtpTitle')}</CardTitle>
          <CardDescription className="text-center">
            {t('auth.verifyOtpSubtitle', { email })}
          </CardDescription>
        </CardHeader>
        <form onSubmit={handleSubmit(onSubmit)}>
          <CardContent className="space-y-4">
            {error && (
              <div className="rounded-md bg-destructive/10 border border-destructive/30 p-3 text-sm text-destructive">
                {error}
              </div>
            )}
            {info && (
              <div className="rounded-md bg-emerald-500/10 border border-emerald-500/30 p-3 text-sm text-emerald-700">
                {info}
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
          <CardFooter className="flex flex-col space-y-3">
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t('auth.verifyOtpSubmitting')}
                </>
              ) : (
                t('auth.verifyOtpSubmit')
              )}
            </Button>
            <Button
              type="button"
              variant="ghost"
              className="w-full"
              disabled={cooldown > 0}
              onClick={handleResend}
            >
              {cooldown > 0
                ? t('auth.verifyOtpResendIn', { seconds: cooldown })
                : t('auth.verifyOtpResend')}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
