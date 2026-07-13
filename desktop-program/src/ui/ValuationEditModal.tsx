import { X } from 'lucide-react';
import type { ValuationSaveState } from '../valuation/models';

function formatCurrency(value: number) {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(value);
}
function formatNumber(value: number) {
  return new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 }).format(value);
}

export default function ValuationEditModal({
  product,
  code,
  unit,
  moduleName,
  quantity,
  value,
  saveState,
  online,
  onChange,
  onSave,
  onClose,
}: {
  product: string;
  code: string;
  unit: string;
  moduleName: string;
  quantity: number;
  value: string;
  saveState?: ValuationSaveState;
  online: boolean;
  onChange: (value: string) => void;
  onSave: () => void;
  onClose: () => void;
}) {
  const parsedValue = Number(value.replace(',', '.'));
  const previewUnitValue = Number.isFinite(parsedValue) && parsedValue >= 0 ? parsedValue : 0;
  const saving = saveState === 'saving';

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section
        className="entries-modal valuation-modal"
        role="dialog"
        aria-modal="true"
        aria-label={`Editar valor unitario de ${product}`}
        onClick={(event) => event.stopPropagation()}
      >
        <header className="evidence-header">
          <div>
            <p className="eyebrow">{moduleName} | Valoración unitaria</p>
            <h2>{product}</h2>
            <small>{code} | {formatNumber(quantity)} {unit}</small>
          </div>
          <button className="icon-button" type="button" title="Cerrar" disabled={saving} onClick={onClose}>
            <X size={18} />
          </button>
        </header>
        <form
          className="valuation-modal-body"
          onSubmit={(event) => {
            event.preventDefault();
            onSave();
          }}
        >
          <div className="valuation-modal-preview">
            <span>Valor total calculado</span>
            <strong>{formatCurrency(previewUnitValue * quantity)}</strong>
            {moduleName === 'TALLER' && <small>La cantidad incluye unidades disponibles y ocupadas.</small>}
          </div>
          <label className="valuation-modal-field">
            Valor unitario
            <span>
              <strong>$</strong>
              <input
                autoFocus
                type="number"
                min="0"
                step="1"
                inputMode="decimal"
                value={value}
                disabled={!online || saving}
                onChange={(event) => onChange(event.target.value)}
              />
            </span>
          </label>
          {saveState === 'error' && <p className="form-error">Revisa el valor unitario e intenta nuevamente.</p>}
          {!online && <p className="form-error">Conéctate a Firestore para guardar cambios.</p>}
          <footer className="valuation-modal-actions">
            <button type="button" disabled={saving} onClick={onClose}>Cancelar</button>
            <button className="tool-button" type="submit" disabled={!online || saving || !value.trim()}>
              {saving ? 'Guardando...' : 'Guardar valor'}
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}
