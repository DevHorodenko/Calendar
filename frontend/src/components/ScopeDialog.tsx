import type { EditScope } from '../types'

interface Props {
  action: 'save' | 'delete'
  onChoose: (scope: EditScope) => void
  onCancel: () => void
}

const OPTIONS: Record<Props['action'], Array<{ scope: EditScope; label: string; hint: string }>> = {
  save: [
    { scope: 'THIS', label: 'Somente este evento', hint: 'As outras repeticoes ficam como estao.' },
    { scope: 'THIS_AND_FUTURE', label: 'Este e os seguintes', hint: 'As repeticoes anteriores nao mudam.' },
    { scope: 'ALL', label: 'Todos os eventos da serie', hint: 'Aplica a mudanca do inicio ao fim.' },
  ],
  delete: [
    { scope: 'THIS', label: 'Somente este evento', hint: 'Apaga so esta repeticao.' },
    { scope: 'THIS_AND_FUTURE', label: 'Este e os seguintes', hint: 'Encerra a serie nesta data.' },
    { scope: 'ALL', label: 'Todos os eventos da serie', hint: 'Apaga a serie inteira.' },
  ],
}

/** Pergunta o alcance antes de mexer numa serie recorrente. */
export default function ScopeDialog({ action, onChoose, onCancel }: Props) {
  return (
    <div className="overlay" onClick={onCancel}>
      <div
        className="modal modal--sm"
        role="dialog"
        aria-modal="true"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal__header">
          <h2 className="modal__title">
            {action === 'delete' ? 'Apagar evento recorrente' : 'Salvar evento recorrente'}
          </h2>
        </div>

        <div className="modal__body">
          <p className="modal__hint">Este evento se repete. O que voce quer alterar?</p>
          <div className="scope-options">
            {OPTIONS[action].map((option) => (
              <button
                key={option.scope}
                type="button"
                className="scope-option"
                onClick={() => onChoose(option.scope)}
              >
                <strong>{option.label}</strong>
                <span>{option.hint}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="modal__footer">
          <div className="toolbar__spacer" />
          <button type="button" className="btn" onClick={onCancel}>
            Cancelar
          </button>
        </div>
      </div>
    </div>
  )
}
