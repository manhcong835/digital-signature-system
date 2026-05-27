import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  ShieldCheck,
  Key as KeyIcon,
  FileText,
  FileSignature,
  ChevronRight,
  ShieldAlert,
  ShieldX,
  Inbox,
} from 'lucide-react';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '../../components/ui/card';
import { Skeleton } from '../../components/ui/skeleton';
import { useAuthStore } from '../../store/authStore';
import { keyService } from '../../services/keyService';
import { documentService } from '../../services/documentService';
import { signatureService } from '../../services/signatureService';
import type { Document, KeyPair, Signature } from '../../types';
import { cn } from '../../lib/utils';
import { getDateLocale } from '../../i18n/dateLocale';
import { format } from 'date-fns';

function formatDateTime(value: string | Date): string {
  return format(new Date(value), 'PPp', { locale: getDateLocale() });
}

function StatCard({
  title,
  description,
  icon: Icon,
  value,
  loading,
  to,
}: {
  title: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  value: number | null;
  loading: boolean;
  to: string;
}) {
  return (
    <Link to={to} className="block group">
      <Card className="h-full transition-colors group-hover:border-primary/40">
        <CardHeader className="pb-2">
          <div className="flex items-start justify-between gap-2">
            <div>
              <CardTitle className="text-base">{title}</CardTitle>
              <CardDescription>{description}</CardDescription>
            </div>
            <div className="rounded-md bg-primary/10 p-2">
              <Icon className="h-5 w-5 text-primary" />
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {loading ? (
            <Skeleton className="h-9 w-16" />
          ) : (
            <p className="text-3xl font-bold">{value ?? '-'}</p>
          )}
        </CardContent>
      </Card>
    </Link>
  );
}

function statusStyles(status?: string) {
  switch (status) {
    case 'VALID':
      return 'bg-green-100 text-green-800 border-green-300';
    case 'TAMPERED':
      return 'bg-amber-100 text-amber-800 border-amber-300';
    case 'INVALID':
      return 'bg-red-100 text-red-800 border-red-300';
    default:
      return 'bg-slate-100 text-slate-700 border-slate-300';
  }
}

function statusIcon(status?: string) {
  switch (status) {
    case 'VALID':
      return ShieldCheck;
    case 'TAMPERED':
      return ShieldAlert;
    case 'INVALID':
      return ShieldX;
    default:
      return ShieldCheck;
  }
}

