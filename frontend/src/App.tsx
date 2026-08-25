import { useCallback, useEffect, useMemo, useState } from 'react'
import EventDialog from './components/EventDialog'
import MonthView from './components/MonthView'
import TimeGridView from './components/TimeGridView'
import Toolbar from './components/Toolbar'
import YearView from './components/YearView'
import { ApiError, api } from './lib/api'
import {
  addDays,
  addMonths,
  addYears,
  dayKey,
  fromLocalIso,
  monthGridDays,
  startOfDay,
  startOfWeek,
  startOfYear,
  weekDays,
} from './lib/dates'
import type { CalendarView, Occurrence } from './types'

/** Intervalo que a visao ativa precisa carregar. */
function rangeFor(view: CalendarView, reference: Date): { from: Date; to: Date } {
  switch (view) {
    case 'year': {
      const from = startOfYear(reference)
      return { from, to: addYears(from, 1) }
    }
    case 'month': {
      // A grade do mes mostra a virada dos meses vizinhos, entao carrega as 6 semanas inteiras.
      const days = monthGridDays(reference)
      return { from: days[0], to: addDays(days[days.length - 1], 1) }
    }
    case 'week': {
      const from = startOfWeek(reference)
      return { from, to: addDays(from, 7) }
    }
    default: {
      const from = startOfDay(reference)
      return { from, to: addDays(from, 1) }
    }
  }
}

/** Teto de dias que um unico evento ocupa no indice por dia. */
const MAX_EVENT_SPAN_DAYS = 400

/**
 * Indexa as ocorrencias por dia. Um evento de varios dias entra em cada dia
 * que ele cobre, para aparecer em todas as celulas por onde passa.
 */
function groupByDay(occurrences: Occurrence[]): Map<string, Occurrence[]> {
  const byDay = new Map<string, Occurrence[]>()

  for (const occurrence of occurrences) {
    const start = startOfDay(fromLocalIso(occurrence.startAt))
    const end = startOfDay(fromLocalIso(occurrence.endAt))
    // Um evento absurdamente longo nao pode encher o mapa de dias.
    const last = Math.min(end.getTime(), addDays(start, MAX_EVENT_SPAN_DAYS).getTime())

    for (let day = start; day.getTime() <= last; day = addDays(day, 1)) {
      const key = dayKey(day)
      const bucket = byDay.get(key)
      if (bucket) {
        bucket.push(occurrence)
      } else {
        byDay.set(key, [occurrence])
      }
    }
  }
  return byDay
}

export default function App() {
  const [view, setView] = useState<CalendarView>('month')
  const [reference, setReference] = useState(() => new Date())
  const [occurrences, setOccurrences] = useState<Occurrence[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<Occurrence | null>(null)
  const [draftStart, setDraftStart] = useState(() => new Date())

  const { from, to } = useMemo(() => rangeFor(view, reference), [view, reference])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setOccurrences(await api.occurrences(from, to))
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'Falha ao carregar os eventos.')
      setOccurrences([])
    } finally {
      setLoading(false)
    }
  }, [from, to])

  useEffect(() => {
    void load()
  }, [load])

  const occurrencesByDay = useMemo(() => groupByDay(occurrences), [occurrences])

  function navigate(direction: -1 | 1) {
    setReference((current) => {
      switch (view) {
        case 'year':
          return addYears(current, direction)
        case 'month':
          return addMonths(current, direction)
        case 'week':
          return addDays(current, 7 * direction)
        default:
          return addDays(current, direction)
      }
    })
  }

  function openCreate(at?: Date) {
    const base = at ?? defaultDraftStart(reference)
    setDraftStart(base)
    setEditing(null)
    setDialogOpen(true)
  }

  function openEdit(occurrence: Occurrence) {
    setEditing(occurrence)
    setDialogOpen(true)
  }

  function openDay(day: Date) {
    setReference(day)
    setView('day')
  }

  return (
    <div className="app">
      <Toolbar
        view={view}
        reference={reference}
        onViewChange={setView}
        onNavigate={navigate}
        onToday={() => setReference(new Date())}
        onCreate={() => openCreate()}
      />

      {error && <div className="status-bar status-bar--error">{error}</div>}
      {loading && !error && <div className="status-bar status-bar--loading">Carregando eventos...</div>}

      <main className="viewport">
        {view === 'year' && (
          <YearView
            year={reference.getFullYear()}
            occurrencesByDay={occurrencesByDay}
            onSelectDay={openDay}
          />
        )}

        {view === 'month' && (
          <MonthView
            reference={reference}
            occurrencesByDay={occurrencesByDay}
            onSelectOccurrence={openEdit}
            onSelectDay={openDay}
            onCreateAt={(day) => openCreate(withDefaultHour(day))}
          />
        )}

        {(view === 'week' || view === 'day') && (
          <TimeGridView
            days={view === 'week' ? weekDays(reference) : [startOfDay(reference)]}
            occurrencesByDay={occurrencesByDay}
            onSelectOccurrence={openEdit}
            onCreateAt={openCreate}
          />
        )}
      </main>

      {dialogOpen && (
        <EventDialog
          key={editing ? `${editing.seriesId}|${editing.occurrenceStart}` : 'novo'}
          occurrence={editing}
          initialStart={draftStart}
          onClose={() => setDialogOpen(false)}
          onSaved={() => {
            setDialogOpen(false)
            void load()
          }}
        />
      )}
    </div>
  )
}

/** Ao criar sem apontar um horario, sugere a proxima hora cheia do dia visivel. */
function defaultDraftStart(reference: Date): Date {
  const base = new Date(reference)
  // Perto da meia-noite a "proxima hora" viraria o dia, entao ela para nas 23h.
  base.setHours(Math.min(23, new Date().getHours() + 1), 0, 0, 0)
  return base
}

function withDefaultHour(day: Date): Date {
  const moment = new Date(day)
  moment.setHours(9, 0, 0, 0)
  return moment
}
