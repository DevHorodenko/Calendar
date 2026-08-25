import {
  WEEKDAY_CODES,
  WEEKDAY_SHORT,
  buildRrule,
  describeRrule,
} from '../lib/recurrence'
import type { Frequency, RecurrencePreset, RecurrenceState } from '../lib/recurrence'
import { WEEKDAY_LABELS } from '../lib/dates'

interface Props {
  value: RecurrenceState
  start: Date
  onChange: (state: RecurrenceState) => void
}

const FREQ_LABELS: Array<[Frequency, string]> = [
  ['DAILY', 'dia(s)'],
  ['WEEKLY', 'semana(s)'],
  ['MONTHLY', 'mes(es)'],
  ['YEARLY', 'ano(s)'],
]

export default function RecurrenceEditor({ value, start, onChange }: Props) {
  const patch = (changes: Partial<RecurrenceState>) => onChange({ ...value, ...changes })
  const weekdayName = WEEKDAY_LABELS[start.getDay()]

  const presets: Array<[RecurrencePreset, string]> = [
    ['none', 'Nao se repete'],
    ['daily', 'Todo dia'],
    ['weekdays', 'De segunda a sexta'],
    ['weekly', `Toda semana (${weekdayName})`],
    ['monthly', `Todo mes (dia ${start.getDate()})`],
    ['yearly', 'Todo ano'],
    ['custom', 'Personalizado...'],
  ]

  return (
    <div className="field">
      <label className="field__label" htmlFor="recurrence-preset">
        Repeticao
      </label>
      <select
        id="recurrence-preset"
        value={value.preset}
        onChange={(event) => patch({ preset: event.target.value as RecurrencePreset })}
      >
        {presets.map(([preset, label]) => (
          <option key={preset} value={preset}>
            {label}
          </option>
        ))}
      </select>

      {value.preset !== 'none' && (
        <div className="recurrence-box">
          {value.preset === 'custom' && (
            <>
              <div className="field__row">
                <div className="field">
                  <label className="field__label" htmlFor="recurrence-interval">
                    A cada
                  </label>
                  <input
                    id="recurrence-interval"
                    type="number"
                    min={1}
                    max={99}
                    value={value.interval}
                    onChange={(event) => patch({ interval: Math.max(1, Number(event.target.value)) })}
                  />
                </div>
                <div className="field">
                  <label className="field__label" htmlFor="recurrence-freq">
                    Unidade
                  </label>
                  <select
                    id="recurrence-freq"
                    value={value.freq}
                    onChange={(event) => patch({ freq: event.target.value as Frequency })}
                  >
                    {FREQ_LABELS.map(([freq, label]) => (
                      <option key={freq} value={freq}>
                        {label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {value.freq === 'WEEKLY' && (
                <div className="field">
                  <span className="field__label">Nos dias</span>
                  <div className="chips-row">
                    {WEEKDAY_CODES.map((code) => {
                      const selected = value.byDay.includes(code)
                      return (
                        <button
                          key={code}
                          type="button"
                          className="toggle-chip"
                          aria-pressed={selected}
                          onClick={() =>
                            patch({
                              byDay: selected
                                ? value.byDay.filter((day) => day !== code)
                                : [...value.byDay, code],
                            })
                          }
                        >
                          {WEEKDAY_SHORT[code]}
                        </button>
                      )
                    })}
                  </div>
                </div>
              )}

              {value.freq === 'MONTHLY' && (
                <div className="field">
                  <label className="field__label" htmlFor="recurrence-monthly">
                    No mes, repetir
                  </label>
                  <select
                    id="recurrence-monthly"
                    value={value.monthlyMode}
                    onChange={(event) =>
                      patch({ monthlyMode: event.target.value as RecurrenceState['monthlyMode'] })
                    }
                  >
                    <option value="dayOfMonth">no dia {start.getDate()}</option>
                    <option value="dayOfWeek">na mesma semana e dia ({weekdayName})</option>
                  </select>
                </div>
              )}
            </>
          )}

          <div className="field">
            <label className="field__label" htmlFor="recurrence-ending">
              Termina
            </label>
            <select
              id="recurrence-ending"
              value={value.ending}
              onChange={(event) => patch({ ending: event.target.value as RecurrenceState['ending'] })}
            >
              <option value="never">Nunca</option>
              <option value="count">Depois de um numero de vezes</option>
              <option value="until">Em uma data</option>
            </select>
          </div>

          {value.ending === 'count' && (
            <div className="field">
              <label className="field__label" htmlFor="recurrence-count">
                Quantas vezes
              </label>
              <input
                id="recurrence-count"
                type="number"
                min={1}
                max={999}
                value={value.count}
                onChange={(event) => patch({ count: Math.max(1, Number(event.target.value)) })}
              />
            </div>
          )}

          {value.ending === 'until' && (
            <div className="field">
              <label className="field__label" htmlFor="recurrence-until">
                Ate
              </label>
              <input
                id="recurrence-until"
                type="date"
                value={value.until}
                onChange={(event) => patch({ until: event.target.value })}
              />
            </div>
          )}

          <p className="modal__hint">{describeRrule(buildRrule(value, start))}</p>
        </div>
      )}
    </div>
  )
}
