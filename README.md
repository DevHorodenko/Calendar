# Calendario

Aplicacao web de calendario pessoal com visoes de ano, mes, semana e dia, e cadastro de
eventos unicos e recorrentes.

## Stack

| Camada | Tecnologia |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Maven |
| Persistencia | PostgreSQL 17, JPA/Hibernate, Flyway |
| Frontend | React 19, TypeScript, Vite |
| Recorrencia | RRULE da RFC 5545, expandida no backend |

Sem autenticacao: a aplicacao roda local e atende um usuario so.

## Rodando

Precisa de JDK 25, Maven, Node 20+ e Docker.

```bash
# 1. banco
docker compose up -d

# 2. backend (http://localhost:8080)
cd backend
mvn spring-boot:run

# 3. frontend (http://localhost:5173)
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

### Testes

```bash
cd backend && mvn test          # 24 testes do motor de recorrencia
cd frontend && npm run typecheck
```

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
usa `toISOString()`: ele monta as datas campo a campo em [`lib/dates.ts`](frontend/src/lib/dates.ts).

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
       "recurrenceRule":"FREQ=WEEKLY;BYDAY=MO,WE,FR","color":"green"}'
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
