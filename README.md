# Calendario

Aplicacao web de calendario pessoal com visoes de ano, mes, semana e dia, e cadastro de
eventos unicos e recorrentes.

## Stack

| Camada | Tecnologia |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Maven |
| Persistencia | H2 em arquivo, JPA/Hibernate, Flyway |
| Frontend | React 19, TypeScript, Vite |
| Recorrencia | RRULE da RFC 5545, expandida no backend |

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

O `.exe` precisa do **WiX**. O script procura o `candle.exe` do WiX 3.14 no PATH e em
`build/wix3`; sem ele, gera so a versao portatil e explica como obter os binarios --
que rodam de uma pasta, sem instalar nada e sem privilegio de administrador. O WiX 7
tambem funciona, mas exige aceitar o EULA da *Open Source Maintenance Fee*.

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

`from` e `to` sao `LocalDateTime` ISO sem fuso (`2026-09-01T00:00:00`); `to` e exclusivo.
A janela e limitada a 800 dias (`calendar.max-range-days`), o suficiente para a visao de ano.

Criar uma serie:

```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"title":"Academia","allDay":false,
       "startAt":"2026-09-02T09:00:00","endAt":"2026-09-02T10:00:00",
       "recurrenceRule":"FREQ=WEEKLY;BYDAY=MO,WE,FR","color":"#2f5c33"}'
```

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
├── domain/       Event e EventOverride
├── repository/   consultas por janela
├── service/      EventService: escopos de edicao e montagem das ocorrencias
└── web/          controller, DTOs e tratamento de erro

installer/
├── package-windows.ps1   jlink + jpackage, gera o .exe e a versao portatil
└── one-ring.ico          icone do aplicativo e do atalho

frontend/src/
├── lib/          dates (horario local flutuante), api, recurrence (RRULE <-> formulario)
├── components/   Toolbar, YearView, MonthView, TimeGridView, EventDialog, RecurrenceEditor
└── App.tsx       navegacao entre visoes e carga da janela
```

## Ideias para depois

- Arrastar e redimensionar eventos na visao de semana.
- Importar e exportar `.ics` (o modelo ja e RFC 5545).
- Lembretes e notificacoes.
- Login, caso o calendario deixe de ser so seu: hoje nao ha `user_id` em lugar nenhum,
  entao seria uma coluna nova em `event` e um filtro nas consultas.
