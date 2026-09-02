import {
  LEAD_UNITS,
  MAX_REMINDERS,
  MESSAGE_PLACEHOLDERS,
  describeLead,
  leadPhrase,
  newReminder,
  toLead,
  toMinutes,
} from '../lib/reminders'
import type { LeadUnit } from '../lib/reminders'
import type { Reminder } from '../types'

interface Props {
  value: Reminder[]
  /** Series avisam que o lembrete vale para todas as ocorrencias, e nao so para esta. */
  recurring: boolean
  /** Sem nenhum canal pronto, os lembretes ficam gravados mas nao saem. */
  notificationsReady: boolean
  onChange: (reminders: Reminder[]) => void
  onOpenSettings: () => void
}

export default function ReminderEditor({
  value,
  recurring,
  notificationsReady,
  onChange,
  onOpenSettings,
}: Props) {
  function patch(index: number, changes: Partial<Reminder>) {
    onChange(value.map((reminder, at) => (at === index ? { ...reminder, ...changes } : reminder)))
  }

  function changeLead(index: number, amount: number, unit: LeadUnit) {
    patch(index, { minutesBefore: toMinutes({ amount, unit }) })
  }

  return (
    <div className="field">
      <span className="field__label">Avisar antes</span>

      {!notificationsReady && (
        <p className="modal__hint">
          Nenhum canal de aviso esta pronto. Os lembretes ficam gravados, mas so comecam a
          sair depois de{' '}
          <button type="button" className="link-button" onClick={onOpenSettings}>
            configurar as notificacoes
          </button>
          .
        </p>
      )}

      {value.length === 0 && (
        <p className="modal__hint">Nenhum aviso. O evento passa em silencio.</p>
      )}

      {value.map((reminder, index) => {
        const lead = toLead(reminder.minutesBefore)
        return (
          <div className="reminder" key={index}>
            <div className="reminder__head">
              <input
                className="reminder__amount"
                type="number"
                min={0}
                max={999}
                value={lead.amount}
                aria-label="Antecedencia"
                onChange={(event) => changeLead(index, Number(event.target.value), lead.unit)}
              />
              <select
                value={lead.unit}
                aria-label="Unidade da antecedencia"
                onChange={(event) => changeLead(index, lead.amount, event.target.value as LeadUnit)}
              >
                {LEAD_UNITS.map((unit) => (
                  <option key={unit.value} value={unit.value}>
                    {unit.label} antes
                  </option>
                ))}
              </select>

              <div className="toolbar__spacer" />

              <label className="checkbox reminder__toggle">
                <input
                  type="checkbox"
                  checked={reminder.enabled}
                  onChange={(event) => patch(index, { enabled: event.target.checked })}
                />
                Ativo
              </label>

              <button
                type="button"
                className="btn btn--ghost btn--icon"
                aria-label={`Remover o aviso de ${describeLead(reminder.minutesBefore)}`}
                onClick={() => onChange(value.filter((_, at) => at !== index))}
              >
                &#10005;
              </button>
            </div>

            <textarea
              className="reminder__message"
              rows={2}
              value={reminder.message ?? ''}
              placeholder={`Lembrete: {titulo} comeca ${leadPhrase(reminder.minutesBefore)}...`}
              aria-label="Mensagem do aviso"
              onChange={(event) => patch(index, { message: event.target.value })}
            />
          </div>
        )
      })}

      {value.length > 0 && (
        <p className="modal__hint">
          Mensagem em branco usa o texto padrao. Dentro dela, {MESSAGE_PLACEHOLDERS.join(' ')} sao
          trocados pelos dados do evento.
          {recurring && ' Os avisos valem para todas as ocorrencias da serie.'}
        </p>
      )}

      {value.length < MAX_REMINDERS && (
        <button
          type="button"
          className="btn reminder__add"
          onClick={() => onChange([...value, newReminder(value)])}
        >
          + Adicionar aviso
        </button>
      )}
    </div>
  )
}
