import { periodTitle } from '../lib/dates'
import type { CalendarView } from '../types'

interface Props {
  view: CalendarView
  reference: Date
  onViewChange: (view: CalendarView) => void
  onNavigate: (direction: -1 | 1) => void
  onToday: () => void
  onCreate: () => void
}

const VIEW_LABELS: Array<[CalendarView, string]> = [
  ['year', 'Ano'],
  ['month', 'Mes'],
  ['week', 'Semana'],
  ['day', 'Dia'],
]

export default function Toolbar({ view, reference, onViewChange, onNavigate, onToday, onCreate }: Props) {
  return (
    <header className="toolbar">
      <div className="toolbar__brand">
        <span aria-hidden>&#128197;</span>
        Calendario
      </div>

      <div className="nav">
        <button type="button" className="btn btn--icon" onClick={() => onNavigate(-1)} aria-label="Periodo anterior">
          &#8249;
        </button>
        <button type="button" className="btn btn--icon" onClick={() => onNavigate(1)} aria-label="Proximo periodo">
          &#8250;
        </button>
        <button type="button" className="btn" onClick={onToday}>
          Hoje
        </button>
      </div>

      <div className="toolbar__title">{periodTitle(view, reference)}</div>

      <div className="toolbar__spacer" />

      <div className="segmented" role="group" aria-label="Modo de visualizacao">
        {VIEW_LABELS.map(([value, label]) => (
          <button
            key={value}
            type="button"
            aria-pressed={view === value}
            onClick={() => onViewChange(value)}
          >
            {label}
          </button>
        ))}
      </div>

      <button type="button" className="btn btn--primary" onClick={onCreate}>
        + Novo evento
      </button>
    </header>
  )
}
