import { useCallback, useEffect, useState } from 'react';
import { useDropzone } from 'react-dropzone';
import { useTranslation } from 'react-i18next';
import {
  FileText,
  UploadCloud,
  Download,
  Trash2,
  Loader2,
  ShieldAlert,
  FileSignature,
  ChevronDown,
  ChevronRight,
} from 'lucide-react';
import { Button } from '../../components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '../../components/ui/card';
import { cn } from '../../lib/utils';
import { documentService } from '../../services/documentService';
import { signatureService } from '../../services/signatureService';
import { toast } from '../../store/toastStore';
import type { Document, VerificationHistory } from '../../types';
import SignDocumentModal from '../../components/documents/SignDocumentModal';
import DocumentSignatureList from '../../components/documents/DocumentSignatureList';
import { format } from 'date-fns';
import { getDateLocale } from '../../i18n/dateLocale';
import { localizeError } from '../../utils/errorMapper';
import { Link } from 'react-router-dom';
import { Info, X } from 'lucide-react';

const TRUST_BANNER_KEY = 'trust-banner-dismissed-v1';

const MAX_FILE_SIZE = 10 * 1024 * 1024;

const ACCEPTED_TYPES = {
  'application/pdf': ['.pdf'],
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
  'text/plain': ['.txt'],
};

function humanReadableSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i++;
  }
  return `${value.toFixed(1)} ${units[i]}`;
}

