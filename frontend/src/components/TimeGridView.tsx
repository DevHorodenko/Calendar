import { useEffect, useRef, useState } from 'react'
import EventChip from './EventChip'
import {
  dayKey,
  formatTime,
  fromLocalIso,
  isToday,
  minutesSinceMidnight,
  WEEKDAY_LABELS,
} from '../lib/dates'
import type { Occurrence } from '../types'

interface Props {
  days: Date[]
  occurrencesByDay: Map<string, Occurrence[]>
  onSelectOccurrence: (occurrence: Occurrence) => void
  onCreateAt: (moment: Date) => void
}

const HOUR_HEIGHT = 48
const MINUTES_PER_DAY = 24 * 60
/** Altura minima para um evento curto continuar clicavel e legivel. */
const MIN_EVENT_HEIGHT = 18

interface Placed {
  occurrence: Occurrence
  top: number
  height: number
  left: number
  width: number
}

/**
 * Distribui os eventos do dia em faixas verticais.
 *
 * Eventos que se sobrepoem no tempo formam um grupo e dividem a largura da coluna
 * entre si, do mesmo jeito que um calendario de agenda faz.
 */
function placeEvents(occurrences: Occurrence[]): Placed[] {
  const timed = occurrences
    .filter((occurrence) => !occurrence.allDay)
    .map((occurrence) => {
      const start = fromLocalIso(occurrence.startAt)
      const end = fromLocalIso(occurrence.endAt)
      const startMinutes = Math.max(0, minutesSinceMidnight(start))
      // Um evento que atravessa a meia-noite e cortado no fim do dia desenhado.
      const rawEnd = end.getDate() === start.getDate() ? minutesSinceMidnight(end) : MINUTES_PER_DAY
      return { occurrence, startMinutes, endMinutes: Math.max(rawEnd, startMinutes + 15) }
    })
    .sort((a, b) => a.startMinutes - b.startMinutes || b.endMinutes - a.endMinutes)

  const placed: Placed[] = []
  let cluster: typeof timed = []
  let clusterEnd = -1

  const flush = () => {
    if (cluster.length === 0) {
      return
    }
    const laneEnds: number[] = []
    const lanes = cluster.map((item) => {
      const lane = laneEnds.findIndex((end) => end <= item.startMinutes)
      const index = lane === -1 ? laneEnds.length : lane
      laneEnds[index] = item.endMinutes
      return index
    })
    const laneCount = laneEnds.length

    cluster.forEach((item, index) => {
      placed.push({
        occurrence: item.occurrence,
        top: (item.startMinutes / 60) * HOUR_HEIGHT,
        height: Math.max(MIN_EVENT_HEIGHT, ((item.endMinutes - item.startMinutes) / 60) * HOUR_HEIGHT),
        left: (lanes[index] / laneCount) * 100,
        width: 100 / laneCount,
      })
    })
    cluster = []
    clusterEnd = -1
  }

  for (const item of timed) {
    if (cluster.length > 0 && item.startMinutes >= clusterEnd) {
      flush()
    }
    cluster.push(item)
    clusterEnd = Math.max(clusterEnd, item.endMinutes)
  }
  flush()

  return placed
}

/** Minuto atual, atualizado de tempos em tempos para a linha do "agora" nao congelar. */
function useCurrentMinute(): number {
  const [minute, setMinute] = useState(() => minutesSinceMidnight(new Date()))

  useEffect(() => {
    const timer = window.setInterval(() => setMinute(minutesSinceMidnight(new Date())), 60_000)
    return () => window.clearInterval(timer)
  }, [])

  return minute
}

