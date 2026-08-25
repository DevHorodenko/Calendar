/**
 * Traducao entre o formulario de repeticao e a RRULE que o backend entende.
 *
 * A tela oferece as escolhas comuns (todo dia, toda semana, dias uteis...) e um modo
 * personalizado; tudo vira uma string RRULE. O caminho de volta existe para reabrir um
 * evento ja salvo com o formulario preenchido do jeito que ele foi criado.
 */

import { daysInMonth, toDateInputValue } from './dates'

export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'
export type RecurrencePreset = 'none' | 'daily' | 'weekdays' | 'weekly' | 'monthly' | 'yearly' | 'custom'
export type Ending = 'never' | 'count' | 'until'
export type MonthlyMode = 'dayOfMonth' | 'dayOfWeek'

export interface RecurrenceState {
  preset: RecurrencePreset
  freq: Frequency
  interval: number
  /** Codigos de dia da semana (MO, TU...) usados no modo personalizado semanal. */
  byDay: string[]
  monthlyMode: MonthlyMode
  ending: Ending
  count: number
  until: string
}

export const WEEKDAY_CODES = ['SU', 'MO', 'TU', 'WE', 'TH', 'FR', 'SA'] as const
export const WEEKDAY_SHORT: Record<string, string> = {
  SU: 'Dom', MO: 'Seg', TU: 'Ter', WE: 'Qua', TH: 'Qui', FR: 'Sex', SA: 'Sab',
}
const WEEKDAY_LONG: Record<string, string> = {
  SU: 'domingo', MO: 'segunda', TU: 'terca', WE: 'quarta', TH: 'quinta', FR: 'sexta', SA: 'sabado',
}
const WEEKDAYS_ONLY = ['MO', 'TU', 'WE', 'TH', 'FR']

export function defaultRecurrence(start: Date): RecurrenceState {
  return {
    preset: 'none',
    freq: 'WEEKLY',
    interval: 1,
    byDay: [WEEKDAY_CODES[start.getDay()]],
    monthlyMode: 'dayOfMonth',
    ending: 'never',
    count: 10,
    until: toDateInputValue(start),
  }
}

/** Posicao da semana em que a data cai no mes; -1 quando e a ultima daquele dia da semana. */
function weekdayOrdinal(date: Date): number {
  const nth = Math.ceil(date.getDate() / 7)
  const isLast = date.getDate() + 7 > daysInMonth(date.getFullYear(), date.getMonth())
  return isLast ? -1 : nth
}

/** Monta a RRULE a partir do formulario, ou null quando o evento nao se repete. */
export function buildRrule(state: RecurrenceState, start: Date): string | null {
  if (state.preset === 'none') {
    return null
  }

  const parts: string[] = []
  const dayCode = WEEKDAY_CODES[start.getDay()]

  switch (state.preset) {
    case 'daily':
      parts.push('FREQ=DAILY')
      break
    case 'weekdays':
      parts.push('FREQ=WEEKLY', `BYDAY=${WEEKDAYS_ONLY.join(',')}`)
      break
    case 'weekly':
      parts.push('FREQ=WEEKLY', `BYDAY=${dayCode}`)
      break
    case 'monthly':
      parts.push('FREQ=MONTHLY')
      break
    case 'yearly':
      parts.push('FREQ=YEARLY')
      break
    case 'custom':
      parts.push(`FREQ=${state.freq}`)
      if (state.interval > 1) {
        parts.push(`INTERVAL=${state.interval}`)
      }
      if (state.freq === 'WEEKLY' && state.byDay.length > 0) {
        parts.push(`BYDAY=${orderWeekdays(state.byDay).join(',')}`)
      }
      if (state.freq === 'MONTHLY' && state.monthlyMode === 'dayOfWeek') {
        parts.push(`BYDAY=${weekdayOrdinal(start)}${dayCode}`)
      }
      break
  }

  if (state.ending === 'count') {
    parts.push(`COUNT=${Math.max(1, state.count)}`)
  } else if (state.ending === 'until' && state.until) {
    parts.push(`UNTIL=${state.until.replaceAll('-', '')}`)
  }
  return parts.join(';')
}

function orderWeekdays(codes: string[]): string[] {
  return WEEKDAY_CODES.filter((code) => codes.includes(code))
}

