import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Loader2, User as UserIcon, Lock, Save } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '../../components/ui/card';
import { userService } from '../../services/userService';
import { useAuthStore } from '../../store/authStore';
import { toast } from '../../store/toastStore';
import { localizeError } from '../../utils/errorMapper';

export default function ProfilePage() {
  const { t } = useTranslation();
  const { user, setAuth, token, refreshToken } = useAuthStore();
  const [isProfileLoading, setIsProfileLoading] = useState(false);
  const [isPasswordLoading, setIsPasswordLoading] = useState(false);

  const profileSchema = z.object({
    fullName: z.string().min(1, t('profile.fullNameRequired')).max(255),
    phone: z.string().max(20).optional().or(z.literal('')),
    organization: z.string().max(255).optional().or(z.literal('')),
  });

  const passwordSchema = z
    .object({
      currentPassword: z.string().min(1, t('profile.currentPasswordRequired')),
      newPassword: z
        .string()
        .min(8, t('profile.newPasswordMin'))
        .regex(/[A-Z]/, t('profile.newPasswordUpper'))
        .regex(/\d/, t('profile.newPasswordDigit')),
      confirmPassword: z.string(),
    })
    .refine((data) => data.newPassword === data.confirmPassword, {
      message: t('profile.passwordsNoMatch'),
      path: ['confirmPassword'],
    });

  type ProfileFormData = z.infer<typeof profileSchema>;
  type PasswordFormData = z.infer<typeof passwordSchema>;

  const profileForm = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      fullName: user?.fullName || '',
      phone: user?.phone || '',
      organization: user?.organization || '',
    },
  });

  const passwordForm = useForm<PasswordFormData>({
    resolver: zodResolver(passwordSchema),
  });

  useEffect(() => {
    userService.getCurrentUser().then((latest) => {
      if (token && refreshToken) {
        setAuth(latest, token, refreshToken);
      }
      profileForm.reset({
        fullName: latest.fullName,
        phone: latest.phone || '',
        organization: latest.organization || '',
      });
    }).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onSubmitProfile = async (data: ProfileFormData) => {
    setIsProfileLoading(true);
    try {
      const updated = await userService.updateProfile({
        fullName: data.fullName,
        phone: data.phone || undefined,
        organization: data.organization || undefined,
      });
      if (token && refreshToken) {
        setAuth(updated, token, refreshToken);
      }
      toast.success(t('profile.profileUpdated'));
    } catch (err: any) {
      toast.error(t('profile.updateFailed'), localizeError(err?.response?.data?.message, 'profile.failedUpdateProfile'));
    } finally {
      setIsProfileLoading(false);
    }
  };

  const onSubmitPassword = async (data: PasswordFormData) => {
    setIsPasswordLoading(true);
    try {
      await userService.changePassword({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      });
      toast.success(t('profile.passwordChanged'));
      passwordForm.reset();
    } catch (err: any) {
      toast.error(t('profile.passwordChangeFailed'), localizeError(err?.response?.data?.message, 'profile.failedChangePassword'));
    } finally {
      setIsPasswordLoading(false);
    }
  };

  return (
    <div className="container mx-auto max-w-3xl px-4 py-8">
      <div className="mb-6">
        <h1 className="text-3xl font-bold tracking-tight">{t('profile.title')}</h1>
        <p className="text-muted-foreground">
          {t('profile.subtitle')}
        </p>
      </div>

      <Card className="mb-6">
        <CardHeader>
          <div className="flex items-center gap-2">
            <UserIcon className="h-5 w-5 text-primary" />
            <CardTitle>{t('profile.personalInfo')}</CardTitle>
          </div>
          <CardDescription>
            {t('profile.personalInfoDesc')}
          </CardDescription>
        </CardHeader>
        <form onSubmit={profileForm.handleSubmit(onSubmitProfile)}>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">{t('profile.email')}</Label>
              <Input
                id="email"
                value={user?.email || ''}
                disabled
                className="bg-muted"
              />
              <p className="text-xs text-muted-foreground">
                {t('profile.emailReadonly')}
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="fullName">{t('profile.fullName')}</Label>
              <Input
                id="fullName"
                {...profileForm.register('fullName')}
                disabled={isProfileLoading}
              />
              {profileForm.formState.errors.fullName && (
                <p className="text-sm text-destructive">
                  {profileForm.formState.errors.fullName.message}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="phone">{t('profile.phone')}</Label>
              <Input
                id="phone"
                placeholder={t('profile.phonePlaceholder')}
                {...profileForm.register('phone')}
                disabled={isProfileLoading}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="organization">{t('profile.organization')}</Label>
              <Input
                id="organization"
                placeholder={t('profile.organizationPlaceholder')}
                {...profileForm.register('organization')}
                disabled={isProfileLoading}
              />
            </div>

            <div className="flex justify-end pt-2">
              <Button type="submit" disabled={isProfileLoading}>
                {isProfileLoading ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    {t('profile.saving')}
                  </>
                ) : (
                  <>
                    <Save className="mr-2 h-4 w-4" />
                    {t('profile.save')}
                  </>
                )}
              </Button>
            </div>
          </CardContent>
        </form>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <Lock className="h-5 w-5 text-primary" />
            <CardTitle>{t('profile.changePassword')}</CardTitle>
          </div>
          <CardDescription>
            {t('profile.changePasswordDesc')}
          </CardDescription>
        </CardHeader>
        <form onSubmit={passwordForm.handleSubmit(onSubmitPassword)}>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="currentPassword">{t('profile.currentPassword')}</Label>
              <Input
                id="currentPassword"
                type="password"
                {...passwordForm.register('currentPassword')}
                disabled={isPasswordLoading}
              />
              {passwordForm.formState.errors.currentPassword && (
                <p className="text-sm text-destructive">
                  {passwordForm.formState.errors.currentPassword.message}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="newPassword">{t('profile.newPassword')}</Label>
              <Input
                id="newPassword"
                type="password"
                {...passwordForm.register('newPassword')}
                disabled={isPasswordLoading}
              />
              {passwordForm.formState.errors.newPassword && (
                <p className="text-sm text-destructive">
                  {passwordForm.formState.errors.newPassword.message}
                </p>
              )}
              <p className="text-xs text-muted-foreground">
                {t('profile.newPasswordHint')}
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirmPassword">{t('profile.confirmPassword')}</Label>
              <Input
                id="confirmPassword"
                type="password"
                {...passwordForm.register('confirmPassword')}
                disabled={isPasswordLoading}
              />
              {passwordForm.formState.errors.confirmPassword && (
                <p className="text-sm text-destructive">
                  {passwordForm.formState.errors.confirmPassword.message}
                </p>
              )}
            </div>

            <div className="flex justify-end pt-2">
              <Button type="submit" disabled={isPasswordLoading}>
                {isPasswordLoading ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    {t('profile.updatingPassword')}
                  </>
                ) : (
                  <>
                    <Lock className="mr-2 h-4 w-4" />
                    {t('profile.updatePassword')}
                  </>
                )}
              </Button>
            </div>
          </CardContent>
        </form>
      </Card>
    </div>
  );
}
