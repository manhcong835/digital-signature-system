import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useDropzone } from 'react-dropzone';
import { useTranslation } from 'react-i18next';
import {
  ShieldCheck,
  ShieldAlert,
  ShieldX,
  UploadCloud,
  Loader2,
  FileText,
  CheckCircle2,
  XCircle,
  Copy,
  Check,
  FileSignature,
} from 'lucide-react';
import { Button } from '../../components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '../../components/ui/card';
import { Label } from '../../components/ui/label';
import LanguageSwitcher from '../../components/ui/LanguageSwitcher';
import { cn } from '../../lib/utils';
import { useAuthStore } from '../../store/authStore';
import { documentService } from '../../services/documentService';
import { signatureService } from '../../services/signatureService';
import { verifyService } from '../../services/verifyService';
import type {
  Document,
  PadesVerifyResponse,
  PadesVerifySignature,
  Signature,
  VerifyResponse,
} from '../../types';
import { format } from 'date-fns';
import { getDateLocale } from '../../i18n/dateLocale';
import { localizeError } from '../../utils/errorMapper';

type TabKey = 'stored' | 'pades';

function StatusBadge({ result }: { result: VerifyResponse }) {
  const { t } = useTranslation();
  const valid = result.valid;
  const tampered = result.status === 'TAMPERED';
  const Icon = valid ? ShieldCheck : tampered ? ShieldAlert : ShieldX;
  const classes = valid
    ? 'bg-green-100 text-green-800 border-green-300'
    : tampered
      ? 'bg-amber-100 text-amber-800 border-amber-300'
      : 'bg-red-100 text-red-800 border-red-300';
  return (
    <div className={cn('inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-medium', classes)}>
      <Icon className="h-3.5 w-3.5" />
      {result.status || (valid ? t('verify.valid') : t('verify.invalid'))}
    </div>
  );
}

function HashLine({ label, hash }: { label: string; hash?: string }) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  if (!hash) return null;
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(hash);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* noop */
    }
  };
  return (
    <div className="flex items-start justify-between gap-2 text-xs">
      <span className="text-muted-foreground shrink-0">{label}</span>
      <div className="flex items-start gap-1 min-w-0">
        <span className="truncate font-mono text-muted-foreground/80" title={hash}>
          {hash.substring(0, 32)}…
        </span>
        <button onClick={copy} className="text-muted-foreground hover:text-foreground" aria-label={t('verify.copy')}>
          {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
        </button>
      </div>
    </div>
  );
}

function ResultCard({ result, title }: { result: VerifyResponse; title?: string }) {
  const { t } = useTranslation();
  return (
    <Card>
      <CardContent className="py-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="font-medium truncate">{title || result.documentName || t('verify.resultTitle')}</p>
            {result.message && (
              <p className="mt-0.5 text-sm text-muted-foreground">{result.message}</p>
            )}
          </div>
          <StatusBadge result={result} />
        </div>
        <div className="mt-3 grid gap-1 text-xs">
          {result.algorithm && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">{t('verify.algorithm')}</span>
              <span className="font-mono">{result.algorithm}</span>
            </div>
          )}
          {result.signerName && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">{t('verify.signer')}</span>
              <span>{result.signerName} ({result.signerEmail})</span>
            </div>
          )}
          {result.keyName && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">{t('verify.key')}</span>
              <span>{result.keyName}</span>
            </div>
          )}
          {result.signedAt && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">{t('verify.signedAt')}</span>
              <span>{format(new Date(result.signedAt), 'PPp', { locale: getDateLocale() })}</span>
            </div>
          )}
          {result.verifiedAt && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">{t('verify.verifiedAt')}</span>
              <span>{format(new Date(result.verifiedAt), 'PPp', { locale: getDateLocale() })}</span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-muted-foreground">{t('verify.hashMatches')}</span>
            <span className={result.hashMatches ? 'text-green-700' : 'text-red-700'}>
              {result.hashMatches === undefined
                ? '-'
                : result.hashMatches
                  ? t('verify.yes')
                  : t('verify.noTampered')}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">{t('verify.signatureValid')}</span>
            <span className={result.signatureValid ? 'text-green-700' : 'text-red-700'}>
              {result.signatureValid === undefined
                ? '-'
                : result.signatureValid
                  ? t('verify.yes')
                  : t('verify.no')}
            </span>
          </div>
          <HashLine label={t('verify.expectedHash')} hash={result.expectedHash} />
          <HashLine label={t('verify.computedHash')} hash={result.computedHash} />
        </div>
      </CardContent>
    </Card>
  );
}

