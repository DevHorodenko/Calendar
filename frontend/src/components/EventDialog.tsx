import { useState } from 'react'
import RecurrenceEditor from './RecurrenceEditor'
import ReminderEditor from './ReminderEditor'
import ScopeDialog from './ScopeDialog'
import { ApiError, api } from '../lib/api'
import {
  datePartOf,
  fromLocalIso,
  timePartOf,
  toInputValue,
  toLocalIso,
  withDatePart,
  withTimePart,
} from '../lib/dates'
import { buildRrule, defaultRecurrence, parseRrule } from '../lib/recurrence'
import type { RecurrenceState } from '../lib/recurrence'
import { describeLead, findDuplicateLead } from '../lib/reminders'
import ColorPicker from './ColorPicker'
import { DEFAULT_EVENT_COLOR } from '../types'
import type { EditScope, EventColor, EventRequest, Occurrence, Reminder } from '../types'

interface Props {
  /** A ocorrencia sendo editada, ou null quando o evento esta sendo criado. */
  occurrence: Occurrence | null
  /** Horario inicial sugerido ao criar. */
  initialStart: Date
  /** Se algum canal de aviso esta pronto, para o editor de lembretes dizer quando nao esta. */
  notificationsReady: boolean
  onClose: () => void
  onSaved: () => void
  onOpenNotificationSettings: () => void
}