function VerificationBreakdown({ signatures, loading }: { signatures: Signature[]; loading: boolean }) {
  const { t } = useTranslation();
  if (loading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-3 w-full" />
        <Skeleton className="h-3 w-3/4" />
        <Skeleton className="h-3 w-1/2" />
      </div>
    );
  }
  if (signatures.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">{t('dashboard.noSignatures')}</p>
    );
  }
  const total = signatures.length;
  const valid = signatures.filter((s) => s.verifyStatus === 'VALID').length;
  const tampered = signatures.filter((s) => s.verifyStatus === 'TAMPERED').length;
  const invalid = signatures.filter((s) => s.verifyStatus === 'INVALID').length;
  const unknown = total - valid - tampered - invalid;

  const segments = [
    { key: 'VALID', count: valid, color: 'bg-green-500', label: t('dashboard.valid') },
    { key: 'TAMPERED', count: tampered, color: 'bg-amber-500', label: t('dashboard.tampered') },
    { key: 'INVALID', count: invalid, color: 'bg-red-500', label: t('dashboard.invalid') },
    { key: 'UNKNOWN', count: unknown, color: 'bg-slate-300', label: t('dashboard.notVerified') },
  ].filter((s) => s.count > 0);

  return (
    <div className="space-y-3">
      <div className="flex h-2 w-full overflow-hidden rounded-full bg-muted">
        {segments.map((s) => (
          <div
            key={s.key}
            className={s.color}
            style={{ width: `${(s.count / total) * 100}%` }}
            title={`${s.label}: ${s.count}`}
          />
        ))}
      </div>
      <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
        {segments.map((s) => (
          <div key={s.key} className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-1.5">
              <span className={cn('h-2 w-2 rounded-full', s.color)} />
              <span className="text-muted-foreground">{s.label}</span>
            </span>
            <span className="font-medium">{s.count}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function RecentDocuments({ documents, loading }: { documents: Document[]; loading: boolean }) {
  const { t } = useTranslation();
  if (loading) {
    return (
      <div className="space-y-3">
        {[1, 2, 3].map((i) => (
          <div key={i} className="flex items-center gap-3">
            <Skeleton className="h-8 w-8 rounded-md" />
            <div className="flex-1 space-y-1">
              <Skeleton className="h-3 w-3/4" />
              <Skeleton className="h-3 w-1/3" />
            </div>
          </div>
        ))}
      </div>
    );
  }
  if (documents.length === 0) {
    return (
      <div className="flex flex-col items-center py-6 text-center">
        <Inbox className="mb-2 h-8 w-8 text-muted-foreground" />
        <p className="text-sm text-muted-foreground">{t('dashboard.noDocuments')}</p>
      </div>
    );
  }
  return (
    <ul className="space-y-2">
      {documents.slice(0, 5).map((doc) => (
        <li key={doc.id} className="flex items-center gap-3">
          <div className="rounded-md bg-primary/10 p-2 shrink-0">
            <FileText className="h-4 w-4 text-primary" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <p className="truncate text-sm font-medium">{doc.originalName}</p>
              {doc.status === 'UPLOADED' && (
                <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-800">
                  {t('documents.statusUploaded')}
                </span>
              )}
              {doc.status === 'SIGNED' && (
                <span className="inline-flex items-center rounded-full bg-blue-100 px-2 py-0.5 text-[10px] font-medium text-blue-800">
                  {t('documents.statusSigned')}
                </span>
              )}
              {doc.status === 'VERIFIED' && (
                <span className="inline-flex items-center rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-medium text-emerald-800">
                  {t('documents.statusVerified')}
                </span>
              )}
            </div>
            <p className="text-xs text-muted-foreground">
              {formatDateTime(doc.createdAt)}
            </p>
          </div>
        </li>
      ))}
    </ul>
  );
}

function RecentSignatures({ signatures, loading }: { signatures: Signature[]; loading: boolean }) {
  const { t } = useTranslation();
  if (loading) {
    return (
      <div className="space-y-3">
        {[1, 2, 3].map((i) => (
          <div key={i} className="flex items-center gap-3">
            <Skeleton className="h-8 w-8 rounded-md" />
            <div className="flex-1 space-y-1">
              <Skeleton className="h-3 w-3/4" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          </div>
        ))}
      </div>
    );
  }
  if (signatures.length === 0) {
    return (
      <div className="flex flex-col items-center py-6 text-center">
        <FileSignature className="mb-2 h-8 w-8 text-muted-foreground" />
        <p className="text-sm text-muted-foreground">{t('dashboard.noSignatures')}</p>
      </div>
    );
  }
  const statusLabel = (s?: string) => {
    switch (s) {
      case 'VALID':
        return t('dashboard.valid');
      case 'TAMPERED':
        return t('dashboard.tampered');
      case 'INVALID':
        return t('dashboard.invalid');
      default:
        return t('dashboard.notVerified');
    }
  };
  return (
    <ul className="space-y-2">
      {signatures.slice(0, 5).map((sig) => {
        const Icon = statusIcon(sig.verifyStatus);
        return (
          <li key={sig.id} className="flex items-center gap-3">
            <div className="rounded-md bg-primary/10 p-2 shrink-0">
              <FileSignature className="h-4 w-4 text-primary" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">
                {sig.documentName ?? t('dashboard.documentNumber', { id: sig.documentId })}
              </p>
              <p className="text-xs text-muted-foreground">
                {sig.keyName ?? t('dashboard.unknownKey')} · {formatDateTime(sig.signedAt)}
              </p>
            </div>
            <span
              className={cn(
                'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium shrink-0',
                statusStyles(sig.verifyStatus),
              )}
            >
              <Icon className="h-3 w-3" />
              {statusLabel(sig.verifyStatus)}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

export default function DashboardPage() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const [keys, setKeys] = useState<KeyPair[]>([]);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [signatures, setSignatures] = useState<Signature[]>([]);
  const [loadingKeys, setLoadingKeys] = useState(true);
  const [loadingDocs, setLoadingDocs] = useState(true);
  const [loadingSigs, setLoadingSigs] = useState(true);

  useEffect(() => {
    keyService.list()
      .then(setKeys).catch(() => setKeys([]))
      .finally(() => setLoadingKeys(false));
    documentService.list()
      .then(setDocuments).catch(() => setDocuments([]))
      .finally(() => setLoadingDocs(false));
    signatureService.listForUser()
      .then(setSignatures).catch(() => setSignatures([]))
      .finally(() => setLoadingSigs(false));
  }, []);

  return (
    <div className="container mx-auto px-4 py-6 md:py-8">
      <div className="mb-6">
        <h1 className="text-2xl md:text-3xl font-bold tracking-tight">
          {t('dashboard.welcome', { name: user?.fullName })}
        </h1>
        <p className="text-muted-foreground">
          {t('dashboard.subtitle')}
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title={t('dashboard.keys')}
          description={t('dashboard.keysDesc')}
          icon={KeyIcon}
          value={keys.length}
          loading={loadingKeys}
          to="/keys"
        />
        <StatCard
          title={t('dashboard.documents')}
          description={t('dashboard.documentsDesc')}
          icon={FileText}
          value={documents.length}
          loading={loadingDocs}
          to="/documents"
        />
        <StatCard
          title={t('dashboard.signatures')}
          description={t('dashboard.signaturesDesc')}
          icon={FileSignature}
          value={signatures.length}
          loading={loadingSigs}
          to="/documents"
        />
        <Link to="/verify" className="block group">
          <Card className="h-full transition-colors group-hover:border-primary/40">
            <CardHeader className="pb-2">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <CardTitle className="text-base">{t('dashboard.verify')}</CardTitle>
                  <CardDescription>{t('dashboard.verifyDesc')}</CardDescription>
                </div>
                <div className="rounded-md bg-primary/10 p-2">
                  <ShieldCheck className="h-5 w-5 text-primary" />
                </div>
              </div>
            </CardHeader>
            <CardContent>
              <span className="inline-flex items-center gap-1 text-sm font-medium text-primary">
                {t('dashboard.openVerify')}
                <ChevronRight className="h-4 w-4" />
              </span>
            </CardContent>
          </Card>
        </Link>
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="text-base">{t('dashboard.signatureStatus')}</CardTitle>
            <CardDescription>{t('dashboard.signatureStatusDesc')}</CardDescription>
          </CardHeader>
          <CardContent>
            <VerificationBreakdown signatures={signatures} loading={loadingSigs} />
          </CardContent>
        </Card>

        <Card className="lg:col-span-1">
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-base">{t('dashboard.recentDocuments')}</CardTitle>
                <CardDescription>{t('dashboard.recentDocumentsDesc')}</CardDescription>
              </div>
              <Link
                to="/documents"
                className="text-xs font-medium text-primary hover:underline"
              >
                {t('dashboard.viewAll')}
              </Link>
            </div>
          </CardHeader>
          <CardContent>
            <RecentDocuments documents={documents} loading={loadingDocs} />
          </CardContent>
        </Card>

        <Card className="lg:col-span-1">
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-base">{t('dashboard.recentSignatures')}</CardTitle>
                <CardDescription>{t('dashboard.recentSignaturesDesc')}</CardDescription>
              </div>
              <Link
                to="/documents"
                className="text-xs font-medium text-primary hover:underline"
              >
                {t('dashboard.viewAll')}
              </Link>
            </div>
          </CardHeader>
          <CardContent>
            <RecentSignatures signatures={signatures} loading={loadingSigs} />
          </CardContent>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>{t('dashboard.accountInfo')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <div className="flex justify-between border-b pb-2">
            <span className="text-muted-foreground">{t('dashboard.fullName')}</span>
            <span className="font-medium">{user?.fullName}</span>
          </div>
          <div className="flex justify-between border-b pb-2">
            <span className="text-muted-foreground">{t('dashboard.email')}</span>
            <span className="font-medium">{user?.email}</span>
          </div>
          {user?.phone && (
            <div className="flex justify-between border-b pb-2">
              <span className="text-muted-foreground">{t('dashboard.phone')}</span>
              <span className="font-medium">{user.phone}</span>
            </div>
          )}
          {user?.organization && (
            <div className="flex justify-between border-b pb-2">
              <span className="text-muted-foreground">{t('dashboard.organization')}</span>
              <span className="font-medium">{user.organization}</span>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
