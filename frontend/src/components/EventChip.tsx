import { formatTime, fromLocalIso } from '../lib/dates'
import type { Occurrence } from '../types'

interface Props {
  occurrence: Occurrence
  onClick: (occurrence: Occurrence) => void
  /** Esconde o horario quando o contexto ja deixa claro (linha de dia inteiro). */
  hideTime?: boolean
}

/** Linha compacta de um evento, usada nas visoes de mes e nos eventos de dia inteiro. */
export default function EventChip({ occurrence, onClick, hideTime }: Props) {
  const start = fromLocalIso(occurrence.startAt)
  const showTime = !hideTime && !occurrence.allDay

  return (
    <button
      type="button"
      className="chip"
      style={{ ['--chip' as string]: `var(--event-${occurrence.color})` }}
      title={`${occurrence.title}${occurrence.location ? ` - ${occurrence.location}` : ''}`}
      onClick={(event) => {
        event.stopPropagation()
        onClick(occurrence)
      }}
    >
      <span className="chip__dot" />
      {showTime && <span className="chip__time">{formatTime(start)}</span>}
      <span className="chip__title">{occurrence.title}</span>
      {occurrence.recurring && (
        <span className="chip__repeat" aria-label="Evento recorrente">
          &#8635;
        </span>
      )}
    </button>
  )
}
