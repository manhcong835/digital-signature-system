import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Button } from '../ui/button';
import i18n from '../../i18n/i18n';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Uncaught error:', error, info);
  }

  handleReload = () => {
    window.location.href = '/';
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100 p-4">
          <div className="max-w-md text-center">
            <div className="mx-auto mb-6 inline-flex items-center justify-center rounded-full bg-destructive/10 p-4">
              <AlertTriangle className="h-10 w-10 text-destructive" />
            </div>
            <h1 className="text-2xl font-bold tracking-tight">{i18n.t('errors.boundaryTitle')}</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              {i18n.t('errors.boundaryHint')}
            </p>
            {this.state.error?.message && (
              <p className="mt-3 rounded-md border bg-muted px-3 py-2 text-xs font-mono text-muted-foreground">
                {this.state.error.message}
              </p>
            )}
            <Button className="mt-6" onClick={this.handleReload}>
              <RefreshCw className="mr-2 h-4 w-4" />
              {i18n.t('errors.reload')}
            </Button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
