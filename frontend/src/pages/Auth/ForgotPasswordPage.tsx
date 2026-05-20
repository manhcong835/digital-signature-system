import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { KeyRound, Loader2 } from 'lucide-react';
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
import { localizeError } from '../../utils/errorMapper';

export default function ForgotPasswordPage() {
  const { t } = useTranslation();
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const schema = z.object({
    email: z.string().email(t('auth.invalidEmail')),
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
      await authService.forgotPassword({ email: data.email });
      setInfo(t('auth.forgotPasswordSent'));
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
            <div className="rounded-full bg-amber-500/10 p-3">
              <KeyRound className="h-6 w-6 text-amber-600" />
            </div>
          </div>
          <CardTitle className="text-2xl text-center">{t('auth.forgotPasswordTitle')}</CardTitle>
          <CardDescription className="text-center">
            {t('auth.forgotPasswordSubtitle')}
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
              <Label htmlFor="email">{t('auth.email')}</Label>
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
          </CardContent>
          <CardFooter className="flex flex-col space-y-3">
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t('auth.forgotPasswordSubmitting')}
                </>
              ) : (
                t('auth.forgotPasswordSubmit')
              )}
            </Button>
            <Link
              to="/login"
              className="text-sm text-center text-muted-foreground hover:text-primary"
            >
              {t('auth.forgotPasswordBackToLogin')}
            </Link>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
