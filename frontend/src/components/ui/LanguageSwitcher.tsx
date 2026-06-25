import { useTranslation } from 'react-i18next';
import { Languages } from 'lucide-react';
import { cn } from '../../lib/utils';

interface Props {
  className?: string;
}

export default function LanguageSwitcher({ className }: Props) {
  const { i18n, t } = useTranslation();
  const current = i18n.language?.startsWith('vi') ? 'vi' : 'en';

  const setLang = (lng: 'en' | 'vi') => {
    if (lng !== current) i18n.changeLanguage(lng);
  };

  return (
    <div
      className={cn('flex items-center gap-1 text-xs', className)}
      role="group"
      aria-label={t('common.language')}
    >
      <Languages className="h-3.5 w-3.5 text-muted-foreground" />
      <button
        type="button"
        onClick={() => setLang('en')}
        className={cn(
          'rounded px-1.5 py-0.5 font-medium transition-colors',
          current === 'en'
            ? 'bg-primary text-primary-foreground'
            : 'text-muted-foreground hover:text-foreground',
        )}
      >
        EN
      </button>
      <span className="text-muted-foreground/60">|</span>
      <button
        type="button"
        onClick={() => setLang('vi')}
        className={cn(
          'rounded px-1.5 py-0.5 font-medium transition-colors',
          current === 'vi'
            ? 'bg-primary text-primary-foreground'
            : 'text-muted-foreground hover:text-foreground',
        )}
      >
        VI
      </button>
    </div>
  );
}
