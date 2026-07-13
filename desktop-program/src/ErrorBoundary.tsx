import { Component, type ErrorInfo, type ReactNode } from 'react';

type ErrorBoundaryProps = {
  children: ReactNode;
  onSignOut: () => Promise<void>;
};

type ErrorBoundaryState = {
  failed: boolean;
  signingOut: boolean;
};

export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { failed: false, signingOut: false };

  static getDerivedStateFromError(): Partial<ErrorBoundaryState> {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Error inesperado en la interfaz del programa:', error, info.componentStack);
  }

  private signOut = async () => {
    if (this.state.signingOut) return;
    this.setState({ signingOut: true });
    await this.props.onSignOut();
  };

  render() {
    if (!this.state.failed) return this.props.children;

    return (
      <main className="startup-shell" role="alert">
        <section className="startup-card">
          <p className="eyebrow">Recuperación segura</p>
          <h1>El programa encontró un error inesperado</h1>
          <p>La interfaz se detuvo para evitar una pantalla vacía. Puedes recargar o cerrar la sesión actual.</p>
          <div className="startup-actions">
            <button type="button" onClick={() => window.location.reload()}>Recargar</button>
            <button type="button" className="secondary" disabled={this.state.signingOut} onClick={() => { void this.signOut(); }}>
              {this.state.signingOut ? 'Cerrando sesión...' : 'Cerrar sesión'}
            </button>
          </div>
        </section>
      </main>
    );
  }
}
