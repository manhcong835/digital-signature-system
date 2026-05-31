import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import {
  Key as KeyIcon,
  Plus,
  Star,
  Download,
  Trash2,
  Loader2,
  X,
  ShieldAlert,
  ShieldOff,
  FileBadge,
} from 'lucide-react';
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
import { keyService } from '../../services/keyService';
import { toast } from '../../store/toastStore';
import type { KeyPair } from '../../types';
import { format } from 'date-fns';
import { getDateLocale } from '../../i18n/dateLocale';
import { localizeError } from '../../utils/errorMapper';

export default function KeysPage() {
  const { t } = useTranslation();
  const [keys, setKeys] = useState<KeyPair[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [revokingId, setRevokingId] = useState<number | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const createKeySchema = z.object({
    name: z
      .string()
      .min(1, t('keys.keyNameRequired'))
      .max(255, t('keys.keyNameMax')),
    password: z.string().min(1, t('keys.passwordRequired')),
  });

  type CreateKeyFormData = z.infer<typeof createKeySchema>;

  const form = useForm<CreateKeyFormData>({
    resolver: zodResolver(createKeySchema),
    defaultValues: { name: '', password: '' },
  });

  const loadKeys = async () => {
    try {
      const data = await keyService.list();
      setKeys(data);
    } catch (err: any) {
      toast.error(t('keys.failedLoadKeys'), localizeError(err?.response?.data?.message));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadKeys();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onCreate = async (data: CreateKeyFormData) => {
    setCreateError(null);
    setIsCreating(true);
    try {
      await keyService.create(data);
      setIsCreateOpen(false);
      form.reset();
      toast.success(t('keys.keyGenerated'));
      await loadKeys();
    } catch (err: any) {
      setCreateError(localizeError(err?.response?.data?.message, 'keys.failedGenerate'));
    } finally {
      setIsCreating(false);
    }
  };

  const handleSetDefault = async (id: number) => {
    setBusyId(id);
    try {
      await keyService.setDefault(id);
      toast.success(t('keys.defaultUpdated'));
      await loadKeys();
    } catch (err: any) {
      toast.error(t('keys.updateFailed'), localizeError(err?.response?.data?.message));
    } finally {
      setBusyId(null);
    }
  };

  const handleDownload = async (key: KeyPair) => {
    setBusyId(key.id);
    try {
      await keyService.downloadPublicKey(key.id, key.name);
    } catch (err: any) {
      toast.error(t('keys.downloadFailed'), localizeError(err?.response?.data?.message));
    } finally {
      setBusyId(null);
    }
  };

  const handleDownloadCert = async (key: KeyPair) => {
    setBusyId(key.id);
    try {
      await keyService.downloadCertificate(key.id, key.name);
    } catch (err: any) {
      toast.error(t('keys.downloadFailed'), localizeError(err?.response?.data?.message));
    } finally {
      setBusyId(null);
    }
  };

  const handleDelete = async (id: number) => {
    setBusyId(id);
    try {
      await keyService.delete(id);
      setDeletingId(null);
      toast.success(t('keys.keyDeleted'));
      await loadKeys();
    } catch (err: any) {
      toast.error(t('keys.deleteFailed'), localizeError(err?.response?.data?.message));
    } finally {
      setBusyId(null);
    }
  };

  const handleRevoke = async (id: number) => {
    setBusyId(id);
    try {
      await keyService.revoke(id);
      setRevokingId(null);
      toast.success(t('keys.keyRevoked'));
      await loadKeys();
    } catch (err: any) {
      toast.error(t('keys.revokeFailed'), localizeError(err?.response?.data?.message));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('keys.title')}</h1>
          <p className="text-muted-foreground">
            {t('keys.subtitle')}
          </p>
        </div>
        <Button onClick={() => { setCreateError(null); form.reset(); setIsCreateOpen(true); }}>
          <Plus className="mr-2 h-4 w-4" />
          {t('keys.generateKey')}
        </Button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : keys.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16 text-center">
            <KeyIcon className="mb-4 h-12 w-12 text-muted-foreground" />
            <h3 className="text-lg font-semibold">{t('keys.noKeysTitle')}</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              {t('keys.noKeysHint')}
            </p>
            <Button className="mt-4" onClick={() => setIsCreateOpen(true)}>
              <Plus className="mr-2 h-4 w-4" />
              {t('keys.generateKey')}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {keys.map((key) => (
            <Card key={key.id}>
              <CardContent className="flex items-center justify-between gap-4 py-4">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="rounded-md bg-primary/10 p-2">
                    <KeyIcon className="h-5 w-5 text-primary" />
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="font-medium truncate">{key.name}</p>
                      {key.isDefault && !key.revoked && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
                          <Star className="h-3 w-3 fill-current" />
                          {t('keys.default')}
                        </span>
                      )}
                      {key.revoked && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-destructive/10 px-2 py-0.5 text-xs font-medium text-destructive">
                          {t('keys.revoked')}
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {key.algorithm} · {t('keys.createdAt')} {format(new Date(key.createdAt), 'PPp', { locale: getDateLocale() })}
                      {key.revoked && key.revokedAt && (
                        <> · <span className="text-destructive font-medium">{t('keys.revokedAt')} {format(new Date(key.revokedAt), 'PPp', { locale: getDateLocale() })}</span></>
                      )}
                    </p>
                    {key.hasCertificate && key.certExpiresAt && (
                      <p className="text-xs text-muted-foreground">
                        {t('keys.certInfo', {
                          expires: format(new Date(key.certExpiresAt), 'PP', { locale: getDateLocale() }),
                          serial: key.certSerialNumber?.slice(0, 12) ?? '',
                        })}
                      </p>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  {!key.isDefault && !key.revoked && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleSetDefault(key.id)}
                      disabled={busyId === key.id}
                    >
                      <Star className="mr-1 h-4 w-4" />
                      {t('keys.setDefault')}
                    </Button>
                  )}
                  {!key.revoked && (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:bg-destructive/10"
                      onClick={() => setRevokingId(key.id)}
                      disabled={busyId === key.id}
                    >
                      <ShieldOff className="mr-1 h-4 w-4" />
                      {t('keys.revoke')}
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleDownload(key)}
                    disabled={busyId === key.id}
                  >
                    <Download className="mr-1 h-4 w-4" />
                    {t('keys.publicKey')}
                  </Button>
                  {key.hasCertificate && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleDownloadCert(key)}
                      disabled={busyId === key.id}
                    >
                      <FileBadge className="mr-1 h-4 w-4" />
                      {t('keys.certificate')}
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive hover:text-destructive"
                    onClick={() => setDeletingId(key.id)}
                    disabled={busyId === key.id}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="w-full max-w-md">
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <KeyIcon className="h-5 w-5 text-primary" />
                  <CardTitle>{t('keys.generateNewKey')}</CardTitle>
                </div>
                <button
                  onClick={() => setIsCreateOpen(false)}
                  className="text-muted-foreground hover:text-foreground"
                  aria-label={t('keys.close')}
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <CardDescription>
                {t('keys.keyDescription')}
              </CardDescription>
            </CardHeader>
            <form onSubmit={form.handleSubmit(onCreate)}>
              <CardContent className="space-y-4">
                {createError && (
                  <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                    {createError}
                  </div>
                )}

                <div className="space-y-2">
                  <Label htmlFor="name">{t('keys.keyName')}</Label>
                  <Input
                    id="name"
                    placeholder={t('keys.keyNamePlaceholder')}
                    {...form.register('name')}
                    disabled={isCreating}
                  />
                  {form.formState.errors.name && (
                    <p className="text-sm text-destructive">
                      {form.formState.errors.name.message}
                    </p>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="password">{t('keys.accountPassword')}</Label>
                  <Input
                    id="password"
                    type="password"
                    {...form.register('password')}
                    disabled={isCreating}
                  />
                  {form.formState.errors.password && (
                    <p className="text-sm text-destructive">
                      {form.formState.errors.password.message}
                    </p>
                  )}
                  <p className="text-xs text-muted-foreground">
                    {t('keys.passwordHelp')}
                  </p>
                </div>

                <div className="flex justify-end gap-2 pt-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setIsCreateOpen(false)}
                    disabled={isCreating}
                  >
                    {t('keys.cancel')}
                  </Button>
                  <Button type="submit" disabled={isCreating}>
                    {isCreating ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        {t('keys.generating')}
                      </>
                    ) : (
                      <>
                        <Plus className="mr-2 h-4 w-4" />
                        {t('keys.generate')}
                      </>
                    )}
                  </Button>
                </div>
              </CardContent>
            </form>
          </Card>
        </div>
      )}

      {deletingId !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="w-full max-w-md">
            <CardHeader>
              <div className="flex items-center gap-2">
                <ShieldAlert className="h-5 w-5 text-destructive" />
                <CardTitle>{t('keys.deleteConfirmTitle')}</CardTitle>
              </div>
              <CardDescription>
                {t('keys.deleteConfirmDesc')}
              </CardDescription>
            </CardHeader>
            <CardContent className="flex justify-end gap-2">
              <Button
                variant="outline"
                onClick={() => setDeletingId(null)}
                disabled={busyId === deletingId}
              >
                {t('keys.cancel')}
              </Button>
              <Button
                variant="destructive"
                onClick={() => handleDelete(deletingId)}
                disabled={busyId === deletingId}
              >
                {busyId === deletingId ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    {t('keys.deleting')}
                  </>
                ) : (
                  <>
                    <Trash2 className="mr-2 h-4 w-4" />
                    {t('keys.delete')}
                  </>
                )}
              </Button>
            </CardContent>
          </Card>
        </div>
      )}

      {revokingId !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="w-full max-w-md">
            <CardHeader>
              <div className="flex items-center gap-2">
                <ShieldAlert className="h-5 w-5 text-destructive" />
                <CardTitle>{t('keys.revokeConfirmTitle')}</CardTitle>
              </div>
              <CardDescription>
                {t('keys.revokeConfirmDesc')}
              </CardDescription>
            </CardHeader>
            <CardContent className="flex justify-end gap-2">
              <Button
                variant="outline"
                onClick={() => setRevokingId(null)}
                disabled={busyId === revokingId}
              >
                {t('keys.cancel')}
              </Button>
              <Button
                variant="destructive"
                onClick={() => handleRevoke(revokingId)}
                disabled={busyId === revokingId}
              >
                {busyId === revokingId ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    {t('keys.revoking')}
                  </>
                ) : (
                  <>
                    <ShieldOff className="mr-2 h-4 w-4" />
                    {t('keys.revoke')}
                  </>
                )}
              </Button>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