export default function TimeGridView({ days, occurrencesByDay, onSelectOccurrence, onCreateAt }: Props) {
  const currentMinute = useCurrentMinute()
  const bodyRef = useRef<HTMLDivElement>(null)
  const columns = `var(--time-gutter) repeat(${days.length}, minmax(0, 1fr))`

  // Abre a agenda perto do horario comercial em vez de na madrugada.
  useEffect(() => {
    bodyRef.current?.parentElement?.scrollTo({ top: 7 * HOUR_HEIGHT })
  }, [])

  const hasAllDay = days.some((day) =>
    (occurrencesByDay.get(dayKey(day)) ?? []).some((occurrence) => occurrence.allDay),
  )

  /** Converte um clique na coluna no horario correspondente, arredondado em 30 minutos. */
  const handleColumnClick = (day: Date, event: React.MouseEvent<HTMLDivElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect()
    const minutes = ((event.clientY - bounds.top) / HOUR_HEIGHT) * 60
    const rounded = Math.max(0, Math.min(MINUTES_PER_DAY - 30, Math.floor(minutes / 30) * 30))
    const moment = new Date(day)
    moment.setHours(Math.floor(rounded / 60), rounded % 60, 0, 0)
    onCreateAt(moment)
  }

  return (
    <div className="timegrid">
      <div className="timegrid__header" style={{ gridTemplateColumns: columns }}>
        <div className="timegrid__corner" />
        {days.map((day) => (
          <div
            key={dayKey(day)}
            className={`timegrid__daylabel${isToday(day) ? ' timegrid__daylabel--today' : ''}`}
          >
            <div className="timegrid__dayname">{WEEKDAY_LABELS[day.getDay()]}</div>
            <div className="timegrid__daynumber">{day.getDate()}</div>
          </div>
        ))}
      </div>

      {hasAllDay && (
        <div className="timegrid__allday" style={{ gridTemplateColumns: columns }}>
          <div className="timegrid__allday-label">Dia todo</div>
          {days.map((day) => (
            <div key={dayKey(day)} className="timegrid__allday-cell">
              {(occurrencesByDay.get(dayKey(day)) ?? [])
                .filter((occurrence) => occurrence.allDay)
                .map((occurrence) => (
                  <EventChip
                    key={`${occurrence.seriesId}|${occurrence.occurrenceStart}`}
                    occurrence={occurrence}
                    onClick={onSelectOccurrence}
                    hideTime
                  />
                ))}
            </div>
          ))}
        </div>
      )}

      <div className="timegrid__body" ref={bodyRef} style={{ gridTemplateColumns: columns }}>
        <div className="timegrid__gutter">
          {Array.from({ length: 24 }, (_, hour) => (
            <div key={hour} className="timegrid__hourlabel">
              {hour > 0 && <span>{String(hour).padStart(2, '0')}:00</span>}
            </div>
          ))}
        </div>

        {days.map((day) => (
          <div
            key={dayKey(day)}
            className={`timegrid__column${isToday(day) ? ' timegrid__column--today' : ''}`}
            onDoubleClick={(event) => handleColumnClick(day, event)}
            title="Clique duas vezes para criar um evento neste horario"
          >
            {Array.from({ length: 24 }, (_, hour) => (
              <div key={hour} className="timegrid__hourline" />
            ))}

            {isToday(day) && (
              <div
                className="timegrid__now"
                style={{ top: `${(currentMinute / 60) * HOUR_HEIGHT}px` }}
                aria-hidden
              />
            )}

            {placeEvents(occurrencesByDay.get(dayKey(day)) ?? []).map((placed) => (
              <button
                key={`${placed.occurrence.seriesId}|${placed.occurrence.occurrenceStart}`}
                type="button"
                className="tg-event"
                style={{
                  top: `${placed.top}px`,
                  height: `${placed.height}px`,
                  left: `calc(${placed.left}% + 2px)`,
                  width: `calc(${placed.width}% - 4px)`,
                  ['--chip' as string]: placed.occurrence.color,
                }}
                onClick={(event) => {
                  event.stopPropagation()
                  onSelectOccurrence(placed.occurrence)
                }}
              >
                <div className="tg-event__title">
                  {placed.occurrence.title}
                  {placed.occurrence.recurring && ' ↻'}
                </div>
                {placed.height > 30 && (
                  <div className="tg-event__time">
                    {formatTime(fromLocalIso(placed.occurrence.startAt))} -{' '}
                    {formatTime(fromLocalIso(placed.occurrence.endAt))}
                  </div>
                )}
              </button>
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}
