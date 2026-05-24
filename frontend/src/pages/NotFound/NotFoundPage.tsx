import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { FileQuestion, Home } from 'lucide-react';
import { Button } from '../../components/ui/button';

export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100 p-4">
      <div className="max-w-md text-center">
        <div className="mx-auto mb-6 inline-flex items-center justify-center rounded-full bg-primary/10 p-4">
          <FileQuestion className="h-10 w-10 text-primary" />
        </div>
        <h1 className="text-5xl font-bold tracking-tight">404</h1>
        <p className="mt-2 text-lg font-medium">{t('notFound.title')}</p>
        <p className="mt-1 text-sm text-muted-foreground">
          {t('notFound.hint')}
        </p>
        <Button asChild className="mt-6">
          <Link to="/">
            <Home className="mr-2 h-4 w-4" />
            {t('notFound.backHome')}
          </Link>
        </Button>
      </div>
    </div>
  );
}
