# Arquitetura — Automação TRC Mobile ↔ CF Obras

> Robô Java que extrai o relatório diário de frequência de catraca do TRC Mobile,
> garante o cadastro de empreiteiras e funcionários no CF Obras e sobe o relatório
> no módulo Serviços > Ponto Catraca.

## 1. Visão geral

O sistema é um **pipeline de execução única**: roda, processa o dia, encerra.
Não é aplicação web — não há servidor HTTP nem interface.

```
TRC Mobile ──(Selenium)──► Excel ──(POI)──► Reconciliação ──► Cadastro ──► Upload CF Obras
                                                 │                              ▲
                                                 ▼                              │
                                        Cache (Postgres/Neon) ─────────────────┘
```

## 2. Fluxo do pipeline

1. **Extração (TRC):** login → seleciona obra → Relatório 03.1 Frequência-Catraca
   (data = dia atual) → exportar Excel → renomear arquivo com obra + data.
2. **Leitura do Excel:** extrai a lista **distinta** de empreiteiras e, por
   empreiteira, os funcionários distintos. O robô não interpreta batidas de
   ponto — isso é responsabilidade do módulo Ponto Catraca do CF Obras.
3. **Reconciliação:** para cada empreiteira/funcionário do relatório, consulta o
   cache local. Desconhecido → busca CNPJ/CPF e dados completos nas telas de
   Cadastro do TRC e grava no cache.
4. **Cadastro:** o que não existe no CF Obras é cadastrado automaticamente
   (fluxo Extract → Map → Submit), respeitando as regras de negócio.
5. **Upload:** login no CF Obras → Serviços > Ponto Catraca → seleciona obra →
   sobe o .xlsx → vincula cada empreiteira detectada à cadastrada.
6. **Log de execução:** toda rodada registra o que fez, o que cadastrou e o que
   falhou.

Ordem essencial: **o cadastro acontece depois de gerar o relatório e antes de
subir o relatório** — o relatório diz quem precisa existir; o upload exige que
já existam.

## 3. Estrutura de pacotes

```
src/main/java/com/cury/automacaocatraca/
│
├── config/          # Configuração e infraestrutura de partida
│   ├── ObraConfig            # POJO: nomes/ids da obra no TRC e no CF Obras
│   ├── ObrasRegistry         # lista de obras a processar (config externa)
│   └── WebDriverFactory      # cria ChromeDriver configurado (download dir, etc.)
│
├── domain/          # O que o sistema SABE (entidades e valores)
│   ├── EmpreiteiraCache      # entidade JPA — cache de identidade por CNPJ
│   ├── FuncionarioCache      # entidade JPA — cache de identidade por CPF
│   ├── ExecucaoLog           # entidade JPA — auditoria de cada rodada
│   └── mapper/CadastroMapper # traduz dado bruto do TRC → formato CF Obras
│
├── trc/             # Tudo que SÓ conhece o TRC Mobile
│   ├── TrcAuthenticator      # login
│   ├── TrcReportExtractor    # gera e exporta o relatório 03.1
│   ├── TrcEmployeeExtractor  # lê telas de Cadastro (empreiteira/funcionário)
│   └── TrcFileManager        # localiza e renomeia o .xlsx baixado
│
├── cfobras/         # Tudo que SÓ conhece o CF Obras
│   ├── CfObrasAuthenticator  # login
│   ├── PontoCatracaPage      # navegação e upload em Serviços > Ponto Catraca
│   ├── EmpreiteiraMatcher    # DECIDE: existe? mapeia ou marca p/ cadastro
│   └── CadastroService       # EXECUTA: cadastra empreiteira/funcionário
│
├── excel/           # Infraestrutura genérica de leitura
│   └── ExcelReportReader     # Apache POI — parse do relatório de frequência
│
└── orchestration/
    └── AutomacaoPipeline     # a maestrina: única classe que conhece todos
```

**Regras de dependência (as mais importantes do projeto):**

- `trc/` e `cfobras/` **nunca se importam mutuamente**. Quem liga os dois é o
  `AutomacaoPipeline`.