export default function DocumentsPage() {
  const { t } = useTranslation();
  const [documents, setDocuments] = useState<Document[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [signingDoc, setSigningDoc] = useState<Document | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [signatureRefresh, setSignatureRefresh] = useState<Record<number, number>>({});
  const [verifications, setVerifications] = useState<Record<number, VerificationHistory[]>>({});
  const [isLoadingVerifications, setIsLoadingVerifications] = useState<Record<number, boolean>>({});
  const [bannerDismissed, setBannerDismissed] = useState(() => {
    try {
      return localStorage.getItem(TRUST_BANNER_KEY) === '1';
    } catch {
      return false;
    }
  });

  const loadDocuments = async () => {
    try {
      const data = await documentService.list();
      setDocuments(data);
    } catch (err: any) {
      toast.error(t('documents.failedLoadDocs'), localizeError(err?.response?.data?.message));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadDocuments();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onDrop = useCallback(async (acceptedFiles: File[], fileRejections: any[]) => {
    if (fileRejections.length > 0) {
      const rejection = fileRejections[0];
      const code = rejection.errors?.[0]?.code;
      let text = rejection.errors?.[0]?.message || t('documents.fileRejected');
      if (code === 'file-too-large') text = t('documents.fileTooLarge');
      if (code === 'file-invalid-type') text = t('documents.unsupportedType');
      toast.error(t('documents.uploadRejected'), text);
      return;
    }
    if (acceptedFiles.length === 0) return;

    const file = acceptedFiles[0];
    setIsUploading(true);
    setUploadProgress(0);
    try {
      await documentService.upload(file, setUploadProgress);
      toast.success(t('documents.uploadComplete'), t('documents.uploadedSuccess', { name: file.name }));
      await loadDocuments();
    } catch (err: any) {
      toast.error(t('documents.uploadFailed'), localizeError(err?.response?.data?.message));
    } finally {
      setIsUploading(false);
      setUploadProgress(0);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: ACCEPTED_TYPES,
    maxSize: MAX_FILE_SIZE,
    multiple: false,
    disabled: isUploading,
  });

  const handleDownload = async (doc: Document) => {
    setBusyId(doc.id);
    try {
      await documentService.download(doc.id, doc.originalName);
    } catch (err: any) {
      toast.error(t('documents.downloadFailed'), localizeError(err?.response?.data?.message));
    } finally {
      setBusyId(null);
    }
  };

  const handleDelete = async (id: number) => {
    setBusyId(id);
    try {
      await documentService.delete(id);
      setDeletingId(null);
      toast.success(t('documents.documentDeleted'));
      await loadDocuments();
    } catch (err: any) {
      toast.error(t('documents.deleteFailed'), localizeError(err?.response?.data?.message));
    } finally {
      setBusyId(null);
    }
  };

  const toggleExpanded = async (id: number) => {
    const isNowExpanded = !expandedIds.has(id);
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

    if (isNowExpanded) {
      setIsLoadingVerifications((prev) => ({ ...prev, [id]: true }));
      try {
        const history = await signatureService.getVerificationHistory(id);
        setVerifications((prev) => ({ ...prev, [id]: history }));
      } catch (err) {
        console.error("Failed to load verification history", err);
      } finally {
        setIsLoadingVerifications((prev) => ({ ...prev, [id]: false }));
      }
    }
  };

  const handleSigned = () => {
    if (!signingDoc) return;
    const id = signingDoc.id;
    toast.success(t('documents.documentSignedSuccess'));
    setSignatureRefresh((prev) => ({ ...prev, [id]: (prev[id] ?? 0) + 1 }));
    setExpandedIds((prev) => new Set(prev).add(id));
    setSigningDoc(null);
    loadDocuments();
  };

  const hasSignedPdf = documents.some(
    (d) => d.mimeType === 'application/pdf' && !!d.currentPadesPdfPath,
  );
  const dismissBanner = () => {
    try {
      localStorage.setItem(TRUST_BANNER_KEY, '1');
    } catch {
      // ignore
    }
    setBannerDismissed(true);
  };

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <div className="mb-6">
        <h1 className="text-3xl font-bold tracking-tight">{t('documents.title')}</h1>
        <p className="text-muted-foreground">
          {t('documents.subtitle')}
        </p>
      </div>

      {hasSignedPdf && !bannerDismissed && (
        <Card className="mb-4 border-amber-300 bg-amber-50/50">
          <CardContent className="flex items-start gap-3 py-3">
            <Info className="h-5 w-5 text-amber-600 mt-0.5 shrink-0" />
            <div className="flex-1 text-sm">
              <p className="font-medium text-amber-900">{t('pades.untrustedBannerTitle')}</p>
              <p className="text-amber-800 mt-1">{t('pades.untrustedBannerBody')}</p>
              <Link
                to="/trust-setup"
                className="mt-2 inline-flex text-sm font-medium text-amber-900 underline"
              >
                {t('pades.openTrustSetup')}
              </Link>
            </div>
            <button
              onClick={dismissBanner}
              className="text-amber-700 hover:text-amber-900"
              aria-label={t('documents.close')}
            >
              <X className="h-4 w-4" />
            </button>
          </CardContent>
        </Card>
      )}

      <Card className="mb-6">
        <CardHeader>
          <div className="flex items-center gap-2">
            <UploadCloud className="h-5 w-5 text-primary" />
            <CardTitle>{t('documents.uploadTitle')}</CardTitle>
          </div>
          <CardDescription>
            {t('documents.uploadConstraints')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div
            {...getRootProps()}
            className={cn(
              'flex flex-col items-center justify-center rounded-lg border-2 border-dashed p-10 text-center transition-colors cursor-pointer',
              isDragActive ? 'border-primary bg-primary/5' : 'border-muted-foreground/30 hover:border-primary/60',
              isUploading && 'pointer-events-none opacity-60',
            )}
          >
            <input {...getInputProps()} />
            <UploadCloud className="mb-3 h-10 w-10 text-muted-foreground" />
            {isUploading ? (
              <>
                <p className="font-medium">{t('documents.uploading', { progress: uploadProgress })}</p>
                <div className="mt-3 h-2 w-full max-w-md overflow-hidden rounded-full bg-muted">
                  <div
                    className="h-full bg-primary transition-all"
                    style={{ width: `${uploadProgress}%` }}
                  />
                </div>
              </>
            ) : isDragActive ? (
              <p className="font-medium">{t('documents.dropHere')}</p>
            ) : (
              <>
                <p className="font-medium">{t('documents.uploadHint')}</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {t('documents.uploadHintMobile')}
                </p>
              </>
            )}
          </div>
        </CardContent>
      </Card>

      {isLoading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : documents.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16 text-center">
            <FileText className="mb-4 h-12 w-12 text-muted-foreground" />
            <h3 className="text-lg font-semibold">{t('documents.noDocsTitle')}</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              {t('documents.noDocsHint')}
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {documents.map((doc) => {
            const isExpanded = expandedIds.has(doc.id);
            return (
              <Card key={doc.id}>
                <CardContent className="py-4">
                  <div className="flex items-center justify-between gap-4">
                    <button
                      onClick={() => toggleExpanded(doc.id)}
                      className="flex items-center gap-3 min-w-0 flex-1 text-left"
                    >
                      {isExpanded ? (
                        <ChevronDown className="h-4 w-4 text-muted-foreground shrink-0" />
                      ) : (
                        <ChevronRight className="h-4 w-4 text-muted-foreground shrink-0" />
                      )}
                      <div className="rounded-md bg-primary/10 p-2 shrink-0">
                        <FileText className="h-5 w-5 text-primary" />
                      </div>
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="font-medium truncate">{doc.originalName}</p>
                          {doc.status === 'UPLOADED' && (
                            <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-800">
                              {t('documents.statusUploaded')}
                            </span>
                          )}
                          {doc.status === 'SIGNED' && (
                            <span className="inline-flex items-center rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-800">
                              {t('documents.statusSigned')}
                            </span>
                          )}
                          {doc.status === 'VERIFIED' && (
                            <span className="inline-flex items-center rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800">
                              {t('documents.statusVerified')}
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-muted-foreground">
                          {humanReadableSize(doc.size)} · {format(new Date(doc.createdAt), 'PPp', { locale: getDateLocale() })}
                        </p>
                        <p className="mt-0.5 truncate font-mono text-xs text-muted-foreground/70">
                          SHA-512: {doc.sha512Hash.substring(0, 32)}…
                        </p>
                      </div>
                    </button>
                    <div className="flex items-center gap-1 shrink-0">
                      <Button
                        variant="default"
                        size="sm"
                        onClick={() => setSigningDoc(doc)}
                        disabled={busyId === doc.id}
                      >
                        <FileSignature className="mr-1 h-4 w-4" />
                        {t('documents.sign')}
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDownload(doc)}
                        disabled={busyId === doc.id}
                      >
                        <Download className="mr-1 h-4 w-4" />
                        {t('documents.download')}
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-destructive hover:text-destructive"
                        onClick={() => setDeletingId(doc.id)}
                        disabled={busyId === doc.id}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                  {isExpanded && (
                    <div className="mt-4 border-t pt-4 space-y-4">
                      <div>
                        <p className="mb-2 text-sm font-medium">{t('documents.signatureHistory')}</p>
                        <DocumentSignatureList
                          documentId={doc.id}
                          documentName={doc.originalName}
                          refreshKey={signatureRefresh[doc.id] ?? 0}
                        />
                      </div>

                      <div className="border-t pt-4">
                        <p className="mb-2 text-sm font-medium">{t('verify.history')}</p>
                        {isLoadingVerifications[doc.id] ? (
                          <div className="flex justify-center py-4">
                            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                          </div>
                        ) : !verifications[doc.id] || verifications[doc.id].length === 0 ? (
                          <p className="text-sm text-muted-foreground">{t('verify.noHistory')}</p>
                        ) : (
                          <div className="space-y-2">
                            {verifications[doc.id].map((ver) => (
                              <div key={ver.id} className="rounded-md border p-3 text-sm">
                                <div className="flex items-center justify-between gap-2">
                                  <span className={cn(
                                    "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
                                    ver.status === 'VALID' && "bg-emerald-100 text-emerald-800",
                                    ver.status === 'INVALID' && "bg-rose-100 text-rose-800",
                                    ver.status === 'TAMPERED' && "bg-amber-100 text-amber-800",
                                  )}>
                                    {ver.status}
                                  </span>
                                  <span className="text-xs text-muted-foreground">
                                    {format(new Date(ver.verifiedAt), 'PPp', { locale: getDateLocale() })}
                                  </span>
                                </div>
                                {ver.message && (
                                  <p className="mt-1.5 text-xs text-muted-foreground">{ver.message}</p>
                                )}
                                <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-muted-foreground/80">
                                  {ver.signerName && (
                                    <span>
                                      {t('verify.signer')}: <strong>{ver.signerName}</strong>
                                    </span>
                                  )}
                                  {ver.verifierEmail && (
                                    <>
                                      <span>·</span>
                                      <span>
                                        {t('verify.verifiedBy')}: <strong>{ver.verifierEmail}</strong>
                                      </span>
                                    </>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {deletingId !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Card className="w-full max-w-md">
            <CardHeader>
              <div className="flex items-center gap-2">
                <ShieldAlert className="h-5 w-5 text-destructive" />
                <CardTitle>{t('documents.deleteConfirmTitle')}</CardTitle>
              </div>
              <CardDescription>
                {t('documents.deleteConfirmDesc')}
              </CardDescription>
            </CardHeader>
            <CardContent className="flex justify-end gap-2">
              <Button
                variant="outline"
                onClick={() => setDeletingId(null)}
                disabled={busyId === deletingId}
              >
                {t('documents.cancel')}
              </Button>
              <Button
                variant="destructive"
                onClick={() => handleDelete(deletingId)}
                disabled={busyId === deletingId}
              >
                {busyId === deletingId ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    {t('documents.deleting')}
                  </>
                ) : (
                  <>
                    <Trash2 className="mr-2 h-4 w-4" />
                    {t('documents.delete')}
                  </>
                )}
              </Button>
            </CardContent>
          </Card>
        </div>
      )}

      {signingDoc && (
        <SignDocumentModal
          documentId={signingDoc.id}
          documentName={signingDoc.originalName}
          documentMimeType={signingDoc.mimeType}
          onClose={() => setSigningDoc(null)}
          onSigned={handleSigned}
        />
      )}
    </div>
  );
}
