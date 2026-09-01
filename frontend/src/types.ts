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

/**
 * Cor do evento em hex (#rrggbb). Era um conjunto fechado de nomes; virou cor livre
 * para a roda poder entregar qualquer valor. O backend recusa o que nao for hex.
 */
export type EventColor = string

export const DEFAULT_EVENT_COLOR = '#2b4c8c'

/** Atalhos para os esmaltes da heraldica, ao lado da roda. */
export const EVENT_PRESETS: ReadonlyArray<{ value: string; label: string }> = [
  { value: '#2b4c8c', label: 'Azur' },
  { value: '#2f5c33', label: 'Sinopla' },
  { value: '#a3781d', label: 'Ouro' },
  { value: '#9c2b21', label: 'Goles' },
  { value: '#6a3a6e', label: 'Purpura' },
  { value: '#2b6b6b', label: 'Verdemar' },
]
