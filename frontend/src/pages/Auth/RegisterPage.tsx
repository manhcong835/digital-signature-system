import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Loader2, UserPlus } from 'lucide-react';
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

export default function RegisterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const registerSchema = z.object({
    fullName: z.string().min(1, t('auth.fullNameRequired')).max(255),
    email: z.string().email(t('auth.invalidEmail')),
    password: z
      .string()
      .min(8, t('auth.passwordMinLength'))
      .regex(/[A-Z]/, t('auth.passwordUppercase'))
      .regex(/\d/, t('auth.passwordDigit')),
    confirmPassword: z.string(),
    phone: z.string().max(20).optional().or(z.literal('')),
    organization: z.string().max(255).optional().or(z.literal('')),
  }).refine((data) => data.password === data.confirmPassword, {
    message: t('auth.passwordsNoMatch'),
    path: ['confirmPassword'],
  });

  type RegisterFormData = z.infer<typeof registerSchema>;

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
  });

  const onSubmit = async (data: RegisterFormData) => {
    setError(null);
    setIsLoading(true);
    try {
      const { confirmPassword: _confirm, ...registerData } = data;
      void _confirm;
      const response = await authService.register({
        ...registerData,
        phone: registerData.phone || undefined,
        organization: registerData.organization || undefined,
      });
      if (response.requiresEmailVerification) {
        navigate('/verify-otp', { state: { email: data.email } });
        return;
      }
      if (response.token && response.refreshToken && response.user) {
        setAuth(response.user, response.token, response.refreshToken);
        navigate('/dashboard');
      }
    } catch (err: any) {
      setError(localizeError(err?.response?.data?.message, 'auth.registerFailed'));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100 p-4 py-8">
      <Card className="w-full max-w-md">
        <CardHeader className="space-y-1">
          <div className="flex items-center justify-center mb-4">
            <div className="rounded-full bg-primary/10 p-3">
              <UserPlus className="h-6 w-6 text-primary" />
            </div>
          </div>
          <CardTitle className="text-2xl text-center">{t('auth.registerTitle')}</CardTitle>
          <CardDescription className="text-center">
            {t('auth.registerSubtitle')}
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
              <Label htmlFor="fullName">{t('auth.fullName')} *</Label>
              <Input
                id="fullName"
                placeholder={t('auth.fullNamePlaceholder')}
                {...register('fullName')}
                disabled={isLoading}
              />
              {errors.fullName && (
                <p className="text-sm text-destructive">{errors.fullName.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">{t('auth.email')} *</Label>
              <Input
                id="email"
                type="email"
                placeholder={t('auth.emailPlaceholder')}
                {...register('email')}
                disabled={isLoading}
              />
              {errors.email && (
                <p className="text-sm text-destructive">{errors.email.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">{t('auth.password')} *</Label>
              <Input
                id="password"
                type="password"
                placeholder="••••••••"
                {...register('password')}
                disabled={isLoading}
              />
              {errors.password && (
                <p className="text-sm text-destructive">{errors.password.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirmPassword">{t('auth.confirmPassword')} *</Label>
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

            <div className="space-y-2">
              <Label htmlFor="phone">{t('auth.phone')}</Label>
              <Input
                id="phone"
                placeholder={t('auth.phonePlaceholder')}
                {...register('phone')}
                disabled={isLoading}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="organization">{t('auth.organization')}</Label>
              <Input
                id="organization"
                placeholder={t('auth.organizationPlaceholder')}
                {...register('organization')}
                disabled={isLoading}
              />
            </div>
          </CardContent>
          <CardFooter className="flex flex-col space-y-4">
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t('auth.creatingAccount')}
                </>
              ) : (
                t('auth.createAccount')
              )}
            </Button>
            <p className="text-sm text-center text-muted-foreground">
              {t('auth.haveAccount')}{' '}
              <Link to="/login" className="text-primary font-medium hover:underline">
                {t('auth.signIn')}
              </Link>
            </p>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
