import {
  MONTH_LABELS,
  WEEKDAY_LABELS,
  addDays,
  dayKey,
  isToday,
  startOfWeek,
} from '../lib/dates'
import type { Occurrence } from '../types'

interface Props {
  year: number
  occurrencesByDay: Map<string, Occurrence[]>
  onSelectDay: (day: Date) => void
}

/** Quantas bolinhas de cor um dia mostra antes de parar de acumular. */
const MAX_DOTS = 3

export default function YearView({ year, occurrencesByDay, onSelectDay }: Props) {
  return (
    <div className="year">
      {MONTH_LABELS.map((label, month) => (
        <MiniMonth
          key={label}
          year={year}
          month={month}
          label={label}
          occurrencesByDay={occurrencesByDay}
          onSelectDay={onSelectDay}
        />
      ))}
    </div>
  )
}

function MiniMonth({
  year,
  month,
  label,
  occurrencesByDay,
  onSelectDay,
}: {
  year: number
  month: number
  label: string
  occurrencesByDay: Map<string, Occurrence[]>
  onSelectDay: (day: Date) => void
}) {
  const first = startOfWeek(new Date(year, month, 1))
  const days = Array.from({ length: 42 }, (_, index) => addDays(first, index))

  return (
    <section className="mini">
      <h2 className="mini__title">{label}</h2>
      <div className="mini__grid">
        {WEEKDAY_LABELS.map((weekday) => (
          <div key={weekday} className="mini__weekday">
            {weekday.charAt(0)}
          </div>
        ))}

        {days.map((day) => {
          const outside = day.getMonth() !== month
          const dayOccurrences = outside ? [] : occurrencesByDay.get(dayKey(day)) ?? []
          // Uma bolinha por cor, para o dia mostrar a variedade em vez da quantidade.
          const colors = [...new Set(dayOccurrences.map((occurrence) => occurrence.color))].slice(0, MAX_DOTS)

          return (
            <button
              key={dayKey(day)}
              type="button"
              className={[
                'mini__day',
                outside ? 'mini__day--outside' : '',
                !outside && isToday(day) ? 'mini__day--today' : '',
              ]
                .filter(Boolean)
                .join(' ')}
              onClick={() => onSelectDay(day)}
              title={
                dayOccurrences.length > 0
                  ? dayOccurrences.map((occurrence) => occurrence.title).join('\n')
                  : undefined
              }
            >
              {day.getDate()}
              {colors.length > 0 && (
                <span className="mini__dots">
                  {colors.map((color) => (
                    <span
                      key={color}
                      className="mini__dot"
                      style={{ ['--chip' as string]: color }}
                    />
                  ))}
                </span>
              )}
            </button>
          )
        })}
      </div>
    </section>
  )
}