function StoredTab() {
  const { t } = useTranslation();
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loadingDocs, setLoadingDocs] = useState(true);
  const [selectedDocId, setSelectedDocId] = useState<number | null>(null);
  const [signatures, setSignatures] = useState<Signature[]>([]);
  const [selectedSigId, setSelectedSigId] = useState<number | 'all'>('all');
  const [loadingSigs, setLoadingSigs] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [result, setResult] = useState<VerifyResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    documentService.list()
      .then((docs) => {
        setDocuments(docs);
        if (docs.length > 0) setSelectedDocId(docs[0].id);
      })
      .catch((err) => setError(localizeError(err?.response?.data?.message, 'verify.failedLoadDocs')))
      .finally(() => setLoadingDocs(false));
  }, []);

  useEffect(() => {
    if (selectedDocId == null) {
      setSignatures([]);
      return;
    }
    setLoadingSigs(true);
    setSelectedSigId('all');
    signatureService.listForDocument(selectedDocId)
      .then(setSignatures)
      .catch(() => setSignatures([]))
      .finally(() => setLoadingSigs(false));
  }, [selectedDocId]);

  const handleVerify = async () => {
    if (selectedDocId == null) return;
    setVerifying(true);
    setError(null);
    setResult(null);
    try {
      const sigId = selectedSigId === 'all' ? undefined : selectedSigId;
      const res = await verifyService.verifyDocument(selectedDocId, sigId);
      setResult(res);
    } catch (err: any) {
      setError(localizeError(err?.response?.data?.message, 'verify.verificationFailed'));
    } finally {
      setVerifying(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('verify.storedTitle')}</CardTitle>
        <CardDescription>
          {t('verify.storedDesc')}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {loadingDocs ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            {t('verify.loadingDocs')}
          </div>
        ) : documents.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {t('verify.noDocs')}
          </p>
        ) : (
          <>
            <div className="grid gap-2">
              <Label htmlFor="verify-doc">{t('verify.document')}</Label>
              <select
                id="verify-doc"
                className="w-full rounded-md border bg-background px-3 py-2 text-sm"
                value={selectedDocId ?? ''}
                onChange={(e) => setSelectedDocId(Number(e.target.value))}
                disabled={verifying}
              >
                {documents.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.originalName}
                  </option>
                ))}
              </select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="verify-sig">{t('verify.signature')}</Label>
              {loadingSigs ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  {t('verify.loadingSigs')}
                </div>
              ) : signatures.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  {t('verify.noSigs')}
                </p>
              ) : (
                <select
                  id="verify-sig"
                  className="w-full rounded-md border bg-background px-3 py-2 text-sm"
                  value={selectedSigId}
                  onChange={(e) =>
                    setSelectedSigId(e.target.value === 'all' ? 'all' : Number(e.target.value))
                  }
                  disabled={verifying}
                >
                  <option value="all">{t('verify.allSignatures', { count: signatures.length })}</option>
                  {signatures.map((s, index) => (
                    <option key={s.id} value={s.id}>
                      #{index + 1} · {s.keyName} · {format(new Date(s.signedAt), 'PPp', { locale: getDateLocale() })}
                    </option>
                  ))}
                </select>
              )}
            </div>

            <Button
              onClick={handleVerify}
              disabled={verifying || signatures.length === 0}
            >
              {verifying ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t('verify.verifying')}
                </>
              ) : (
                <>
                  <ShieldCheck className="mr-2 h-4 w-4" />
                  {t('verify.verify')}
                </>
              )}
            </Button>
          </>
        )}

        {error && (
          <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
            {error}
          </div>
        )}

        {result && (
          <div className="space-y-3 pt-2">
            <ResultCard result={result} title={result.documentName} />
            {result.signatures && result.signatures.length > 0 && (
              <div className="space-y-2 pl-3 border-l-2 border-muted">
                <p className="text-sm font-medium">{t('verify.perSigResults')}</p>
                {result.signatures.map((s, idx) => (
                  <ResultCard
                    key={idx}
                    result={s}
                    title={t('verify.signatureLabel', { index: idx + 1, key: s.keyName ?? '' })}
                  />
                ))}
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function PadesSignatureCard({ sig, index }: { sig: PadesVerifySignature; index: number }) {
  const { t } = useTranslation();
  const ok =
    sig.cryptoValid &&
    sig.certChainValid &&
    (sig.byteRangeCoversFile || sig.incrementalUpdateValid);
  const Icon = ok ? ShieldCheck : ShieldX;
  const badgeClasses = ok
    ? 'bg-green-100 text-green-800 border-green-300'
    : 'bg-red-100 text-red-800 border-red-300';
  return (
    <Card>
      <CardContent className="py-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="font-medium">
              #{index + 1} · {sig.signerCn ?? '—'}
            </p>
            {sig.issuerCn && (
              <p className="text-xs text-muted-foreground">Issuer: {sig.issuerCn}</p>
            )}
          </div>
          <div className={cn('inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-medium', badgeClasses)}>
            <Icon className="h-3.5 w-3.5" />
            {ok ? t('verify.padesValid') : t('verify.padesInvalid')}
          </div>
        </div>
        <div className="mt-3 grid gap-1 text-xs">
          {sig.signedAt && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">{t('verify.padesSignedAt')}</span>
              <span>{format(new Date(sig.signedAt), 'PPp', { locale: getDateLocale() })}</span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-muted-foreground">{t('verify.signatureValid')}</span>
            <span className={sig.cryptoValid ? 'text-green-700' : 'text-red-700'}>
              {sig.cryptoValid ? t('verify.yes') : t('verify.no')}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">{t('verify.padesCoversFile')}</span>
            <span className={sig.byteRangeCoversFile ? 'text-green-700' : 'text-amber-700'}>
              {sig.byteRangeCoversFile ? t('verify.yes') : t('verify.padesNotFinalRev')}
            </span>
          </div>
          {!sig.byteRangeCoversFile && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">{t('verify.padesAppendedLabel')}</span>
              <span className={sig.incrementalUpdateValid ? 'text-green-700' : 'text-red-700'}>
                {sig.incrementalUpdateValid
                  ? t('verify.padesAppendedValid')
                  : t('verify.padesAppendedInvalid')}
              </span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-muted-foreground">Cert chain</span>
            <span className={sig.certChainValid ? 'text-green-700' : 'text-red-700'}>
              {sig.certChainValid ? t('verify.yes') : t('verify.no')}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Trusted by local CA</span>
            <span className={sig.certTrustedByLocalCa ? 'text-green-700' : 'text-amber-700'}>
              {sig.certTrustedByLocalCa ? t('verify.yes') : t('verify.no')}
            </span>
          </div>
          {sig.error && (
            <p className="text-xs text-red-700 mt-1">{sig.error}</p>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function PadesTab() {
  const { t } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [result, setResult] = useState<PadesVerifyResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const onDrop = (acceptedFiles: File[]) => {
    if (acceptedFiles.length > 0) {
      setFile(acceptedFiles[0]);
      setResult(null);
      setError(null);
    }
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: false,
    accept: { 'application/pdf': ['.pdf'] },
    disabled: verifying,
  });

  const handleVerify = async () => {
    if (!file) return;
    setVerifying(true);
    setError(null);
    setResult(null);
    try {
      const res = await signatureService.verifyPadesPdf(file);
      setResult(res);
    } catch (err: any) {
      setError(localizeError(err?.response?.data?.message, 'verify.verificationFailed'));
    } finally {
      setVerifying(false);
    }
  };

  const summaryClasses = result?.allSignaturesValid
    ? 'border-green-300 bg-green-50 text-green-800'
    : 'border-red-300 bg-red-50 text-red-800';

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('verify.padesTitle')}</CardTitle>
        <CardDescription>{t('verify.padesDesc')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <Label className="mb-2 block">{t('verify.file')}</Label>
          <div
            {...getRootProps()}
            className={cn(
              'flex flex-col items-center justify-center rounded-lg border-2 border-dashed p-6 text-center transition-colors cursor-pointer',
              isDragActive ? 'border-primary bg-primary/5' : 'border-muted-foreground/30 hover:border-primary/60',
              verifying && 'pointer-events-none opacity-60',
            )}
          >
            <input {...getInputProps()} />
            {file ? (
              <div className="flex items-center gap-2">
                <FileText className="h-5 w-5 text-primary" />
                <span className="font-medium">{file.name}</span>
              </div>
            ) : (
              <>
                <UploadCloud className="mb-2 h-8 w-8 text-muted-foreground" />
                <p className="text-sm">
                  {isDragActive ? t('verify.dropHere') : t('verify.dragOrClick')}
                </p>
              </>
            )}
          </div>
        </div>

        <Button onClick={handleVerify} disabled={verifying || !file}>
          {verifying ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              {t('verify.verifying')}
            </>
          ) : (
            <>
              <ShieldCheck className="mr-2 h-4 w-4" />
              {t('verify.verify')}
            </>
          )}
        </Button>

        {error && (
          <div className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
            <XCircle className="h-4 w-4" />
            <span>{error}</span>
          </div>
        )}

        {result && (
          <div className="space-y-3 pt-2">
            {result.signatureCount === 0 ? (
              <div className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">
                {t('verify.padesNoSignatures')}
              </div>
            ) : (
              <div className={cn('rounded-md border p-3 text-sm', summaryClasses)}>
                {result.allSignaturesValid
                  ? t('verify.padesAllValid')
                  : t('verify.padesSomeInvalid')}
              </div>
            )}
            {result.signatures.map((s, idx) => (
              <PadesSignatureCard key={idx} sig={s} index={idx} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function PageHeader() {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuthStore();

  return (
    <header className="border-b bg-white">
      <div className="container mx-auto flex items-center justify-between px-4 py-3">
        <Link to={isAuthenticated ? '/dashboard' : '/login'} className="flex items-center gap-2">
          <FileSignature className="h-6 w-6 text-primary" />
          <span className="font-bold">{t('sidebar.appName')}</span>
        </Link>
        <div className="flex items-center gap-3">
          <LanguageSwitcher />
          {isAuthenticated ? (
            <Link to="/dashboard" className="text-sm text-primary hover:underline">
              {t('verify.backToApp')}
            </Link>
          ) : (
            <>
              <Link to="/login" className="text-sm text-primary hover:underline">
                {t('verify.loginToApp')}
              </Link>
              <Link to="/register" className="text-sm text-muted-foreground hover:underline">
                {t('verify.registerLink')}
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}

export default function VerifyPage() {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuthStore();
  // Guests default to the PAdES tab — that's the workflow they actually want.
  // Authenticated users default to "stored" (their own docs).
  const [tab, setTab] = useState<TabKey>(isAuthenticated ? 'stored' : 'pades');

  return (
    <div className="min-h-screen bg-slate-50">
      <PageHeader />
      <div className="container mx-auto max-w-3xl px-4 py-8">
        <div className="mb-6">
          <h1 className="text-3xl font-bold tracking-tight">
            {isAuthenticated ? t('verify.title') : t('verify.guestHeaderTitle')}
          </h1>
          <p className="text-muted-foreground">
            {isAuthenticated ? t('verify.subtitle') : t('verify.guestHeaderSubtitle')}
          </p>
        </div>

        <div className="mb-4 inline-flex flex-wrap rounded-md border bg-muted/30 p-1">
          {isAuthenticated && (
            <button
              onClick={() => setTab('stored')}
              className={cn(
                'rounded-sm px-4 py-1.5 text-sm font-medium transition-colors',
                tab === 'stored' ? 'bg-background shadow' : 'text-muted-foreground',
              )}
            >
              <CheckCircle2 className="mr-1 inline h-4 w-4" />
              {t('verify.tabStored')}
            </button>
          )}
          <button
            onClick={() => setTab('pades')}
            className={cn(
              'rounded-sm px-4 py-1.5 text-sm font-medium transition-colors',
              tab === 'pades' ? 'bg-background shadow' : 'text-muted-foreground',
            )}
          >
            <FileText className="mr-1 inline h-4 w-4" />
            {t('verify.tabPades')}
          </button>
        </div>

        {tab === 'stored' && isAuthenticated && <StoredTab />}
        {tab === 'pades' && <PadesTab />}
      </div>
    </div>
  );
}
