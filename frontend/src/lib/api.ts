import { toLocalIso } from './dates'
import type { EditScope, EventRequest, Occurrence } from '../types'

const BASE = '/api/events'

/** Erro vindo da API, ja com a mensagem que o backend escreveu no ProblemDetail. */
export class ApiError extends Error {
  readonly status: number
  readonly fields?: Record<string, string>

  constructor(status: number, message: string, fields?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fields = fields
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })
  } catch {
    throw new ApiError(0, 'Nao foi possivel falar com o servidor. Ele esta rodando na porta 8080?')
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = await response.json()
    return new ApiError(
      response.status,
      problem.detail ?? problem.message ?? `Erro ${response.status}`,
      problem.fields,
    )
  } catch {
    return new ApiError(response.status, `Erro ${response.status}`)
  }
}

/** Monta a query de escopo usada ao editar ou apagar parte de uma serie. */
function scopeQuery(scope: EditScope, occurrenceStart?: Date): string {
  const params = new URLSearchParams({ scope })
  if (occurrenceStart) {
    params.set('occurrenceStart', toLocalIso(occurrenceStart))
  }
  return `?${params}`
}

export const api = {
  /** Ocorrencias entre from (inclusivo) e to (exclusivo). */
  occurrences(from: Date, to: Date): Promise<Occurrence[]> {
    const params = new URLSearchParams({ from: toLocalIso(from), to: toLocalIso(to) })
    return request<Occurrence[]>(`${BASE}?${params}`)
  },

  create(event: EventRequest): Promise<unknown> {
    return request(BASE, { method: 'POST', body: JSON.stringify(event) })
  },

  update(id: string, event: EventRequest, scope: EditScope, occurrenceStart?: Date): Promise<unknown> {
    return request(`${BASE}/${id}${scopeQuery(scope, occurrenceStart)}`, {
      method: 'PUT',
      body: JSON.stringify(event),
    })
  },

  remove(id: string, scope: EditScope, occurrenceStart?: Date): Promise<void> {
    return request<void>(`${BASE}/${id}${scopeQuery(scope, occurrenceStart)}`, { method: 'DELETE' })
  },
}
