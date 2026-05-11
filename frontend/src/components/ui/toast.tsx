import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';
import { useToastStore, type ToastVariant } from '../../store/toastStore';
import { cn } from '../../lib/utils';

const variantStyles: Record<
  ToastVariant,
  { container: string; icon: React.ComponentType<{ className?: string }> }
> = {
  success: {
    container: 'bg-green-50 border-green-300 text-green-900',
    icon: CheckCircle2,
  },
  error: {
    container: 'bg-red-50 border-red-300 text-red-900',
    icon: AlertCircle,
  },
  info: {
    container: 'bg-slate-50 border-slate-300 text-slate-900',
    icon: Info,
  },
};

export default function ToastViewport() {
  const toasts = useToastStore((s) => s.toasts);
  const dismiss = useToastStore((s) => s.dismiss);

  if (toasts.length === 0) return null;

  return (
    <div
      className="fixed top-4 right-4 z-[100] flex flex-col gap-2 w-full max-w-sm"
      role="region"
      aria-live="polite"
      aria-label="Notifications"
    >
      {toasts.map((t) => {
        const { container, icon: Icon } = variantStyles[t.variant];
        return (
          <div
            key={t.id}
            className={cn(
              'flex items-start gap-3 rounded-md border p-3 shadow-sm pointer-events-auto',
              container,
            )}
            role={t.variant === 'error' ? 'alert' : 'status'}
          >
            <Icon className="h-5 w-5 shrink-0 mt-0.5" />
            <div className="flex-1 min-w-0">
              {t.title && <p className="text-sm font-medium">{t.title}</p>}
              {t.description && (
                <p className="text-xs text-muted-foreground mt-0.5 break-words">
                  {t.description}
                </p>
              )}
            </div>
            <button
              onClick={() => dismiss(t.id)}
              className="opacity-60 hover:opacity-100 shrink-0"
              aria-label="Dismiss"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
