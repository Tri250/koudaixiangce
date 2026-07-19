import { Component, type ErrorInfo, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

function ErrorFallback({ error, onReload }: { error: Error | null; onReload: () => void }) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-col items-center justify-center h-screen bg-bg-primary text-text-primary p-8">
      <div className="flex flex-col items-center gap-4 max-w-md text-center">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="w-12 h-12 text-text-secondary"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.5}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 9v3.75m-9.303 3.618c.396.813.952 1.536 1.627 2.12M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z"
          />
        </svg>
        <h2 className="text-xl font-semibold">{t('errorBoundary.title')}</h2>
        <p className="text-sm text-text-secondary">{t('errorBoundary.message')}</p>
        {error && (
          <pre className="mt-2 p-3 rounded-md bg-surface text-xs text-text-secondary overflow-auto max-w-full text-left border border-border-color">
            {error.message}
          </pre>
        )}
        <button
          onClick={onReload}
          className="mt-2 px-4 py-2 rounded-md bg-surface hover:bg-surface-hover text-text-primary text-sm font-medium border border-border-color transition-colors"
        >
          {t('errorBoundary.reload')}
        </button>
      </div>
    </div>
  );
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('[ErrorBoundary] Uncaught rendering error:', error, errorInfo);
  }

  handleReload = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      return <ErrorFallback error={this.state.error} onReload={this.handleReload} />;
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
