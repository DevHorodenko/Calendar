import { useState } from 'react'
import RecurrenceEditor from './RecurrenceEditor'
import ScopeDialog from './ScopeDialog'
import { ApiError, api } from '../lib/api'
import { fromLocalIso, toDateInputValue, toInputValue, toLocalIso } from '../lib/dates'
import { buildRrule, defaultRecurrence, parseRrule } from '../lib/recurrence'
import type { RecurrenceState } from '../lib/recurrence'
import { COLOR_LABELS, EVENT_COLORS } from '../types'
import type { EditScope, EventColor, EventRequest, Occurrence } from '../types'

interface Props {
  /** A ocorrencia sendo editada, ou null quando o evento esta sendo criado. */
  occurrence: Occurrence | null
  /** Horario inicial sugerido ao criar. */
  initialStart: Date
  onClose: () => void
  onSaved: () => void
}

export default function EventDialog({ occurrence, initialStart, onClose, onSaved }: Props) {
  const editing = occurrence !== null
  const start = occurrence ? fromLocalIso(occurrence.startAt) : initialStart
  const end = occurrence ? fromLocalIso(occurrence.endAt) : new Date(initialStart.getTime() + 60 * 60 * 1000)

  const [title, setTitle] = useState(occurrence?.title ?? '')
  const [description, setDescription] = useState(occurrence?.description ?? '')
  const [location, setLocation] = useState(occurrence?.location ?? '')
  const [allDay, setAllDay] = useState(occurrence?.allDay ?? false)
  const [startValue, setStartValue] = useState(toInputValue(start))
  const [endValue, setEndValue] = useState(toInputValue(end))
  const [color, setColor] = useState<EventColor>(occurrence?.color ?? 'blue')
  const [recurrence, setRecurrence] = useState<RecurrenceState>(() =>
    occurrence ? parseRrule(occurrence.recurrenceRule, start) : defaultRecurrence(start),
  )

  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [pendingAction, setPendingAction] = useState<'save' | 'delete' | null>(null)

  const startDate = fromLocalIso(startValue)

  /**
   * Um evento de dia inteiro ocupa da meia-noite ao ultimo segundo do dia final,
   * que e como o backend o guarda em LocalDateTime.
   */
  function buildRequest(): EventRequest {
    const from = fromLocalIso(startValue)
    const to = fromLocalIso(endValue)

    if (allDay) {
      from.setHours(0, 0, 0, 0)
      to.setHours(23, 59, 59, 0)
    }

    return {
      title: title.trim(),
      description: description.trim() || undefined,
      location: location.trim() || undefined,
      allDay,
      startAt: toLocalIso(from),
      endAt: toLocalIso(to),
      recurrenceRule: buildRrule(recurrence, from),
      color,
    }
  }

  function validate(): string | null {
    if (!title.trim()) {
      return 'Escreva um titulo para o evento.'
    }
    if (fromLocalIso(endValue) < fromLocalIso(startValue)) {
      return 'O fim do evento nao pode ser antes do inicio.'
    }
    if (recurrence.preset === 'custom' && recurrence.freq === 'WEEKLY' && recurrence.byDay.length === 0) {
      return 'Escolha pelo menos um dia da semana para a repeticao.'
    }
    return null
  }

  function handleSave() {
    const problem = validate()
    if (problem) {
      setError(problem)
      return
    }
    // Mexer numa serie recorrente exige saber o alcance antes de gravar.
    if (editing && occurrence.recurring) {
      setPendingAction('save')
      return
    }
    void submit('ALL')
  }

  function handleDelete() {
    if (editing && occurrence.recurring) {
      setPendingAction('delete')
      return
    }
    void remove('ALL')
  }

  async function submit(scope: EditScope) {
    setSaving(true)
    setError(null)
    try {
      if (editing) {
        await api.update(occurrence.seriesId, buildRequest(), scope, fromLocalIso(occurrence.occurrenceStart))
      } else {
        await api.create(buildRequest())
      }
      onSaved()
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'Nao foi possivel salvar o evento.')
      setPendingAction(null)
    } finally {
      setSaving(false)
    }
  }

  async function remove(scope: EditScope) {
    if (!editing) {
      return
    }
    setSaving(true)
    setError(null)
    try {
      await api.remove(occurrence.seriesId, scope, fromLocalIso(occurrence.occurrenceStart))
      onSaved()
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'Nao foi possivel apagar o evento.')
      setPendingAction(null)
    } finally {
      setSaving(false)
    }
  }

  if (pendingAction) {
    return (
      <ScopeDialog
        action={pendingAction}
        onCancel={() => setPendingAction(null)}
        onChoose={(scope) => (pendingAction === 'delete' ? void remove(scope) : void submit(scope))}
      />
    )
  }

  return (
    <div className="overlay" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={editing ? 'Editar evento' : 'Novo evento'}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal__header">
          <h2 className="modal__title">{editing ? 'Editar evento' : 'Novo evento'}</h2>
          <button type="button" className="btn btn--ghost btn--icon" onClick={onClose} aria-label="Fechar">
            &#10005;
          </button>
        </div>

        <div className="modal__body">
          {error && <div className="form-error">{error}</div>}

          <div className="field">
            <label className="field__label" htmlFor="event-title">
              Titulo
            </label>
            <input
              id="event-title"
              type="text"
              value={title}
              autoFocus
              placeholder="Reuniao, consulta, treino..."
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>

          <label className="checkbox">
            <input type="checkbox" checked={allDay} onChange={(event) => setAllDay(event.target.checked)} />
            Dia inteiro
          </label>

          <div className="field__row">
            <div className="field">
              <label className="field__label" htmlFor="event-start">
                Inicio
              </label>
              <input
                id="event-start"
                type={allDay ? 'date' : 'datetime-local'}
                value={allDay ? startValue.slice(0, 10) : startValue}
                onChange={(event) => {
                  const next = allDay ? `${event.target.value}T00:00` : event.target.value
                  setStartValue(next)
                  // Arrastar o inicio para depois do fim empurra o fim junto.
                  if (fromLocalIso(next) > fromLocalIso(endValue)) {
                    setEndValue(next)
                  }
                }}
              />
            </div>
            <div className="field">
              <label className="field__label" htmlFor="event-end">
                Fim
              </label>
              <input
                id="event-end"
                type={allDay ? 'date' : 'datetime-local'}
                value={allDay ? endValue.slice(0, 10) : endValue}
                min={allDay ? toDateInputValue(startDate) : startValue}
                onChange={(event) =>
                  setEndValue(allDay ? `${event.target.value}T23:59` : event.target.value)
                }
              />
            </div>
          </div>

          <RecurrenceEditor value={recurrence} start={startDate} onChange={setRecurrence} />

          <div className="field">
            <label className="field__label" htmlFor="event-location">
              Local
            </label>
            <input
              id="event-location"
              type="text"
              value={location}
              placeholder="Opcional"
              onChange={(event) => setLocation(event.target.value)}
            />
          </div>

          <div className="field">
            <label className="field__label" htmlFor="event-description">
              Descricao
            </label>
            <textarea
              id="event-description"
              value={description}
              placeholder="Opcional"
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>

          <div className="field">
            <span className="field__label">Cor</span>
            <div className="swatches">
              {EVENT_COLORS.map((option) => (
                <button
                  key={option}
                  type="button"
                  className="swatch"
                  aria-pressed={color === option}
                  aria-label={COLOR_LABELS[option]}
                  title={COLOR_LABELS[option]}
                  style={{ ['--chip' as string]: `var(--event-${option})` }}
                  onClick={() => setColor(option)}
                />
              ))}
            </div>
          </div>
        </div>

        <div className="modal__footer">
          {editing && (
            <button type="button" className="btn btn--danger" onClick={handleDelete} disabled={saving}>
              Apagar
            </button>
          )}
          <div className="toolbar__spacer" />
          <button type="button" className="btn" onClick={onClose} disabled={saving}>
            Cancelar
          </button>
          <button type="button" className="btn btn--primary" onClick={handleSave} disabled={saving}>
            {saving ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </div>
    </div>
  )
}
