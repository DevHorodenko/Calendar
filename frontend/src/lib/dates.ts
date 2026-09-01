/**
 * Utilidades de data do calendario.
 *
 * O backend trabalha com horarios locais flutuantes (LocalDateTime, sem fuso), entao
 * aqui nunca se usa toISOString(): ele converteria para UTC e um evento das 09h viraria
 * 06h ou 12h dependendo de onde a maquina esta. Todo texto de data e montado campo a campo.
 */

/** Domingo. Trocar para 1 faz a semana comecar na segunda em todas as visoes. */
export const WEEK_STARTS_ON = 0

const pad = (value: number) => String(value).padStart(2, '0')

/** Formata uma data como "2026-08-25T14:30:00", que e o que o backend le. */
export function toLocalIso(date: Date): string {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

/** Le "2026-08-25T14:30:00" como horario local, sem deslocar por fuso. */
export function fromLocalIso(value: string): Date {
  const [datePart, timePart = '00:00:00'] = value.split('T')
  const [year, month, day] = datePart.split('-').map(Number)
  const [hour, minute, second] = timePart.split(':').map(Number)
  return new Date(year, month - 1, day, hour || 0, minute || 0, Math.trunc(second) || 0)
}

/** Valor aceito por <input type="datetime-local">. */
export function toInputValue(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** Valor aceito por <input type="date">. */
export function toDateInputValue(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * O formulario guarda data e hora numa string so ("2026-08-25T18:00"), mas os mostra
 * em dois campos. Estas funcoes leem e trocam cada metade sem desmontar o valor.
 *
 * Um campo esvaziado nao pode zerar a string inteira, senao o formulario fica num
 * estado que nao da para ler de volta: a metade que sumiu volta ao que era.
 */
export function datePartOf(value: string): string {
  return value.slice(0, 10)
}

export function timePartOf(value: string): string {
  return value.slice(11, 16)
}

export function withDatePart(value: string, date: string): string {
  return `${date || datePartOf(value)}T${timePartOf(value) || '00:00'}`
}

export function withTimePart(value: string, time: string): string {
  return `${datePartOf(value)}T${time || '00:00'}`
}

export function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

export function addDays(date: Date, days: number): Date {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}

export function addMonths(date: Date, months: number): Date {
  const result = new Date(date)
  const targetDay = result.getDate()
  result.setDate(1)
  result.setMonth(result.getMonth() + months)
  // Evita que 31 de janeiro + 1 mes vire 3 de marco.
  result.setDate(Math.min(targetDay, daysInMonth(result.getFullYear(), result.getMonth())))
  return result
}

export function addYears(date: Date, years: number): Date {
  const result = new Date(date)
  result.setFullYear(result.getFullYear() + years)
  return result
}

export function daysInMonth(year: number, month: number): number {
  return new Date(year, month + 1, 0).getDate()
}

export function startOfWeek(date: Date): Date {
  const result = startOfDay(date)
  const diff = (result.getDay() - WEEK_STARTS_ON + 7) % 7
  return addDays(result, -diff)
}

export function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

export function startOfYear(date: Date): Date {
  return new Date(date.getFullYear(), 0, 1)
}

export function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

export function isToday(date: Date): boolean {
  return isSameDay(date, new Date())
}

/** Chave estavel de um dia, usada para agrupar ocorrencias por data. */
export function dayKey(date: Date): string {
  return toDateInputValue(date)
}

export function minutesSinceMidnight(date: Date): number {
  return date.getHours() * 60 + date.getMinutes()
}

/** As 42 celulas da grade de um mes, incluindo a virada dos meses vizinhos. */
export function monthGridDays(reference: Date): Date[] {
  const first = startOfWeek(startOfMonth(reference))
  return Array.from({ length: 42 }, (_, index) => addDays(first, index))
}

/** Os dias de uma linha de semana. */
export function weekDays(reference: Date): Date[] {
  const first = startOfWeek(reference)
  return Array.from({ length: 7 }, (_, index) => addDays(first, index))
}

// ------------------------------------------------------------------ formatacao

const LOCALE = 'pt-BR'

const timeFormat = new Intl.DateTimeFormat(LOCALE, { hour: '2-digit', minute: '2-digit' })
const monthYearFormat = new Intl.DateTimeFormat(LOCALE, { month: 'long', year: 'numeric' })
const longDateFormat = new Intl.DateTimeFormat(LOCALE, {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
})
const shortDateFormat = new Intl.DateTimeFormat(LOCALE, { day: '2-digit', month: 'short' })

export const formatTime = (date: Date) => timeFormat.format(date)
export const formatMonthYear = (date: Date) => capitalize(monthYearFormat.format(date))
export const formatLongDate = (date: Date) => capitalize(longDateFormat.format(date))
export const formatShortDate = (date: Date) => shortDateFormat.format(date)

/** Nomes dos dias da semana ja na ordem em que a grade os desenha. */
export const WEEKDAY_LABELS = Array.from({ length: 7 }, (_, index) => {
  const sample = addDays(startOfWeek(new Date(2024, 0, 7)), index)
  return capitalize(new Intl.DateTimeFormat(LOCALE, { weekday: 'short' }).format(sample).replace('.', ''))
})

export const MONTH_LABELS = Array.from({ length: 12 }, (_, index) =>
  capitalize(new Intl.DateTimeFormat(LOCALE, { month: 'long' }).format(new Date(2024, index, 1))),
)

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1)
}

/** Titulo do periodo mostrado no cabecalho, conforme a visao ativa. */
export function periodTitle(view: string, date: Date): string {
  switch (view) {
    case 'year':
      return String(date.getFullYear())
    case 'month':
      return formatMonthYear(date)
    case 'week': {
      const days = weekDays(date)
      const first = days[0]
      const last = days[6]
      const sameMonth = first.getMonth() === last.getMonth()
      return sameMonth
        ? `${first.getDate()} a ${last.getDate()} de ${formatMonthYear(first)}`
        : `${formatShortDate(first)} a ${formatShortDate(last)} de ${last.getFullYear()}`
    }
    default:
      return formatLongDate(date)
  }
}
