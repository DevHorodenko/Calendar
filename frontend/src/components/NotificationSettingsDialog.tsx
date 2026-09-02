import { useEffect, useState } from 'react'
import { ApiError, api } from '../lib/api'
import type { ChannelTestResult } from '../types'

interface Props {
  onClose: () => void
  /** Avisa a tela principal se algum canal passou a ter -- ou deixou de ter -- como enviar. */
  onSaved: (ready: boolean) => void
}

const BOTFATHER = 'https://t.me/BotFather'

export default function NotificationSettingsDialog({ onClose, onSaved }: Props) {
  const [telegramEnabled, setTelegramEnabled] = useState(false)
  const [botToken, setBotToken] = useState('')
  const [tokenSet, setTokenSet] = useState(false)
  const [chatId, setChatId] = useState('')
  const [windowsEnabled, setWindowsEnabled] = useState(true)
  const [windowsAvailable, setWindowsAvailable] = useState(false)

  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [results, setResults] = useState<ChannelTestResult[] | null>(null)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const settings = await api.notifications.settings()
        if (cancelled) {
          return
        }
        setTelegramEnabled(settings.telegramEnabled)
        setTokenSet(settings.telegramTokenSet)
        setChatId(settings.telegramChatId ?? '')
        setWindowsEnabled(settings.windowsEnabled)
        setWindowsAvailable(settings.windowsAvailable)
      } catch (failure) {
        if (!cancelled) {
          setError(failure instanceof ApiError ? failure.message : 'Nao foi possivel ler os ajustes.')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  /** Grava e devolve se deu certo, para o teste e a deteccao encadearem sem repetir isto. */
  async function persist(): Promise<boolean> {
    setError(null)
    setNotice(null)
    setResults(null)
    try {
      const saved = await api.notifications.save({
        telegramEnabled,
        telegramBotToken: botToken,
        telegramChatId: chatId,
        windowsEnabled,
      })
      setTokenSet(saved.telegramTokenSet)
      setChatId(saved.telegramChatId ?? '')
      setWindowsAvailable(saved.windowsAvailable)
      // O token gravado nunca volta do servidor; o campo se esvazia para nao dar a
      // impressao de que o que esta ali e o que esta guardado.
      setBotToken('')
      onSaved(saved.ready)
      return true
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'Nao foi possivel gravar os ajustes.')
      return false
    }
  }

  async function handleSave() {
    setBusy(true)
    if (await persist()) {
      onClose()
    }
    setBusy(false)
  }

  /** Grava antes de testar: o envio usa o que esta no banco, nao o que esta na tela. */
  async function handleTest() {
    setBusy(true)
    if (await persist()) {
      try {
        setResults((await api.notifications.test()).results)
      } catch (failure) {
        setError(failure instanceof ApiError ? failure.message : 'Nao foi possivel enviar o teste.')
      }
    }
    setBusy(false)
  }

  /** Grava o token e pergunta ao Telegram quem falou com o bot. */
  async function handleDetectChat() {
    setBusy(true)
    if (await persist()) {
      try {
        const chat = await api.notifications.detectChat()
        if (chat.found) {
          setChatId(chat.chatId ?? '')
          setNotice(`Conversa encontrada: ${chat.name}. Salve para confirmar.`)
        } else {
          setNotice(
            'O bot ainda nao recebeu nenhuma mensagem. Mande /start para ele no Telegram e tente de novo.',
          )
        }
      } catch (failure) {
        setError(failure instanceof ApiError ? failure.message : 'Nao foi possivel detectar a conversa.')
      }
    }
    setBusy(false)
  }

  // Ligado mas sem credencial: gravar continua valendo -- o que ja foi preenchido fica
  // guardado --, mas nada sai ate a ficha fechar, e calar sobre isso faria o Salvar
  // parecer que resolveu.
  const missingToken = telegramEnabled && !tokenSet && botToken.trim() === ''
  const missingChat = telegramEnabled && chatId.trim() === ''

  return (
    <div className="overlay" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label="Notificacoes"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal__header">
          <h2 className="modal__title">Notificacoes</h2>
          <button type="button" className="btn btn--ghost btn--icon" onClick={onClose} aria-label="Fechar">
            &#10005;
          </button>
        </div>

        <div className="modal__body">
          {error && <div className="form-error">{error}</div>}
          {notice && <div className="form-notice">{notice}</div>}

          {results && (
            <div className={results.every((r) => r.ok) ? 'form-notice' : 'form-error'}>
              {results.map((result) => (
                <div key={result.channel}>
                  {result.ok ? '✓' : '✗'} {result.channel}: {result.detail}
                </div>
              ))}
            </div>
          )}

          {loading ? (
            <p className="modal__hint">Carregando...</p>
          ) : (
            <>
              {/* -------------------------------------------------------- Telegram */}
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={telegramEnabled}
                  onChange={(event) => setTelegramEnabled(event.target.checked)}
                />
                Avisar pelo Telegram (celular e computador)
              </label>

              <div className="field">
                <label className="field__label" htmlFor="telegram-token">
                  Token do bot
                </label>
                <input
                  id="telegram-token"
                  type="password"
                  value={botToken}
                  autoComplete="off"
                  placeholder={tokenSet ? 'Ja gravado -- preencha so para trocar' : 'Ex.: 8123456789:AAE...'}
                  onChange={(event) => setBotToken(event.target.value)}
                />
              </div>

              <div className="field">
                <label className="field__label" htmlFor="telegram-chat">
                  Conversa
                </label>
                <div className="field__row">
                  <input
                    id="telegram-chat"
                    type="text"
                    value={chatId}
                    placeholder="Detecte depois de falar com o bot"
                    onChange={(event) => setChatId(event.target.value)}
                  />
                  <button
                    type="button"
                    className="btn field__row-action"
                    onClick={handleDetectChat}
                    disabled={busy || (!tokenSet && botToken.trim() === '')}
                  >
                    Detectar
                  </button>
                </div>
                <p className="modal__hint">
                  O numero da conversa nao aparece em lugar nenhum do Telegram. Mande{' '}
                  <code>/start</code> para o seu bot e clique em Detectar.
                </p>
              </div>

              {(missingToken || missingChat) && (
                <div className="form-notice">
                  {missingToken
                    ? 'Falta o token do bot.'
                    : 'Falta a conversa: mande /start para o bot e clique em Detectar.'}{' '}
                  Pode salvar assim mesmo -- o que ja esta preenchido fica guardado --, mas os
                  avisos do Telegram so comecam a sair quando faltar nada.
                </div>
              )}

              <div className="recurrence-box">
                <strong>Como criar o bot</strong>
                <ol className="steps">
                  <li>
                    No Telegram, fale com{' '}
                    <a href={BOTFATHER} target="_blank" rel="noreferrer">
                      @BotFather
                    </a>{' '}
                    e mande <code>/newbot</code>.
                  </li>
                  <li>Escolha um nome e um usuario terminado em "bot". Ele devolve o token.</li>
                  <li>
                    Cole o token acima, abra a conversa com o seu bot, mande <code>/start</code> e
                    clique em Detectar.
                  </li>
                </ol>
                <p className="modal__hint">
                  O bot e seu: a API do Telegram e oficial, gratuita e sem limite de cadastro.
                  Instale o Telegram tambem no computador e o mesmo aviso chega nos dois.
                </p>
              </div>

              {/* --------------------------------------------------------- Windows */}
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={windowsEnabled}
                  disabled={!windowsAvailable}
                  onChange={(event) => setWindowsEnabled(event.target.checked)}
                />
                Mostrar o balao de notificacao do Windows
              </label>
              <p className="modal__hint">
                {windowsAvailable
                  ? 'Aparece na area de trabalho mesmo com o Telegram fechado, e nao depende de internet.'
                  : 'Indisponivel nesta execucao: o balao vem do icone da bandeja, que so existe no aplicativo instalado.'}
              </p>
            </>
          )}
        </div>

        <div className="modal__footer">
          <button type="button" className="btn" onClick={handleTest} disabled={busy || loading}>
            Enviar teste
          </button>
          <div className="toolbar__spacer" />
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancelar
          </button>
          <button type="button" className="btn btn--primary" onClick={handleSave} disabled={busy || loading}>
            {busy ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </div>
    </div>
  )
}
