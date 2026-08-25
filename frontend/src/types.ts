/** Como o backend enxerga uma edicao dentro de uma serie recorrente. */
export type EditScope = 'THIS' | 'THIS_AND_FUTURE' | 'ALL'

export type CalendarView = 'year' | 'month' | 'week' | 'day'

/** Uma aparicao concreta de um evento na tela. */
export interface Occurrence {
  seriesId: string
  /** Horario original do encaixe na serie: junto com seriesId, identifica a ocorrencia. */
  occurrenceStart: string
  startAt: string
  endAt: string
  allDay: boolean
  title: string
  description?: string
  location?: string
  color: EventColor
  recurring: boolean
  modified: boolean
  recurrenceRule?: string
}

/** O que se envia ao criar ou editar um evento. */
export interface EventRequest {
  title: string
  description?: string
  location?: string
  allDay: boolean
  startAt: string
  endAt: string
  recurrenceRule?: string | null
  color: EventColor
}

export const EVENT_COLORS = ['blue', 'green', 'amber', 'red', 'violet', 'teal'] as const
export type EventColor = (typeof EVENT_COLORS)[number]

export const COLOR_LABELS: Record<EventColor, string> = {
  blue: 'Azul',
  green: 'Verde',
  amber: 'Ambar',
  red: 'Vermelho',
  violet: 'Violeta',
  teal: 'Turquesa',
}
