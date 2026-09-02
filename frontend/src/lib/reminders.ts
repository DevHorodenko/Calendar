/**
 * Antecedencia dos lembretes.
 *
 * O backend guarda um numero de minutos e so. A tela mostra "2 horas" em vez de "120
 * minutos" porque e assim que se pensa num aviso, entao aqui se traduz de um para o
 * outro: para exibir, escolhe-se a maior unidade que divide o valor sem sobra.
 */
import type { Reminder } from '../types'

export type LeadUnit = 'minutes' | 'hours' | 'days'

export const LEAD_UNITS: ReadonlyArray<{ value: LeadUnit; label: string }> = [
  { value: 'minutes', label: 'minutos' },
  { value: 'hours', label: 'horas' },
  { value: 'days', label: 'dias' },
]

const MINUTES_IN: Record<LeadUnit, number> = {
  minutes: 1,
  hours: 60,
  days: 60 * 24,
}

/** Teto que o backend e o banco tambem cobram: 30 dias. */
export const MAX_MINUTES_BEFORE = 30 * 24 * 60

/** Antecedencia do primeiro lembrete que se adiciona. */
export const DEFAULT_MINUTES_BEFORE = 30

/** Teto de lembretes por evento, o mesmo que a API aceita. */
export const MAX_REMINDERS = 10

/** Marcadores que o backend troca pelos dados do evento na hora de enviar. */
export const MESSAGE_PLACEHOLDERS = [
  '{titulo}',
  '{data}',
  '{hora}',
  '{local}',
  '{descricao}',
  '{antecedencia}',
] as const

export interface Lead {
  amount: number
  unit: LeadUnit
}

/** Quebra os minutos na maior unidade que os divide sem sobra: 120 vira "2 horas". */
export function toLead(minutes: number): Lead {
  if (minutes > 0 && minutes % MINUTES_IN.days === 0) {
    return { amount: minutes / MINUTES_IN.days, unit: 'days' }
  }
  if (minutes > 0 && minutes % MINUTES_IN.hours === 0) {
    return { amount: minutes / MINUTES_IN.hours, unit: 'hours' }
  }
  return { amount: minutes, unit: 'minutes' }
}

export function toMinutes(lead: Lead): number {
  const minutes = Math.round(lead.amount) * MINUTES_IN[lead.unit]
  return Math.min(Math.max(minutes, 0), MAX_MINUTES_BEFORE)
}

/** Como a tela se refere a uma antecedencia: "30 minutos antes". */
export function describeLead(minutes: number): string {
  if (minutes <= 0) {
    return 'na hora'
  }
  return `${countAndUnit(minutes)} antes`
}

/**
 * O mesmo texto que o backend poe em {antecedencia} -- "em 30 minutos".
 *
 * Existe separado de describeLead porque as duas formas nao sao intercambiaveis: uma
 * rotula um campo, a outra entra no meio da frase que chega ao usuario. Manter esta
 * igual a do backend e o que faz o exemplo mostrado na tela ser o texto de verdade.
 */
export function leadPhrase(minutes: number): string {
  return minutes <= 0 ? 'agora' : `em ${countAndUnit(minutes)}`
}

function countAndUnit(minutes: number): string {
  const { amount, unit } = toLead(minutes)
  if (unit === 'minutes') {
    return `${amount} ${amount === 1 ? 'minuto' : 'minutos'}`
  }
  if (unit === 'hours') {
    return `${amount} ${amount === 1 ? 'hora' : 'horas'}`
  }
  return `${amount} ${amount === 1 ? 'dia' : 'dias'}`
}

export function newReminder(existing: Reminder[]): Reminder {
  return { minutesBefore: firstFreeLead(existing), message: '', enabled: true }
}

/**
 * Dois lembretes com a mesma antecedencia sao duas mensagens iguais seguidas, e a
 * tabela nem aceita o par repetido. Ao adicionar, pula para a proxima sugestao livre.
 */
const SUGGESTED_LEADS = [DEFAULT_MINUTES_BEFORE, 10, 60, 120, 24 * 60, 15, 5, 0]

function firstFreeLead(existing: Reminder[]): number {
  const taken = new Set(existing.map((reminder) => reminder.minutesBefore))
  const suggestion = SUGGESTED_LEADS.find((minutes) => !taken.has(minutes))
  if (suggestion !== undefined) {
    return suggestion
  }
  // Todas as sugestoes ocupadas: sobe de minuto em minuto ate achar um vago.
  let minutes = 1
  while (taken.has(minutes) && minutes < MAX_MINUTES_BEFORE) {
    minutes += 1
  }
  return minutes
}

/** Se ha antecedencia repetida, o backend descartaria uma delas sem avisar. */
export function findDuplicateLead(reminders: Reminder[]): number | null {
  const seen = new Set<number>()
  for (const reminder of reminders) {
    if (seen.has(reminder.minutesBefore)) {
      return reminder.minutesBefore
    }
    seen.add(reminder.minutesBefore)
  }
  return null
}
