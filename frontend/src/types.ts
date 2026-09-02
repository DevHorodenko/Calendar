/** Como o backend enxerga uma edicao dentro de uma serie recorrente. */
export type EditScope = 'THIS' | 'THIS_AND_FUTURE' | 'ALL'

export type CalendarView = 'year' | 'month' | 'week' | 'day'

/**
 * Um aviso preso ao evento, enviado antes de ele comecar.
 *
 * O aviso pertence a serie, e nao a uma ocorrencia: quem se repete toda terca tem os
 * mesmos lembretes em todas as tercas. A mensagem vazia deixa o backend montar o texto
 * padrao a partir do proprio evento.
 */
export interface Reminder {
  /** Ausente enquanto o lembrete so existe no formulario. */
  id?: string
  minutesBefore: number
  message?: string
  enabled: boolean
}

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
  reminders: Reminder[]
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
  reminders: Reminder[]
}

/**
 * Por onde os avisos saem, como a tela recebe.
 *
 * O token nunca volta do servidor; `telegramTokenSet` diz apenas que existe um gravado,
 * o bastante para a tela mostrar o campo como ja preenchido.
 */
export interface NotificationSettings {
  telegramEnabled: boolean
  telegramTokenSet: boolean
  telegramChatId?: string
  windowsEnabled: boolean
  /** Se existe bandeja nesta instalacao. Falso em desenvolvimento, sem area de trabalho. */
  windowsAvailable: boolean
  /** Algum canal ligado e completo. E o que diz se um aviso tem como sair. */
  ready: boolean
  updatedAt?: string
}

/** O que se envia ao gravar. O token em branco mantem o que ja esta la. */
export interface NotificationSettingsInput {
  telegramEnabled: boolean
  telegramBotToken: string
  telegramChatId: string
  windowsEnabled: boolean
}

/** Como foi a mensagem de teste em cada canal ligado. */
export interface ChannelTestResult {
  channel: string
  ok: boolean
  detail: string
}

/** A conversa que o bot encontrou, ou `found: false` se ninguem falou com ele ainda. */
export interface TelegramChat {
  found: boolean
  chatId?: string
  name?: string
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
