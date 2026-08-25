/**
 * Conversao entre hex e HSL para a roda de cores.
 *
 * A roda pensa em matiz, saturacao e luminosidade -- angulo, distancia do centro e
 * um controle a parte. O evento guarda hex, que e o que vai para o banco e para o
 * CSS. Estas funcoes fazem a ponte entre os dois.
 */

export interface Hsl {
  /** 0 a 360 */
  h: number
  /** 0 a 100 */
  s: number
  /** 0 a 100 */
  l: number
}

const HEX_PATTERN = /^#[0-9a-f]{6}$/i

export function isHexColor(value: string): boolean {
  return HEX_PATTERN.test(value.trim())
}

/**
 * Normaliza o que o usuario digitou: aceita com ou sem '#' e na forma curta de
 * tres digitos, para colar um valor de qualquer lugar nao virar erro.
 */
export function normalizeHex(value: string): string | null {
  let raw = value.trim().replace(/^#/, '')
  if (/^[0-9a-f]{3}$/i.test(raw)) {
    raw = raw
      .split('')
      .map((digit) => digit + digit)
      .join('')
  }
  return /^[0-9a-f]{6}$/i.test(raw) ? `#${raw.toLowerCase()}` : null
}

export function hslToHex({ h, s, l }: Hsl): string {
  const saturation = s / 100
  const lightness = l / 100
  const k = (n: number) => (n + h / 30) % 12
  const a = saturation * Math.min(lightness, 1 - lightness)
  const channel = (n: number) => {
    const value = lightness - a * Math.max(-1, Math.min(k(n) - 3, 9 - k(n), 1))
    return Math.round(255 * value)
      .toString(16)
      .padStart(2, '0')
  }
  return `#${channel(0)}${channel(8)}${channel(4)}`
}

export function hexToHsl(hex: string): Hsl {
  const normalized = normalizeHex(hex) ?? '#000000'
  const r = parseInt(normalized.slice(1, 3), 16) / 255
  const g = parseInt(normalized.slice(3, 5), 16) / 255
  const b = parseInt(normalized.slice(5, 7), 16) / 255

  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const delta = max - min
  const l = (max + min) / 2

  if (delta === 0) {
    return { h: 0, s: 0, l: Math.round(l * 100) }
  }

  const s = delta / (1 - Math.abs(2 * l - 1))
  let h: number
  if (max === r) {
    h = 60 * (((g - b) / delta) % 6)
  } else if (max === g) {
    h = 60 * ((b - r) / delta + 2)
  } else {
    h = 60 * ((r - g) / delta + 4)
  }

  return { h: Math.round((h + 360) % 360), s: Math.round(s * 100), l: Math.round(l * 100) }
}
