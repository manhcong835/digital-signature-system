import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Loader2, FileSignature, Copy, CheckCircle2, Download } from 'lucide-react';
import { signatureService } from '../../services/signatureService';
import { toast } from '../../store/toastStore';
import type { Signature } from '../../types';
import { format } from 'date-fns';
import { getDateLocale } from '../../i18n/dateLocale';
import { localizeError } from '../../utils/errorMapper';
import { cn } from '../../lib/utils';

interface Props {
  documentId: number;
  documentName?: string;
  refreshKey?: number;
}

export default function DocumentSignatureList({ documentId, documentName, refreshKey }: Props) {
  const { t } = useTranslation();
  const [signatures, setSignatures] = useState<Signature[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [downloadingId, setDownloadingId] = useState<number | null>(null);

  const baseName = (documentName ?? 'document').replace(/\.pdf$/i, '');

  const handleDownloadPades = async (sig: Signature) => {
    setDownloadingId(sig.id);
    try {
      await signatureService.downloadPadesPdf(sig.id, `${baseName}-signed.pdf`);
    } catch (err: any) {
      toast.error(
        t('pades.downloadFailed'),
        localizeError(err?.response?.data?.message),
      );
    } finally {
      setDownloadingId(null);
    }
  };

  useEffect(() => {
    setIsLoading(true);
    signatureService
      .listForDocument(documentId)
      .then(setSignatures)
      .catch((err) => setError(localizeError(err?.response?.data?.message, 'documents.failedLoadDocs')))
      .finally(() => setIsLoading(false));
  }, [documentId, refreshKey]);

  const handleCopy = async (sig: Signature) => {
    if (!sig.documentHash) return;
    try {
      await navigator.clipboard.writeText(sig.documentHash);
      setCopiedId(sig.id);
      setTimeout(() => setCopiedId((id) => (id === sig.id ? null : id)), 1500);
    } catch {
      // ignore
    }
  };

  const statusLabel = (s?: string) => {
    switch (s) {
      case 'VALID':
        return t('dashboard.valid');
      case 'TAMPERED':
        return t('dashboard.tampered');
      case 'INVALID':
        return t('dashboard.invalid');
      default:
        return s ?? '';
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 py-3 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        {t('documents.loadingSignatures')}
      </div>
    );
  }

  if (error) {
    return <p className="py-2 text-sm text-destructive">{error}</p>;
  }

  if (signatures.length === 0) {
    return <p className="py-2 text-sm text-muted-foreground">{t('documents.noSignatures')}</p>;
  }

  return (
    <div className="space-y-2">
      {signatures.map((sig) => (
        <div
          key={sig.id}
          className="rounded-md border bg-muted/30 p-3 text-sm"
        >
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2 min-w-0">
              <FileSignature className="h-4 w-4 text-primary shrink-0" />
              <div className="min-w-0">
                <p className="font-medium truncate">
                  {sig.keyName ?? `Key #${sig.keyId}`}
                  <span className="ml-2 text-xs font-normal text-muted-foreground">
                    {sig.algorithm}
                  </span>
                </p>
                <p className="text-xs text-muted-foreground">
                  {t('documents.signedBy')} {sig.signerName ?? sig.signerEmail ?? t('documents.unknownSigner')} ·{' '}
                  {format(new Date(sig.signedAt), 'PPp', { locale: getDateLocale() })}
                </p>
              </div>
            </div>
            {sig.verifyStatus && (
              <span className={cn(
                "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium",
                sig.verifyStatus === 'VALID' && "bg-green-100 text-green-800",
                sig.verifyStatus === 'TAMPERED' && "bg-amber-100 text-amber-800",
                sig.verifyStatus === 'INVALID' && "bg-rose-100 text-rose-800"
              )}>
                <CheckCircle2 className="h-3 w-3" />
                {statusLabel(sig.verifyStatus)}
              </span>
            )}
          </div>
          <div className="mt-2 flex items-center gap-2">
            <p className="truncate font-mono text-xs text-muted-foreground/80">
              {t('documents.hashPrefix')}: {sig.documentHash.substring(0, 48)}…
            </p>
            <button
              onClick={() => handleCopy(sig)}
              className="text-muted-foreground hover:text-foreground"
              title={t('documents.copyFullHash')}
            >
              {copiedId === sig.id ? (
                <CheckCircle2 className="h-3 w-3 text-green-600" />
              ) : (
                <Copy className="h-3 w-3" />
              )}
            </button>
          </div>
          {sig.padesPdfAvailable && (
            <div className="mt-2">
              <button
                type="button"
                onClick={() => handleDownloadPades(sig)}
                disabled={downloadingId === sig.id}
                className="inline-flex items-center gap-1 rounded-md border bg-background px-2 py-1 text-xs font-medium hover:bg-accent disabled:opacity-50"
              >
                {downloadingId === sig.id ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  <Download className="h-3 w-3" />
                )}
                {t('pades.downloadSignedPdf')}
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
