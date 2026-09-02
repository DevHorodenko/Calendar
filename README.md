# Calendario

Aplicacao web de calendario pessoal com visoes de ano, mes, semana e dia, cadastro de
eventos unicos e recorrentes, e lembretes no Telegram e na area de trabalho antes de o
evento comecar.

## Stack

| Camada | Tecnologia |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Maven |
| Persistencia | H2 em arquivo, JPA/Hibernate, Flyway |
| Frontend | React 19, TypeScript, Vite |
| Recorrencia | RRULE da RFC 5545, expandida no backend |
| Avisos | Telegram (API de bots) e balao nativo do Windows, por um agendador no backend |

Sem autenticacao: a aplicacao roda local e atende um usuario so.

## Instalando

O jeito mais simples de usar: rode `installer\package-windows.ps1` e depois
`build\dist\Calendario-1.0.exe`. Nao ha nada a instalar alem disso -- o Java vai
embutido e o banco e um arquivo. Veja [Empacotamento](#empacotamento).

## Rodando em desenvolvimento

Precisa de JDK 25, Maven e Node 20+. Nao precisa de Docker nem de banco instalado.

```bash
# backend (http://localhost:8080), banco em backend/data/
cd backend
mvn spring-boot:run

# frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

Abra <http://localhost:5173>. O Vite encaminha `/api` para o backend, entao o navegador
enxerga tudo na mesma origem.

O Maven precisa rodar sobre o JDK 25. Se o seu `JAVA_HOME` aponta para outra versao:

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-25.0.1.8-hotspot" mvn spring-boot:run
```

### Empacotamento

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot"
.\installer\package-windows.ps1
```

Sai em `build/dist/`:

| Artefato | Tamanho | O que e |
| --- | --- | --- |
| `Calendario-1.0.exe` | ~86 MB | Instalador: atalho no Menu Iniciar e desinstalacao pelo Windows |
| `Calendario/` | ~107 MB | Versao portatil: descompactar e abrir, sem instalar |

O script faz tres coisas: embute o frontend compilado no jar (um processo so serve
interface e API), monta um runtime Java so com os modulos usados via `jlink`
(~54 MB, contra ~291 MB do JDK inteiro) e junta tudo com `jpackage`.

O `.exe` precisa do **WiX**. Do JDK 24 em diante o `jpackage` so aceita o `wix.exe` do
WiX 4 ou mais novo (`dotnet tool install --global wix`); o WiX 3, que rodava de uma pasta
com `candle.exe`, nao serve mais. Sem WiX utilizavel o script gera so a versao portatil e
explica o que falta. O WiX 7 recusa qualquer comando ate a EULA da *Open Source
Maintenance Fee* ser aceita uma vez (`wix eula accept wix7`) -- uma decisao de
licenciamento de quem empacota; quem preferir evita-la ficando no WiX 5.

### Atualizando depois de mexer no codigo

O instalador copia o aplicativo para `C:\Program Files\Calendario`. Reempacotar atualiza
so `build/dist`; para a versao instalada mudar, rode o instalador de novo.

**E preciso subir o numero da versao.** O Windows Installer nao reinstala uma versao que ja
esta na maquina: ele reconhece o produto, sai sem fazer nada e sem avisar -- o instalador
parece simplesmente nao abrir, deixando processos parados que seguram o arquivo. O
`--win-upgrade-uuid` permite a substituicao, mas so entra em acao quando a versao muda:

```powershell
.\installer\package-windows.ps1 -AppVersion 1.0.1
```

O script avisa quando a versao pedida ja esta instalada.

Os dados nao sao tocados: banco e log ficam em `%LOCALAPPDATA%\Calendario`, fora da pasta
de instalacao. A versao portatil e a instalada usam o mesmo banco -- rode uma de cada vez,
senao a segunda perde a porta fixa.

### O aplicativo instalado

E um aplicativo de bandeja: o processo fica residente e a janela e descartavel.

- **A janela nao tem abas nem barra de endereco.** E uma janela do Edge ou do Chrome em
  modo aplicativo (`--app=`), com perfil proprio em `%LOCALAPPDATA%\Calendario\janela`,
  para ganhar lugar separado na barra de tarefas em vez de se misturar a navegacao.
- **Fechar a janela nao encerra o aplicativo.** Ele continua na bandeja; quem encerra e
  o "Sair" do menu. Abrir e fechar a janela quantas vezes quiser nao custa nada.
- **So existe uma janela.** Acionar a bandeja com a janela ja aberta traz ela para frente
  em vez de abrir outra. O Java nao enxerga janelas no Windows, entao quem responde
  "existe janela?" e o script [`focus-window.ps1`](backend/src/main/resources/scripts/focus-window.ps1),
  que enumera as janelas visiveis e exige **titulo exato** e **processo dono sendo um
  navegador**.

  Essa exigencia nao e preciosismo. O caminho curto seria o `AppActivate`, mas ele casa
  por *prefixo* de titulo: com uma pasta do Explorer aberta em `C:\Program Files\Calendario`
  -- cujo titulo de janela e exatamente "Calendario" -- ele da positivo, o aplicativo
  conclui que ja havia janela e **nunca abre nenhuma**. Detectar e focar tambem sao
  problemas separados: focar pode falhar (o Windows barra troca de primeiro plano vinda
  de processo de segundo plano), e essa falha nao pode virar "abra outra janela".
- **Abrir o aplicativo duas vezes nao cria uma segunda instancia.** Quando a porta fixa ja
  esta ocupada, o aplicativo confere pelo manifesto se quem responde ali e outro
  Calendario; se for, mostra a janela dele e encerra, em vez de subir um segundo servidor
  numa porta sorteada -- o que quebraria o atalho do aplicativo instalado.
- **O icone na bandeja e o ponto de ativacao**: clique duplo ou "Abrir calendario".
  No Windows 11 ele comeca escondido em "Mostrar icones ocultos"; arraste para fixar.
- A porta e fixa (**17325**), porque o aplicativo instalado guarda a URL de partida e uma
  porta sorteada a cada abertura deixaria esse atalho apontando para o vazio. Se algo ja
  ocupa a porta, o aplicativo cede e usa uma sorteada -- continua funcionando pela
  bandeja, e o log avisa que o atalho instalado nao vale nesta sessao.
- Banco e log ficam em `%LOCALAPPDATA%\Calendario\`, fora de Program Files, para gravar
  sem permissao de administrador. Backup e copiar `calendario.mv.db`.

Um componente de navegador embutido renderizaria pior: a interface usa `color-mix()` em
21 lugares e `conic-gradient` na roda de cores, que motores antigos como o WebView do
JavaFX ignoram. Alem disso, janela embutida morre junto com o processo -- o contrario do
que um aplicativo de bandeja precisa.

### Icone proprio na barra de tarefas

Por padrao a janela mostra o icone do Edge. Isso nao e ajustavel de fora: o icone vem do
**AppUserModelID** da janela, e como ela pertence ao `msedge.exe`, o Windows usa o icone
dele. Quem carrega identidade propria e um site *instalado* como aplicativo.

A instalacao **nao pode ser feita pela janela do aplicativo**: ela nao tem barra de
endereco nem menu, so os botoes de minimizar, maximizar e fechar. Precisa ser numa
janela normal do Edge.

Uma vez:

1. Abra <http://localhost:17325> numa janela comum do Edge (com o aplicativo rodando).
2. Na barra de endereco, clique no icone de instalacao a direita -- ele aparece porque
   existe o manifesto. Sem ele: **`...`** -> **Aplicativos** ->
   **Instalar este site como um aplicativo**.
3. Confirme o nome **Calendario**.

Ou, mais simples: **"Instalar como aplicativo"** no menu da bandeja, que abre a janela
certa e explica o que clicar.

O Edge cria um aplicativo de verdade, com icone do anel e entrada propria na barra de
tarefas. Dai em diante a bandeja abre por ele.

**O Edge nao cria atalho no Menu Iniciar.** O que ele deixa e
`<perfil>/Web Applications/_crx__<id>/Calendario.ico` dentro dos dados do navegador, e e
por esse rastro que o aplicativo descobre a instalacao -- o id e gerado na maquina, entao
nao da para fixa-lo no codigo. Achado o id, a janela abre com
`msedge --profile-directory=<perfil> --app-id=<id>`.

Duas armadilhas nesse caminho, ambas ja tratadas:

- A pasta vem como `_crx__<id>`, com uma barra baixa **a mais**. Um id com esse caractere
  extra nao da erro: o navegador simplesmente nao abre nada.
- Por isso o aplicativo confere se a janela apareceu de fato, e cai no modo `--app=`
  quando nao aparece. Sem essa conferencia, acionar a bandeja ficaria sem efeito nenhum.

O nome e o icone vem de [`manifest.webmanifest`](frontend/public/manifest.webmanifest),
servido por um endpoint proprio ([`ManifestController`](backend/src/main/java/org/horodenko/calendar/web/ManifestController.java)):
como recurso estatico ele sairia com o tipo errado e o Edge poderia descartar o manifesto,
perdendo justamente o nome e o icone.

### Iniciar junto com o Windows

> **Atencao:** durante a instalacao, o Edge oferece iniciar o aplicativo junto com o
> Windows e cria um atalho proprio em `Startup`. Esse atalho abre **so a janela**, que
> aponta para `http://localhost:17325` -- se o Calendario nao estiver rodando, ela abre
> numa pagina de erro. Quem precisa iniciar com o Windows e o Calendario, nao a janela.
> Se voce marcou essa opcao, desmarque em `edge://apps` e use o atalho abaixo.

O aplicativo aceita `calendar.open-on-start=false` para subir calado, so na bandeja, sem
abrir a janela. E o que a entrada de inicializacao automatica deve usar:

```powershell
# O instalador coloca o app aqui; com a versao portatil, aponte para build\dist\Calendario.
$alvo = "C:\Program Files\Calendario\Calendario.exe"
$atalho = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup\Calendario.lnk"
$s = (New-Object -ComObject WScript.Shell).CreateShortcut($atalho)
$s.TargetPath = $alvo
$s.Arguments = '--calendar.open-on-start=false'
$s.WorkingDirectory = Split-Path $alvo -Parent
$s.IconLocation = "$alvo,0"
$s.Save()
```

Assim o Calendario sobe na bandeja e a janela abre quando voce pedir -- e, por ja estar
no ar, ela abre no aplicativo instalado, com o icone certo.

### Testes

```bash
cd backend && mvn test          # 24 testes do motor de recorrencia
cd frontend && npm run typecheck
```

O banco de desenvolvimento fica em `backend/data/calendario.mv.db`. Apagar o arquivo
recomeca do zero, com as migrations rodando de novo.

## Como os eventos sao modelados

O ponto central do projeto e a diferenca entre **serie** e **ocorrencia**.

- Uma linha em `event` e uma **serie**. Sem `recurrence_rule` ela acontece uma vez so;
  com a regra preenchida, `start_at`/`end_at` descrevem a primeira ocorrencia e a duracao
  entre eles se repete nas demais.
- As **ocorrencias** nao ficam no banco. Elas sao geradas na hora da consulta, para a
  janela que a tela pediu. Uma serie "toda segunda, para sempre" ocupa uma linha, e nao
  uma linha por semana ate o fim dos tempos.
- `event_override` guarda as excecoes: a segunda em que a reuniao foi cancelada, ou aquela
  em que ela mudou de horario. A excecao aponta para a ocorrencia pelo horario **original**,
  que nao muda quando ela e remarcada.

Os horarios sao locais e flutuantes (`LocalDateTime`, sem fuso). Um evento marcado para as
14h continua as 14h quando o horario de verao entra ou sai. E por isso que o frontend nunca
usa `toISOString()`: ele monta as datas campo a campo em [`lib/dates.ts`](installer/
├── package-windows.ps1   jlink + jpackage, gera o .exe e a versao portatil
└── one-ring.ico          icone do aplicativo e do atalho

frontend/src/lib/dates.ts).

### Editar uma serie

Toda edicao e exclusao carrega um escopo, como no Google Agenda:

| Escopo | O que acontece |
| --- | --- |
| `THIS` | Cria uma excecao em `event_override` para aquela ocorrencia. |
| `THIS_AND_FUTURE` | Encerra a serie original com `UNTIL` na vespera do corte e cria uma serie nova a partir dali. |
| `ALL` | Altera ou apaga a serie inteira. |

## Recorrencia

As regras sao guardadas como RRULE da RFC 5545, e o parser
([`RecurrenceRule`](backend/src/main/java/org/horodenko/calendar/recurrence/RecurrenceRule.java))
cobre o subconjunto que um calendario pessoal usa:

`FREQ` (DAILY, WEEKLY, MONTHLY, YEARLY), `INTERVAL`, `COUNT`, `UNTIL`, `BYDAY`
(com ordinais como `2FR` ou `-1SU`), `BYMONTHDAY` (negativos contam do fim do mes),
`BYMONTH` e `WKST`.

Partes fora dessa lista (`BYSETPOS`, `BYWEEKNO`, `BYYEARDAY`...) sao **recusadas na leitura**
com HTTP 400, em vez de aceitas e expandidas errado em silencio.

A expansao ([`RecurrenceExpander`](backend/src/main/java/org/horodenko/calendar/recurrence/RecurrenceExpander.java))
sempre parte do inicio da serie, e nao do inicio da janela consultada, porque `COUNT` conta
ocorrencias desde a primeira. Um teto de 20.000 periodos protege contra regras que gerariam
uma varredura sem fim.

Exemplos que os testes cobrem:

```
FREQ=WEEKLY;BYDAY=MO,WE,FR          segunda, quarta e sexta
FREQ=MONTHLY;BYDAY=-1FR             ultima sexta de cada mes
FREQ=MONTHLY;BYMONTHDAY=-1          ultimo dia de cada mes
FREQ=MONTHLY                        mesmo dia do mes, pulando os meses que nao tem dia 31
FREQ=DAILY;INTERVAL=3;COUNT=4       de tres em tres dias, quatro vezes
FREQ=YEARLY;BYMONTH=3,9;BYMONTHDAY=10   10 de marco e 10 de setembro, todo ano
```

## Lembretes

Cada evento pode ter ate dez avisos. Um aviso e um par -- **quanto tempo antes** e **o que
dizer** -- e pertence a serie: quem se repete toda terca leva os mesmos avisos em todas as
tercas, cada uma com o seu envio.

A mensagem em branco usa um texto padrao montado a partir do evento. Escrevendo uma
mensagem propria, estes marcadores sao trocados na hora do envio:

```
{titulo}  {data}  {hora}  {local}  {descricao}  {antecedencia}
```

Assim uma serie nao precisa de um texto por ocorrencia: `Consulta {data} as {hora}` serve
para todas.

### Os dois canais

| Canal | Alcance | Precisa de |
| --- | --- | --- |
| Telegram | celular e qualquer maquina com a conta aberta | um bot seu (token) e a conversa |
| Notificacao do Windows | a area de trabalho desta maquina | nada -- so o aplicativo instalado |

Eles se complementam de proposito. O Telegram te alcanca longe do computador; o balao
aparece na sua frente mesmo com o Telegram fechado e sem depender de internet. Ligando os
dois, o mesmo lembrete sai pelos dois, e cada um guarda o proprio registro de envio -- um
Telegram que falhou nao fica escondido atras de um balao que apareceu.

O balao so existe onde ha icone na bandeja, ou seja, no aplicativo instalado. Rodando em
desenvolvimento, a tela de ajustes mostra a caixa desabilitada explicando isso.

### Criando o bot do Telegram

No botao **Avisos** da barra superior:

1. No Telegram, fale com [@BotFather](https://t.me/BotFather) e mande `/newbot`.
2. Escolha um nome e um usuario terminado em `bot`. Ele devolve o token.
3. Cole o token, abra a conversa com o seu bot, mande `/start` e clique em **Detectar**.

O passo 3 existe porque o id da conversa e um numero que a interface do Telegram nao mostra
em lugar nenhum. Em vez de mandar caçar esse numero num terceiro bot, o backend consulta o
`getUpdates` do seu proprio bot e grava o chat de quem falou com ele por ultimo.

Da para salvar so o token e voltar depois com a conversa: a ficha pela metade e gravada de
boa vontade. O que nao fica pela metade e o envio -- enquanto faltar token ou conversa, nada
sai, e a tela diz isso.

O token fica na tabela `notification_settings` do proprio banco do usuario, junto com os
eventos, e nunca volta para a tela: a API responde apenas se existe um gravado.

> **Por que nao WhatsApp?** Mandar texto livre para o WhatsApp sem conta comercial so era
> possivel por servicos como o CallMeBot, e cada bot deles tem um teto de usuarios: cheio,
> para de emitir chaves sem dizer nada -- nem erro, nem resposta. A API oficial da Meta
> exige conta Business e template aprovado, o que mataria a mensagem livre por evento. O
> Telegram e oficial, gratuito, sem fila, e o bot e seu.

### Como o disparo funciona

Nao ha fila nem tarefa agendada por evento. A cada meio minuto o `ReminderDispatcher`
pergunta quais ocorrencias comecam dentro da maior antecedencia configurada e, entre elas,
quais ja passaram do horario de aviso. Uma serie recorrente nao precisa de nada agendado de
antemao, e mudar o horario de um evento nao deixa aviso orfao para tras -- a proxima volta
simplesmente pergunta de novo.

Cada envio grava uma linha em `reminder_delivery`, com a chave `reminder_id` +
`occurrence_start` + `channel`. E o que impede a repeticao: a ocorrencia continua dentro da
janela ate comecar, entao sem esse registro a mesma mensagem sairia a cada volta. Uma falha
e reenviada nas voltas seguintes ate `calendar.reminders.max-attempts`, e depois desiste --
insistir num token errado de meio em meio minuto nao conserta nada.

Dois limites que valem saber:

- **Os avisos so saem com o Calendario aberto.** Ele mora na bandeja do Windows justamente
  para continuar de pe com a janela fechada; com o computador desligado, nada e enviado. Um
  aviso que venceu nesse meio tempo sai assim que o app volta, desde que o evento ainda nao
  tenha comecado.
- **Aviso atrasado nao sai.** Passados cinco minutos do inicio da ocorrencia, o lembrete
  perdeu a graca e e descartado.

Os ajustes ficam em `calendar.reminders` no `application.yml` (intervalo da varredura,
tentativas, retencao do historico, tempo limite do HTTP). `REMINDERS_ENABLED=false`
desliga o agendador por completo.

### Trocando de canal

`NotificationChannel` e uma interface, e o Spring recolhe todas as implementacoes. Um canal
novo -- e-mail, ntfy, webhook do Discord -- e uma classe que responde `isReady()` e
`send()`, mais os campos dele nos ajustes. Nem o agendamento, nem a deduplicacao, nem a
montagem da mensagem mudam. Foi essa separacao que permitiu trocar o WhatsApp pelo Telegram
sem tocar em nada disso.

## API

Base: `http://localhost:8080/api/events`

| Metodo | Rota | O que faz |
| --- | --- | --- |
| `GET` | `/?from=&to=` | Ocorrencias da janela, ja expandidas e com as excecoes aplicadas. |
| `GET` | `/series` | As series como estao guardadas, sem expandir. |
| `GET` | `/series/{id}` | Uma serie. |
| `POST` | `/` | Cria um evento. |
| `PUT` | `/{id}?scope=&occurrenceStart=` | Edita conforme o escopo. |
| `DELETE` | `/{id}?scope=&occurrenceStart=` | Apaga conforme o escopo. |

Notificacoes, em `http://localhost:8080/api/settings/notifications`:

| Metodo | Rota | O que faz |
| --- | --- | --- |
| `GET` | `/` | Ajustes atuais. O token nao volta; so `telegramTokenSet`. |
| `PUT` | `/` | Grava. `telegramBotToken` em branco mantem o que ja esta la. |
| `POST` | `/test` | Manda uma conferencia por cada canal ligado, com um resultado por canal. |
| `POST` | `/telegram/detect-chat` | Le o `getUpdates` do bot e grava a conversa encontrada. |

`from` e `to` sao `LocalDateTime` ISO sem fuso (`2026-09-01T00:00:00`); `to` e exclusivo.
A janela e limitada a 800 dias (`calendar.max-range-days`), o suficiente para a visao de ano.

Criar uma serie:

```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"title":"Academia","allDay":false,
       "startAt":"2026-09-02T09:00:00","endAt":"2026-09-02T10:00:00",
       "recurrenceRule":"FREQ=WEEKLY;BYDAY=MO,WE,FR","color":"#2f5c33",
       "reminders":[{"minutesBefore":30,"message":"Academia {data} as {hora}"}]}'
```

Os avisos viajam junto com o evento, e valem para a serie inteira qualquer que seja o
`scope` da edicao. Omitir `reminders` num `PUT` apaga os que existiam -- e a lista enviada
que passa a valer, campo como qualquer outro.

Cancelar uma unica ocorrencia:

```bash
curl -X DELETE "http://localhost:8080/api/events/$ID?scope=THIS&occurrenceStart=2026-09-09T09:00:00"
```

Cada ocorrencia devolvida traz `seriesId` + `occurrenceStart`. Esse par identifica a
ocorrencia e e o que se manda de volta para editar ou apagar so ela.

Erros vem como `ProblemDetail` (RFC 9457), com `detail` em portugues e, na falha de
validacao, um mapa `fields` campo a campo.

## Estrutura

```
backend/src/main/java/org/horodenko/calendar/
├── recurrence/   RecurrenceRule (parser) e RecurrenceExpander (expansao)
├── domain/       Event, EventOverride, EventReminder e o registro de envio por canal
├── repository/   consultas por janela
├── service/      EventService (escopos de edicao) e os ajustes de notificacao
├── notification/ disparador, montagem da mensagem e os canais (Telegram, bandeja)
└── web/          controllers, DTOs e tratamento de erro

installer/
├── package-windows.ps1   jlink + jpackage, gera o .exe e a versao portatil
└── one-ring.ico          icone do aplicativo e do atalho

frontend/src/
├── lib/          dates (horario local flutuante), api, recurrence, reminders (antecedencia)
├── components/   Toolbar, YearView, MonthView, TimeGridView, EventDialog, RecurrenceEditor,
│                 ReminderEditor, NotificationSettingsDialog
└── App.tsx       navegacao entre visoes e carga da janela
```

## Ideias para depois

- Arrastar e redimensionar eventos na visao de semana.
- Importar e exportar `.ics` (o modelo ja e RFC 5545).
- Mais canais de aviso: e-mail, ntfy, webhook -- a interface `NotificationChannel` ja
  espera por eles.
- Login, caso o calendario deixe de ser so seu: hoje nao ha `user_id` em lugar nenhum,
  entao seria uma coluna nova em `event` e um filtro nas consultas.