- `domain/` não conhece Selenium nem Spring MVC — só dados e regras.
- `EmpreiteiraMatcher` (decisão) é separado de `CadastroService` (execução):
  decisão é testável sem navegador; execução é Selenium puro.
- `excel/` é genérico: ler planilha não é "coisa do TRC".

## 4. Camada de dados

- **Banco:** PostgreSQL gerenciado no **Neon** (projeto próprio, separado de
  outros sistemas). Acesso via Spring Data JPA / Hibernate, `ddl-auto: update`
  na fase de desenvolvimento.
- **Papel do banco:** *cache de identidade* + auditoria. A fonte de verdade dos
  cadastros é o CF Obras; o cache evita reconsultar os dois sites a cada rodada.
- **Chaves:** toda entidade usa chave sintética (`id` autogerado). CNPJ/CPF são
  colunas únicas — chaves naturais não viram primary key, porque a empreiteira
  entra no cache (pelo nome do relatório) antes de o CNPJ ser conhecido.
- **Nomes:** o relatório do TRC traz nomes com encoding sujo e capitalização
  inconsistente. Por isso toda entidade de identidade guarda o **nome cru**
  (auditoria) e o **nome normalizado** (comparação: maiúsculas, sem acento,
  espaços colapsados). Comparação NUNCA usa o nome cru.

## 5. Leitura do relatório (contrato do ExcelReportReader)

- Layout **posicional**, sem cabeçalho de colunas: dados começam na linha 7;
  colunas A=obra, B=empreiteira, C=funcionário, D=data, E=hora, F=identificador
  da catraca, G=tipo (Entrada/Saída). Lê até a primeira linha com A vazia.
- **Fail fast:** valida na largada que o layout é o esperado (A7 preenchida,
  G7 ∈ {Entrada, Saída}). Layout diferente → aborta com erro claro, nunca
  importa dados suspeitos em silêncio.
- Saída: empreiteiras distintas + funcionários distintos por empreiteira,
  nomes crus preservados.

## 6. Navegação (Selenium)

- **Duas instâncias de WebDriver**, uma por site — isolamento de falha e de
  estado (cookies, downloads). Como o fluxo é sequencial, o driver do TRC pode
  ser fechado antes de abrir o do CF Obras.
- Ciclo de vida controlado pelo pipeline via `WebDriverFactory`: quem cria o
  recurso é responsável por destruí-lo.
- Fase atual: execução **local (Windows), navegador visível, disparo manual**.
  Headless/servidor e agendamento são etapas futuras, depois de o robô ser
  confiável.

## 7. Configuração e segredos

- Segredos (Neon, login TRC, login CF Obras) vivem no arquivo **`.env`** na
  raiz do projeto, **fora do Git** (`.gitignore`).
- O `application.yml` importa o `.env` via `spring.config.import` e referencia
  tudo por `${VARIAVEL}` — o yml versionado não contém segredo nenhum.
- Blocos customizados `trc:` e `cfobras:` no yml alimentarão classes
  `@ConfigurationProperties` consumidas pelos authenticators.

## 8. Regras de negócio (resumo — detalhe em regras-negocio.md)

- Telefone ausente no TRC → `(00) 00000-0000` no CF Obras.
- Email ausente → `primeironome@ultimonome.com` (do nome completo do TRC).
- Nível de acesso derivado da função (nome normalizado), nesta ordem:
  contém "proprietario" ou "gerente" → **Gestor/Proprietário**;
  contém "encarregado" → **Encarregado**; qualquer outra → **Funcionário**.
  Toda atribuição de Gestor e toda função desconhecida ficam registradas no
  log de execução para auditoria.
- CCISA188 Incorporadora (SPE da obra) não é exceção: entra no fluxo normal de
  cadastro como qualquer empreiteira.

## 9. Pendências conhecidas

- Padrão para campos obrigatórios do CF Obras sem fonte no TRC:
  **Cor de Identificação** e **Valor Unitário (R$)** no cadastro de
  empreiteira (decidir antes de implementar o CadastroService).
- Tabela de funções reais do TRC → validar a regra de nível na prática.
- Agendamento (hoje disparo manual) e execução headless em servidor.