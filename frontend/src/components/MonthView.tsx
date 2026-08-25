import EventChip from './EventChip'
import { WEEKDAY_LABELS, dayKey, isToday, monthGridDays } from '../lib/dates'
import type { Occurrence } from '../types'

interface Props {
  reference: Date
  occurrencesByDay: Map<string, Occurrence[]>
  onSelectOccurrence: (occurrence: Occurrence) => void
  onSelectDay: (day: Date) => void
  onCreateAt: (day: Date) => void
}

/** Quantos eventos cabem numa celula antes de virar "mais N". */
const MAX_CHIPS_PER_DAY = 3

export default function MonthView({
  reference,
  occurrencesByDay,
  onSelectOccurrence,
  onSelectDay,
  onCreateAt,
}: Props) {
  const days = monthGridDays(reference)
  const weeks = Array.from({ length: 6 }, (_, index) => days.slice(index * 7, index * 7 + 7))

  return (
    <div className="month">
      <div className="month__weekdays">
        {WEEKDAY_LABELS.map((label) => (
          <div key={label} className="month__weekday">
            {label}
          </div>
        ))}
      </div>

      {weeks.map((week) => (
        <div className="month__row" key={dayKey(week[0])}>
          {week.map((day) => {
            const dayOccurrences = occurrencesByDay.get(dayKey(day)) ?? []
            const outside = day.getMonth() !== reference.getMonth()
            const visible = dayOccurrences.slice(0, MAX_CHIPS_PER_DAY)
            const hidden = dayOccurrences.length - visible.length

            return (
              <div
                key={dayKey(day)}
                className={[
                  'day-cell',
                  outside ? 'day-cell--outside' : '',
                  isToday(day) ? 'day-cell--today' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onDoubleClick={() => onCreateAt(day)}
              >
                <button
                  type="button"
                  className="day-cell__number btn--ghost"
                  onClick={() => onSelectDay(day)}
                  title="Abrir este dia"
                >
                  {day.getDate()}
                </button>

                {visible.map((occurrence) => (
                  <EventChip
                    key={`${occurrence.seriesId}|${occurrence.occurrenceStart}`}
                    occurrence={occurrence}
                    onClick={onSelectOccurrence}
                  />
                ))}

                {hidden > 0 && (
                  <button type="button" className="day-cell__more btn--ghost" onClick={() => onSelectDay(day)}>
                    + mais {hidden}
                  </button>
                )}
              </div>
            )
          })}
        </div>
      ))}
    </div>
  )
}
