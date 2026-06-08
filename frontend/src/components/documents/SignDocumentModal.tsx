import { lazy, Suspense, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { FileSignature, Loader2, X } from 'lucide-react';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/card';
import { keyService } from '../../services/keyService';
import { signatureService } from '../../services/signatureService';
import { documentService } from '../../services/documentService';
import type { KeyPair, SignatureRect } from '../../types';
import { localizeError } from '../../utils/errorMapper';

const PdfPositionPicker = lazy(() => import('../pdf/PdfPositionPicker'));

interface Props {
  documentId: number;
  documentName: string;
  documentMimeType?: string;
  onClose: () => void;
  onSigned: () => void;
}

export default function SignDocumentModal({
  documentId,
  documentName,
  documentMimeType,
  onClose,
  onSigned,
}: Props) {
  const { t } = useTranslation();
  const [keys, setKeys] = useState<KeyPair[]>([]);
  const [isLoadingKeys, setIsLoadingKeys] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSigning, setIsSigning] = useState(false);
  const [pdfBlob, setPdfBlob] = useState<Blob | null>(null);
  const [pdfError, setPdfError] = useState<string | null>(null);
  const [signaturePage, setSignaturePage] = useState<number | null>(null);
  const [signatureRect, setSignatureRect] = useState<SignatureRect | null>(null);
  const [visible, setVisible] = useState<boolean>(true);

  const isPdf = documentMimeType === 'application/pdf';

  const schema = z.object({
    keyId: z.string().min(1, t('documents.selectKeyRequired')),
    password: z.string().min(1, t('documents.passwordRequired')),
    reason: z.string().max(255).optional(),
    location: z.string().max(255).optional(),
  });

  type FormData = z.infer<typeof schema>;

  const form = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { keyId: '', password: '', reason: '', location: '' },
  });

  useEffect(() => {
    keyService
      .list()
      .then((data) => {
        setKeys(data);
        const def = data.find((k) => k.isDefault);
        if (def) form.setValue('keyId', String(def.id));
        else if (data.length > 0) form.setValue('keyId', String(data[0].id));
      })
      .catch(() => setError(t('documents.failedLoadKeys')))
      .finally(() => setIsLoadingKeys(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!isPdf) return;
    let cancelled = false;
    documentService
      .fetchBlob(documentId)
      .then((blob) => {
        if (!cancelled) setPdfBlob(blob);
      })
      .catch(() => {
        if (!cancelled) setPdfError(t('pades.pdfLoadFailed'));
      });
    return () => {
      cancelled = true;
    };
  }, [documentId, isPdf, t]);

  const onSubmit = async (data: FormData) => {
    setError(null);
    setIsSigning(true);
    try {
      await signatureService.sign(documentId, {
        keyId: Number(data.keyId),
        password: data.password,
        reason: data.reason || undefined,
        location: data.location || undefined,
        signaturePage: visible ? signaturePage ?? undefined : undefined,
        signatureRect: visible ? signatureRect ?? undefined : undefined,
        visible: isPdf ? visible : undefined,
      });
      onSigned();
    } catch (err: any) {
      setError(localizeError(err?.response?.data?.message, 'documents.failedSign'));
    } finally {
      setIsSigning(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <Card className={`w-full ${isPdf ? 'max-w-3xl' : 'max-w-md'} max-h-[95vh] overflow-y-auto`}>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <FileSignature className="h-5 w-5 text-primary" />
              <CardTitle>{t('documents.signDocumentTitle')}</CardTitle>
            </div>
            <button
              onClick={onClose}
              className="text-muted-foreground hover:text-foreground"
              aria-label={t('documents.close')}
              disabled={isSigning}
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          <CardDescription className="truncate">{documentName}</CardDescription>
        </CardHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent className="space-y-4">
            {error && (
              <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                {error}
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="keyId">{t('documents.signingKey')}</Label>
              {isLoadingKeys ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  {t('documents.loadingKeys')}
                </div>
              ) : keys.length === 0 ? (
                <p className="text-sm text-destructive">
                  {t('documents.noKeysAvail')}
                </p>
              ) : (
                <select
                  id="keyId"
                  {...form.register('keyId')}
                  disabled={isSigning}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {keys.map((k) => (
                    <option key={k.id} value={k.id}>
                      {k.name} {k.isDefault ? `(${t('documents.default')})` : ''} · {k.algorithm}
                    </option>
                  ))}
                </select>
              )}
              {form.formState.errors.keyId && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.keyId.message}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">{t('documents.accountPassword')}</Label>
              <Input
                id="password"
                type="password"
                {...form.register('password')}
                disabled={isSigning}
              />
              {form.formState.errors.password && (
                <p className="text-sm text-destructive">
                  {form.formState.errors.password.message}
                </p>
              )}
              <p className="text-xs text-muted-foreground">
                {t('documents.passwordHelp')}
              </p>
            </div>

            {isPdf && (
              <>
                <div className="space-y-2">
                  <Label>{t('pades.visibilityLabel')}</Label>
                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    <label
                      className={`flex cursor-pointer flex-col gap-1 rounded-md border p-3 text-sm transition ${
                        visible ? 'border-primary bg-primary/5' : 'border-input hover:bg-muted/30'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <input
                          type="radio"
                          name="signatureVisibility"
                          checked={visible}
                          onChange={() => setVisible(true)}
                          disabled={isSigning}
                        />
                        <span className="font-medium">{t('pades.visibleOption')}</span>
                      </div>
                      <span className="pl-6 text-xs text-muted-foreground">
                        {t('pades.visibleOptionHint')}
                      </span>
                    </label>
                    <label
                      className={`flex cursor-pointer flex-col gap-1 rounded-md border p-3 text-sm transition ${
                        !visible ? 'border-primary bg-primary/5' : 'border-input hover:bg-muted/30'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <input
                          type="radio"
                          name="signatureVisibility"
                          checked={!visible}
                          onChange={() => setVisible(false)}
                          disabled={isSigning}
                        />
                        <span className="font-medium">{t('pades.invisibleOption')}</span>
                      </div>
                      <span className="pl-6 text-xs text-muted-foreground">
                        {t('pades.invisibleOptionHint')}
                      </span>
                    </label>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <Label htmlFor="reason">{t('pades.reasonLabel')}</Label>
                    <Input
                      id="reason"
                      placeholder={t('pades.reasonPlaceholder')}
                      {...form.register('reason')}
                      disabled={isSigning}
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="location">{t('pades.locationLabel')}</Label>
                    <Input
                      id="location"
                      placeholder={t('pades.locationPlaceholder')}
                      {...form.register('location')}
                      disabled={isSigning}
                    />
                  </div>
                </div>

                {visible && (
                  <div className="space-y-2">
                    <Label>{t('pades.positionLabel')}</Label>
                    {pdfError ? (
                      <p className="text-sm text-destructive">{pdfError}</p>
                    ) : !pdfBlob ? (
                      <div className="flex items-center gap-2 rounded-md border bg-muted/30 p-4 text-sm text-muted-foreground">
                        <Loader2 className="h-4 w-4 animate-spin" />
                        {t('pades.loadingPdf')}
                      </div>
                    ) : (
                      <Suspense
                        fallback={
                          <div className="flex items-center gap-2 rounded-md border bg-muted/30 p-4 text-sm text-muted-foreground">
                            <Loader2 className="h-4 w-4 animate-spin" />
                            {t('pades.loadingPdf')}
                          </div>
                        }
                      >
                        <PdfPositionPicker
                          pdfBlob={pdfBlob}
                          disabled={isSigning}
                          onChange={(page, rect) => {
                            setSignaturePage(page);
                            setSignatureRect(rect);
                          }}
                        />
                      </Suspense>
                    )}
                  </div>
                )}
              </>
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={onClose} disabled={isSigning}>
                {t('documents.cancel')}
              </Button>
              <Button type="submit" disabled={isSigning || keys.length === 0}>
                {isSigning ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    {t('documents.signing')}
                  </>
                ) : (
                  <>
                    <FileSignature className="mr-2 h-4 w-4" />
                    {t('documents.signDocumentTitle')}
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