/** Le uma RRULE guardada e reconstroi o estado do formulario. */
export function parseRrule(rrule: string | null | undefined, start: Date): RecurrenceState {
  const state = defaultRecurrence(start)
  if (!rrule) {
    return state
  }

  const parts = new Map<string, string>()
  for (const part of rrule.replace(/^RRULE:/i, '').split(';')) {
    const [name, value] = part.split('=')
    if (name && value) {
      parts.set(name.toUpperCase(), value)
    }
  }

  const freq = (parts.get('FREQ') ?? 'WEEKLY') as Frequency
  const interval = Number(parts.get('INTERVAL') ?? 1)
  const byDay = parts.get('BYDAY')?.split(',') ?? []
  const hasOrdinal = byDay.some((code) => /^-?\d/.test(code))

  state.freq = freq
  state.interval = interval
  state.byDay = byDay.map((code) => code.replace(/^-?\d+/, ''))
  state.monthlyMode = freq === 'MONTHLY' && hasOrdinal ? 'dayOfWeek' : 'dayOfMonth'

  if (parts.has('COUNT')) {
    state.ending = 'count'
    state.count = Number(parts.get('COUNT'))
  } else if (parts.has('UNTIL')) {
    state.ending = 'until'
    const raw = parts.get('UNTIL')!.slice(0, 8)
    state.until = `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)}`
  }

  state.preset = detectPreset(freq, interval, byDay, hasOrdinal, start)
  return state
}

function detectPreset(
  freq: Frequency,
  interval: number,
  byDay: string[],
  hasOrdinal: boolean,
  start: Date,
): RecurrencePreset {
  if (interval !== 1) {
    return 'custom'
  }
  const startCode = WEEKDAY_CODES[start.getDay()]

  if (freq === 'DAILY' && byDay.length === 0) return 'daily'
  if (freq === 'YEARLY' && byDay.length === 0) return 'yearly'
  if (freq === 'MONTHLY') return hasOrdinal ? 'custom' : 'monthly'
  if (freq === 'WEEKLY') {
    if (sameSet(byDay, WEEKDAYS_ONLY)) return 'weekdays'
    if (byDay.length === 0 || sameSet(byDay, [startCode])) return 'weekly'
  }
  return 'custom'
}

function sameSet(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((value) => b.includes(value))
}

/** Frase curta em portugues descrevendo a repeticao, mostrada no formulario e no evento. */
export function describeRrule(rrule: string | null | undefined): string {
  if (!rrule) {
    return 'Nao se repete'
  }

  const parts = new Map<string, string>()
  for (const part of rrule.replace(/^RRULE:/i, '').split(';')) {
    const [name, value] = part.split('=')
    if (name && value) {
      parts.set(name.toUpperCase(), value)
    }
  }

  const freq = parts.get('FREQ') as Frequency | undefined
  const interval = Number(parts.get('INTERVAL') ?? 1)
  const byDay = parts.get('BYDAY')?.split(',') ?? []

  let text: string
  if (freq === 'DAILY') {
    text = interval === 1 ? 'Todo dia' : `A cada ${interval} dias`
  } else if (freq === 'WEEKLY') {
    const base = interval === 1 ? 'Toda semana' : `A cada ${interval} semanas`
    text = byDay.length > 0 ? `${base}, ${listWeekdays(byDay)}` : base
  } else if (freq === 'MONTHLY') {
    const base = interval === 1 ? 'Todo mes' : `A cada ${interval} meses`
    text = byDay.length > 0 ? `${base}, na ${describeOrdinalDay(byDay[0])}` : base
  } else if (freq === 'YEARLY') {
    text = interval === 1 ? 'Todo ano' : `A cada ${interval} anos`
  } else {
    text = 'Repeticao personalizada'
  }

  if (parts.has('COUNT')) {
    text += `, ${parts.get('COUNT')} vezes`
  } else if (parts.has('UNTIL')) {
    const raw = parts.get('UNTIL')!.slice(0, 8)
    text += `, ate ${raw.slice(6, 8)}/${raw.slice(4, 6)}/${raw.slice(0, 4)}`
  }
  return text
}

function listWeekdays(codes: string[]): string {
  const names = orderWeekdays(codes.map((code) => code.replace(/^-?\d+/, ''))).map(
    (code) => WEEKDAY_LONG[code] ?? code,
  )
  if (names.length === 1) return `as ${names[0]}s`
  return `as ${names.slice(0, -1).join(', ')} e ${names[names.length - 1]}`
}

function describeOrdinalDay(code: string): string {
  const match = /^(-?\d+)([A-Z]{2})$/.exec(code)
  if (!match) {
    return WEEKDAY_LONG[code] ?? code
  }
  const ordinal = Number(match[1])
  const name = WEEKDAY_LONG[match[2]] ?? match[2]
  const labels: Record<number, string> = { 1: 'primeira', 2: 'segunda', 3: 'terceira', 4: 'quarta' }
  return ordinal === -1 ? `ultima ${name}` : `${labels[ordinal] ?? `${ordinal}a`} ${name}`
}
