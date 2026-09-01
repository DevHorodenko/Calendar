import { useEffect, useRef, useState } from 'react'
import { hexToHsl, hslToHex, normalizeHex } from '../lib/color'
import type { Hsl } from '../lib/color'
import { EVENT_PRESETS } from '../types'

interface Props {
  value: string
  onChange: (hex: string) => void
}

/** Passo do teclado, em graus de matiz e pontos de saturacao. */
const KEY_STEP = 4

/**
 * Roda de cores: o angulo e a matiz, a distancia do centro e a saturacao, e a
 * luminosidade fica num controle proprio embaixo.
 *
 * O HSL vive aqui dentro e o hex e o que sai. Guardar o HSL em estado, em vez de
 * derivar do hex a cada render, evita que ida e volta entre os dois va deslocando
 * a cor alguns pontos a cada arrasto -- o arredondamento nao e exato.
 */
export default function ColorPicker({ value, onChange }: Props) {
  const [hsl, setHsl] = useState<Hsl>(() => hexToHsl(value))
  const [hexDraft, setHexDraft] = useState(value)
  const lastEmitted = useRef(value)
  const wheelRef = useRef<HTMLDivElement>(null)

  // Cor trocada por fora (um preset, o campo hex): realinha a roda com ela.
  useEffect(() => {
    if (value !== lastEmitted.current) {
      lastEmitted.current = value
      setHsl(hexToHsl(value))
      setHexDraft(value)
    }
  }, [value])

  function emit(next: Hsl) {
    setHsl(next)
    const hex = hslToHex(next)
    lastEmitted.current = hex
    setHexDraft(hex)
    onChange(hex)
  }

  /** Converte um ponto da roda em matiz e saturacao. */
  function pickFromPoint(clientX: number, clientY: number) {
    const wheel = wheelRef.current
    if (!wheel) {
      return
    }
    const bounds = wheel.getBoundingClientRect()
    const radius = bounds.width / 2
    const dx = clientX - (bounds.left + radius)
    const dy = clientY - (bounds.top + radius)
    // Fora da roda, gruda na borda em vez de ignorar o gesto.
    const distance = Math.min(Math.hypot(dx, dy), radius)
    const angle = (Math.atan2(dy, dx) * 180) / Math.PI

    emit({
      h: Math.round((angle + 360) % 360),
      s: Math.round((distance / radius) * 100),
      l: hsl.l,
    })
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    const moves: Record<string, Partial<Hsl>> = {
      ArrowRight: { h: (hsl.h + KEY_STEP) % 360 },
      ArrowLeft: { h: (hsl.h - KEY_STEP + 360) % 360 },
      ArrowUp: { s: Math.min(100, hsl.s + KEY_STEP) },
      ArrowDown: { s: Math.max(0, hsl.s - KEY_STEP) },
    }
    const move = moves[event.key]
    if (move) {
      event.preventDefault()
      emit({ ...hsl, ...move })
    }
  }

  function handleHexInput(raw: string) {
    setHexDraft(raw)
    const normalized = normalizeHex(raw)
    if (normalized) {
      lastEmitted.current = normalized
      setHsl(hexToHsl(normalized))
      onChange(normalized)
    }
  }

  // A roda acompanha a luminosidade escolhida, em vez de mentir mostrando
  // sempre o tom medio: no escuro ela escurece junto.
  const ring = Array.from({ length: 13 }, (_, index) => `hsl(${index * 30} 100% ${hsl.l}%)`).join(', ')
  const wheelBackground =
    `radial-gradient(circle closest-side, hsl(0 0% ${hsl.l}%), hsl(0 0% ${hsl.l}% / 0)),` +
    `conic-gradient(from 90deg, ${ring})`

  const angle = (hsl.h * Math.PI) / 180
  const markerLeft = 50 + Math.cos(angle) * (hsl.s / 2)
  const markerTop = 50 + Math.sin(angle) * (hsl.s / 2)

  return (
    <div className="color-picker">
      <div
        ref={wheelRef}
        className="color-wheel"
        style={{ background: wheelBackground }}
        role="slider"
        tabIndex={0}
        aria-label="Roda de cores: setas laterais mudam o matiz, verticais a saturacao"
        aria-valuetext={`matiz ${hsl.h} graus, saturacao ${hsl.s}%`}
        aria-valuenow={hsl.h}
        aria-valuemin={0}
        aria-valuemax={360}
        onKeyDown={handleKeyDown}
        onPointerDown={(event) => {
          event.currentTarget.setPointerCapture(event.pointerId)
          pickFromPoint(event.clientX, event.clientY)
        }}
        onPointerMove={(event) => {
          if (event.buttons === 1) {
            pickFromPoint(event.clientX, event.clientY)
          }
        }}
      >
        <span
          className="color-wheel__marker"
          style={{ left: `${markerLeft}%`, top: `${markerTop}%`, background: hslToHex(hsl) }}
        />
      </div>

      <div className="color-picker__controls">
        <div className="field">
          <label className="field__label" htmlFor="color-lightness">
            Luminosidade
          </label>
          <input
            id="color-lightness"
            className="color-slider"
            type="range"
            min={0}
            max={100}
            value={hsl.l}
            style={{
              ['--slider-track' as string]:
                `linear-gradient(to right, #000, hsl(${hsl.h} ${hsl.s}% 50%), #fff)`,
            }}
            onChange={(event) => emit({ ...hsl, l: Number(event.target.value) })}
          />
        </div>

        <div className="field">
          <label className="field__label" htmlFor="color-hex">
            Codigo
          </label>
          <input
            id="color-hex"
            className="color-hex"
            type="text"
            inputMode="text"
            spellCheck={false}
            maxLength={7}
            value={hexDraft}
            placeholder="#8f2f1d"
            onChange={(event) => handleHexInput(event.target.value)}
            onBlur={() => setHexDraft(value)}
          />
        </div>
      </div>

      <div className="swatches">
        {EVENT_PRESETS.map((preset) => (
          <button
            key={preset.value}
            type="button"
            className="swatch"
            aria-pressed={value.toLowerCase() === preset.value}
            aria-label={preset.label}
            title={preset.label}
            style={{ ['--chip' as string]: preset.value }}
            onClick={() => onChange(preset.value)}
          />
        ))}
      </div>
    </div>
  )
}