export default function EventDialog({
  occurrence,
  initialStart,
  notificationsReady,
  onClose,
  onSaved,
  onOpenNotificationSettings,
}: Props) {
  const editing = occurrence !== null
  const start = occurrence ? fromLocalIso(occurrence.startAt) : initialStart
  const end = occurrence ? fromLocalIso(occurrence.endAt) : new Date(initialStart.getTime() + 60 * 60 * 1000)

  const [title, setTitle] = useState(occurrence?.title ?? '')
  const [description, setDescription] = useState(occurrence?.description ?? '')
  const [location, setLocation] = useState(occurrence?.location ?? '')
  const [allDay, setAllDay] = useState(occurrence?.allDay ?? false)
  const [startValue, setStartValue] = useState(toInputValue(start))
  const [endValue, setEndValue] = useState(toInputValue(end))
  const [color, setColor] = useState<EventColor>(occurrence?.color ?? DEFAULT_EVENT_COLOR)
  const [recurrence, setRecurrence] = useState<RecurrenceState>(() =>
    occurrence ? parseRrule(occurrence.recurrenceRule, start) : defaultRecurrence(start),
  )
  const [reminders, setReminders] = useState<Reminder[]>(() => occurrence?.reminders ?? [])

  /**
   * Numa serie, a data de fim descreve a duracao de uma ocorrencia, e nao o fim da
   * repeticao -- quem manda nisso e o "A repeticao termina" da caixa de recorrencia.
   * Como quase toda ocorrencia cabe num dia so, a data de fim some enquanto o evento
   * se repete, e este escape a traz de volta para os casos que atravessam dias.
   */
  const [endsOnAnotherDay, setEndsOnAnotherDay] = useState(
    () => toInputValue(start).slice(0, 10) !== toInputValue(end).slice(0, 10),
  )

  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [pendingAction, setPendingAction] = useState<'save' | 'delete' | null>(null)

  const startDate = fromLocalIso(startValue)
  const repeating = recurrence.preset !== 'none'
  const showEndDate = !repeating || endsOnAnotherDay

  /** Fim que vale de fato: com a data escondida, a ocorrencia termina no dia em que comecou. */
  function effectiveEnd(): string {
    return showEndDate ? endValue : withDatePart(endValue, datePartOf(startValue))
  }

  /**
   * Um evento de dia inteiro ocupa da meia-noite ao ultimo segundo do dia final,
   * que e como o backend o guarda em LocalDateTime.
   */
  function buildRequest(): EventRequest {
    const from = fromLocalIso(startValue)
    const to = fromLocalIso(effectiveEnd())

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
      reminders,
    }
  }

  function moveStart(next: string) {
    setStartValue(next)
    // Com a data de fim escondida, ela acompanha a de inicio em vez de ficar para tras.
    const glued = showEndDate ? endValue : withDatePart(endValue, datePartOf(next))
    // Mover o inicio para depois do fim arrasta o fim junto.
    setEndValue(fromLocalIso(next) > fromLocalIso(glued) ? next : glued)
  }

  /**
   * Ligar a repeticao esconde a data de fim, entao um intervalo de varios dias que ja
   * estivesse montado marca o escape sozinho: melhor mostrar o campo do que encolher
   * o evento sem avisar.
   */
  function changeRecurrence(next: RecurrenceState) {
    if (next.preset !== 'none' && !repeating && datePartOf(endValue) !== datePartOf(startValue)) {
      setEndsOnAnotherDay(true)
    }
    setRecurrence(next)
  }

  function toggleEndsOnAnotherDay(checked: boolean) {
    setEndsOnAnotherDay(checked)
    if (!checked) {
      setEndValue(withDatePart(endValue, datePartOf(startValue)))
    }
  }

  function validate(): string | null {
    if (!title.trim()) {
      return 'Escreva um titulo para o evento.'
    }
    if (fromLocalIso(effectiveEnd()) < fromLocalIso(startValue)) {
      return repeating && !endsOnAnotherDay
        ? 'O fim vem antes do inicio. Se o evento atravessa a meia-noite, marque "termina em outro dia".'
        : 'O fim do evento nao pode ser antes do inicio.'
    }
    if (recurrence.preset === 'custom' && recurrence.freq === 'WEEKLY' && recurrence.byDay.length === 0) {
      return 'Escolha pelo menos um dia da semana para a repeticao.'
    }
    // O backend guardaria so um dos dois, e o aviso perdido sumiria sem explicacao.
    const repeated = findDuplicateLead(reminders)
    if (repeated !== null) {
      return `Ha dois avisos marcados para ${describeLead(repeated)}. Deixe so um.`
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

          <div className="field">
            <label className="field__label" htmlFor="event-start-date">
              Inicio
            </label>
            <input
              id="event-start-date"
              type="date"
              value={datePartOf(startValue)}
              onChange={(event) => moveStart(withDatePart(startValue, event.target.value))}
            />
          </div>

          {showEndDate && (
            <div className="field">
              <label className="field__label" htmlFor="event-end-date">
                {repeating ? 'Termina em' : 'Fim'}
              </label>
              <input
                id="event-end-date"
                type="date"
                value={datePartOf(endValue)}
                min={datePartOf(startValue)}
                onChange={(event) => setEndValue(withDatePart(endValue, event.target.value))}
              />
            </div>
          )}

          {repeating && (
            <label className="checkbox">
              <input
                type="checkbox"
                checked={endsOnAnotherDay}
                onChange={(event) => toggleEndsOnAnotherDay(event.target.checked)}
              />
              Termina em outro dia
            </label>
          )}

          {!allDay && (
            <div className="time-range">
              <div className="field">
                <label className="field__label" htmlFor="event-start-time">
                  Horario inicial
                </label>
                <input
                  id="event-start-time"
                  type="time"
                  value={timePartOf(startValue)}
                  onChange={(event) => moveStart(withTimePart(startValue, event.target.value))}
                />
              </div>
              <span className="time-range__dash" aria-hidden>
                &ndash;
              </span>
              <div className="field">
                <label className="field__label" htmlFor="event-end-time">
                  Horario final
                </label>
                <input
                  id="event-end-time"
                  type="time"
                  value={timePartOf(endValue)}
                  onChange={(event) => setEndValue(withTimePart(endValue, event.target.value))}
                />
              </div>
            </div>
          )}

          <RecurrenceEditor value={recurrence} start={startDate} onChange={changeRecurrence} />

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
            <ColorPicker value={color} onChange={setColor} />
          </div>

          <ReminderEditor
            value={reminders}
            recurring={repeating}
            notificationsReady={notificationsReady}
            onChange={setReminders}
            onOpenSettings={onOpenNotificationSettings}
          />
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
