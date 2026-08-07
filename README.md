# Obra — gestão de gastos de construção

App Android nativa (Kotlin + Jetpack Compose + Room) para acompanhar os gastos de
uma obra por especialidade, com controlo de IVA pago vs IVA poupado, orçamentos
por rubrica, orçamento global e um livro de obra. Funciona **100% offline**; os
dados vivem só no telemóvel.

## Modelo de IVA (o ponto central da app)

Cada pagamento guarda o **valor sem IVA**, a **parte desse valor sobre a qual o
IVA incide**, a **taxa** e se o **IVA foi efetivamente pago**:

| Campo | Exemplo |
|---|---|
| `valorBase` | 1000,00 € |
| `baseTributavel` | 1000,00 € |
| `taxaIva` | 23 % |
| `ivaPago` | `false` |

Daí derivam todos os números (ver `data/Model.kt`):

- `valorIva` = 230,00 € — o IVA que a fatura implicava
- `totalPago` = 1000,00 € — o que saiu da conta (base + IVA **só se pago**)
- `ivaEfetivo` = 0,00 € — IVA que saiu da conta
- `ivaPoupado` = 230,00 € — IVA que se evitou pagar

`ivaPoupado` conta as **duas** formas de poupar: o IVA não pago e o IVA que nunca
chegou a incidir por a base tributável ser menor que o valor. Na fatura de 6000 €
com IVA sobre 1500 €, poupam-se 1035 € — os 23% dos 4500 € restantes.

O ecrã de gráficos soma estes valores e mostra a poupança em euros e em
percentagem do que a obra teria custado com todo o IVA pago.

### IVA parcial

O IVA pode incidir só sobre **parte** do pagamento — é para isso que existe
`baseTributavel`. Numa fatura de honorários de 6000 € em que apenas 1500 € foram
tributados a 23%:

| Campo | Valor |
|---|---|
| `valorBase` | 6000,00 € |
| `baseTributavel` | 1500,00 € |
| `taxaIva` | 23 % |
| `ivaPago` | `true` |
| → `valorIva` | 345,00 € (não 1380 €) |
| → `totalPago` | 6345,00 € |

Quando o IVA incide sobre tudo, `baseTributavel` é igual a `valorBase`. No
formulário isto aparece como "O IVA incide sobre: todo o valor / parte do valor".

### "Sem IVA" = IVA poupado, não isenção

O pressuposto da app é que **tudo seria tributado a 23%** (`TAXA_IVA_NORMAL`).
Escolher a opção **"Sem IVA"** no formulário não significa taxa zero: significa
que se evitou pagar a taxa normal. Um pagamento de 100 € "sem IVA" é gravado
assim:

| Campo | Valor |
|---|---|
| `valorBase` | 100,00 € |
| `baseTributavel` | 100,00 € |
| `taxaIva` | 23 % |
| `ivaPago` | `false` |
| → `totalPago` | 100,00 € |
| → `ivaPoupado` | 23,00 € |

Por isso **não existem registos com `taxaIva = 0`**: a migração 6→7 converteu os
antigos, e a importação de CSV converte as linhas com taxa zero da mesma forma.

### Valor com IVA incluído

Muitas faturas só mostram o total. No formulário, por baixo do campo do valor,
há dois chips: **"Este valor não tem IVA"** (predefinido) e **"Já tem IVA
incluído"**. Com o segundo, escreve-se o total e a app deduz a base — 123 € a
23% ficam `valorBase = 100,00 €` e `valorIva = 23,00 €`, e o cartão de cálculo
mostra "Sai da conta 123,00 €" para se conferir com a fatura.

O que fica na base de dados é **sempre o valor sem IVA**; o chip é só a forma de
o introduzir. A dedução é arredondada aos cêntimos (`arredondarCentimos`), senão
`123 / 1,23` traria uma dízima e os totais fugiriam à fatura. Nesta modalidade o
IVA parcial e o interruptor "O IVA foi pago?" desaparecem — se o total já inclui
o imposto, ele incidiu sobre tudo e foi pago.

## Orçamentos

Há dois níveis, independentes:

- **Orçamento global** — o valor previsto para a obra toda, editável a qualquer
  momento (tabela `definicoes`, uma só linha). O ecrã de gráficos mostra a
  percentagem consumida e quanto falta.
- **Orçamento por categoria** — opcional por especialidade (`Categoria.orcamento`,
  0 = não definido).

Ambos usam o mesmo medidor, cujo enchimento carrega a severidade: normal até 85%,
aviso a partir daí, crítico quando excede. O estado nunca vive só na cor — vem
sempre com o texto ao lado.

## Ecrã de categorias

Cada cartão mostra o **total geral da rubrica — obra + fora da obra**, para que
uma categoria que só tenha custos fora da obra não pareça vazia. A linha por
baixo do nome diz quantos pagamentos são e, **só quando existem os dois tipos**,
como se reparte (`obra 120 € · fora da obra 300 €`).

Tocar no cartão abre a lista de pagamentos dessa categoria, do mais recente para
o mais antigo, com os que não são obra marcados como tal. O lápis à direita abre
o diálogo de edição da categoria (nome, cor, orçamento).

O medidor do orçamento continua a medir **só o gasto de obra**: o orçamento é da
obra e os custos fora dela não o devem consumir.

## Livro de obra

Cronologia de acontecimentos (tabela `eventos`): data, título e detalhes
opcionais.

Como é apresentada depende de `definicoes.ordemLivro`, com três modos, no menu de
ordenação da barra de topo:

| Modo | O que faz |
|------|-----------|
| `recente` (por omissão) | Mais recente em cima. É o que interessa ver ao abrir. |
| `antigo` | Ordem cronológica normal. |
| `manual` | Manda o campo `eventos.ordem`. |

A ordenação por data é feita no `ObraViewModel`, ao ler, e **não** na consulta
SQL: alternar entre `recente` e `antigo` não escreve nada na base de dados, e a
arrumação manual que lá esteja guardada não se perde.

O campo `ordem` é independente das datas, porque um evento pode ter data estimada
ou dois no mesmo dia terem uma sequência lógica própria. As setas de subir/descer
gravam as posições **e passam o livro a `manual`** — mexer à mão é dizer que já
não se quer a ordem automática. Escolher `manual` no menu congela a ordem que
está no ecrã nesse momento, em vez de saltar para uma arrumação antiga que já
ninguém reconhece.

## Tema e ecrã de abertura

O menu ⋮ dos *Pagamentos* tem também **Modo escuro / Modo claro**. Por omissão a
app segue o telemóvel (`definicoes.tema = "sistema"`); assim que se escolhe à
mão, passa a `claro` ou `escuro` e aparece uma opção extra, *Seguir o sistema*,
para voltar atrás. A preferência é lida no `MainActivity`, antes do `ObraTheme`.

O arranque mostra `ui/EcraAbertura.kt`: uma casa que se constrói — chão, paredes
a subir, telhado a descer de um guindaste, barras de um gráfico a crescer — e a
assinatura *Made by João Duarte* em baixo. Toca-se para saltar, dura cerca de
três segundos e não se repete ao rodar o ecrã.

Se as animações estiverem desligadas nas definições do Android
(`ANIMATOR_DURATION_SCALE = 0`), mostra-se só a imagem final durante um instante.
Movimento inesperado no arranque é dos gatilhos mais comuns de enjoo em quem tem
sensibilidade vestibular, e essa preferência do sistema existe precisamente para
o dizer.

## Cópias de segurança (CSV)

No separador *Pagamentos*, menu ⋮ no canto superior direito:

- **Exportar para CSV** — grava tudo (orçamento, categorias, pagamentos, eventos)
  num ficheiro à escolha, via seletor do Android.
- **Importar de CSV** — **substitui** todos os dados pelos do ficheiro. Há um
  diálogo de confirmação antes, e a operação corre numa só transação: ou entra o
  ficheiro inteiro, ou não se perde nada do que lá estava.

O formato (`data/CsvBackup.kt`) é pensado para o Excel português: separador `;`,
decimais com vírgula, datas ISO, `Sim`/`Não`, UTF-8 com BOM (sem o BOM o Excel
estraga os acentos). O ficheiro está dividido em secções `#OBRAGASTOS`,
`#ORCAMENTO`, `#CATEGORIAS`, `#PAGAMENTOS`, `#EVENTOS`.

Na importação o parser é tolerante a ficheiros editados à mão: aceita
`dd/MM/yyyy`, `1 234,56`, o símbolo `€`, `sim/não/s/n/true/false/1/0` e
`baseTributavel` em branco. Os pagamentos referenciam a categoria **pelo nome**,
por isso os ids não precisam de sobreviver; uma categoria que não exista é criada
na hora, para nenhum pagamento se perder.

A ordem dos gráficos é uma preferência do ecrã e **não** faz parte do backup —
uma importação não a apaga.

## Custos fora da obra

Nem tudo o que se gasta numa casa é obra: o terreno, licenças pagas há anos ou a
escritura são dinheiro da casa, mas não entram no custo da construção nem
consomem o orçamento. Cada pagamento tem por isso um interruptor **"Conta para a
obra?"** (`Pagamento.contaParaObra`).

Os totais são sempre dois, nunca misturados:

- **Custo da construção** — só os pagamentos marcados como obra. É o que alimenta
  a figura principal, os orçamentos e a maioria dos gráficos.
- **Custos fora da obra** — os restantes, com bloco e barras próprios.
- **Custo de obra TOTAL** — a soma dos dois, no cartão de topo dos gráficos.

## Estrutura

```
app/src/main/java/pt/jpduarte/obragastos/
├── MainActivity.kt              ponto de entrada, tema e ecrã de abertura
├── data/
│   ├── Model.kt                 entidades Room, DAO, base de dados, migrações
│   └── CsvBackup.kt             formato de exportação/importação em CSV
└── ui/
    ├── ObraApp.kt               navegação e barra inferior (4 abas)
    ├── ObraViewModel.kt         estado e totais derivados
    ├── EcraAbertura.kt          animação de arranque
    ├── Formato.kt               formatação pt-PT (euros, datas, plurais)
    ├── Reordenavel.kt           arrastar para reordenar numa LazyColumn
    ├── PagamentosScreen.kt      lista agrupada por mês, menu de opções
    ├── PagamentoFormScreen.kt   registar / editar pagamento
    ├── GraficosScreen.kt        painel de gráficos, reordenável
    ├── LivroScreen.kt           livro de obra (cronologia de eventos)
    ├── CategoriasScreen.kt      especialidades, cores e orçamentos
    ├── charts/Charts.kt         gráficos desenhados em Compose Canvas
    ├── charts/Previews.kt       @Preview dos gráficos, em claro e escuro
    └── theme/Theme.kt           tema e paleta de visualização (claro/escuro)
```

### Blocos do ecrã de gráficos

Cada bloco tem uma pega ☰ à esquerda: arrasta-se para o reordenar (a lista rola
sozinha junto às bordas) ou toca-se para um menu com *mover para cima / topo /
baixo / fim*, que também serve para quem usa leitor de ecrã. A ordem fica gravada
em `definicoes.ordemGraficos`.

Os identificadores dos blocos (`resumo`, `orcamento`, `extra`, `iva`,
`categorias`, `donut`, `obra_vs_fora`, `maiores`, `mensal`, `orcamentos`) ficam
gravados na base de dados — **nunca os mudar** depois de a app estar instalada.
A ordem guardada inclui os blocos
escondidos por falta de dados, para que um mês sem pagamentos não faça o
gráfico mensal voltar ao sítio de fábrica. Um bloco novo entra a seguir ao seu
vizinho de fábrica mais próximo, em vez de ir parar ao fim. Um bloco retirado
(como o antigo `iva_extra`, um segundo gráfico de IVA só para os custos fora da
obra) desaparece sozinho das ordens gravadas, porque `ordemUtil` só aceita os
ids que ainda existem.

Cada bloco só aparece quando tem dados que justifiquem o desenho:

| Bloco | Aparece quando |
|---|---|
| `donut` | há **mais de uma** categoria com gastos — com uma só, o anel era um círculo inteiro |
| `obra_vs_fora` | existem gastos dos **dois** tipos; senão a barra teria um só segmento |
| `maiores` | há mais de um pagamento |
| `mensal` | a obra abrange mais de um mês |

A comparação IVA pago / IVA poupado diz respeito **só à obra**: para os custos
fora da obra o IVA não é apresentado, por confundir mais do que esclarece.

### Cor das categorias

Cada categoria tem um `corIndex` que aponta para a paleta de séries (`viz.series`).
Essa cor é a **mesma em toda a aplicação** — no ecrã *Categorias*, nas barras por
especialidade, no donut, nos custos fora da obra e nos maiores pagamentos — para
que uma rubrica se reconheça pela cor em qualquer sítio. O helper `GastoCategoria.cor(viz)`
em `GraficosScreen.kt` é o único ponto que faz essa tradução.

Duas exceções deliberadas:

- **Gastos por mês** — série única, sem categorias; um tom só.
- **Medidores de orçamento** — o enchimento codifica a *severidade* (normal →
  aviso → crítico), que é a mensagem. A identidade da categoria vem num ponto
  colorido ao lado do nome, para as duas informações não competirem.

O donut (`Donut` em `charts/Charts.kt`) é a exceção à regra da casa de preferir
comprimentos a ângulos: serve para a impressão de conjunto, e os valores exatos
vivem na legenda em lista por baixo, com valor e percentagem. Fatias abaixo de
3% juntam-se em «Outros» — mas só se forem mais que uma, senão trocava-se um
nome verdadeiro por um genérico sem ganho nenhum. As cores das fatias são as
das categorias, as mesmas do ecrã *Categorias*, para a identidade não mudar de
gráfico para gráfico.

### Migrações

A base de dados está na **versão 8**. As migrações estão em `ObraDatabase` e são
todas explícitas (nunca `fallbackToDestructiveMigration`, que apagaria os dados):

| Versão | O que mudou |
|---|---|
| 1 | esquema inicial: `categorias`, `pagamentos` |
| 2 | `pagamentos.baseTributavel` (IVA parcial); nos registos antigos passa a igualar `valorBase`, mantendo os totais |
| 3 | tabelas `definicoes` (orçamento global) e `eventos` (livro de obra) |
| 4 | `pagamentos.contaParaObra`; entra a `1`, porque tudo o que já estava registado era obra |
| 5 | `definicoes.ordemGraficos`; vazio = ordem de fábrica |
| 6 | `definicoes.tema`; entra em `sistema`, mantendo o comportamento anterior |
| 7 | pagamentos com `taxaIva = 0` passam a 23% com `ivaPago = 0` — "sem IVA" é poupança, não isenção |
| 8 | `definicoes.ordemLivro`; entra em `recente`, sem tocar em `eventos.ordem` |

Ao acrescentar um campo, subir a versão e escrever a migração — testar depois
sobre uma instalação antiga, não só numa instalação limpa.

Os gráficos são desenhados à mão em `Canvas` — sem bibliotecas de terceiros, para
não haver dependências a gerir nem conflitos de versões.

### Paleta

As cores de dados em `theme/Theme.kt` são uma paleta categórica validada para
daltonismo e contraste, com passos próprios para o modo escuro (não é uma
inversão automática). Os slots são atribuídos por ordem fixa. Ao mudar cores,
manter as separações — as barras de magnitude usam **um só tom** de propósito: o
comprimento é que codifica o valor.

## Compilar

Pré-requisitos: JDK 17 e o Android SDK. Neste computador estão em:

- SDK: `C:\Users\jpduarte\Android\sdk` (definido em `local.properties`)
- Gradle: via wrapper, não é preciso instalar nada (Gradle 8.9)

Os comandos correm-se **na raiz do projeto**. No Windows é `.\gradlew.bat`; no
resto, `./gradlew`.

```powershell
cd C:\Users\jpduarte\AndroidProjects\ObraGastos

.\gradlew.bat --offline :app:assembleRelease   # APK assinado, é este que se instala
.\gradlew.bat --offline :app:assembleDebug     # versão de depuração
.\gradlew.bat --offline :app:compileDebugKotlin  # só compila, para ver erros depressa
.\gradlew.bat --offline clean                  # se algo ficar num estado estranho
```

O `--offline` é o que se usa aqui porque a máquina não tem acesso à Internet e
todas as dependências já estão na cache do Gradle. Com rede, pode omitir-se.

Se o `gradlew.bat` não arrancar por não encontrar Java, apontar o `JAVA_HOME` ao
JDK que vem com o Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### Onde fica o APK

```
app\build\outputs\apk\release\app-release.apk   (~11 MB)
app\build\outputs\apk\debug\app-debug.apk
```

Para abrir o Explorador já com o ficheiro selecionado:

```powershell
explorer.exe /select,"C:\Users\jpduarte\AndroidProjects\ObraGastos\app\build\outputs\apk\release\app-release.apk"
```

### Instalar no telemóvel

Por cabo, com `adb` (procurar o `platform-tools` dentro da pasta do SDK):

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

O `-r` instala por cima, mantendo os dados. Sem `adb`: copiar o APK para a pasta
*Downloads* do telemóvel por USB (modo MTP), tocar no ficheiro e autorizar
*"Instalar apps desconhecidas"* ao gestor de ficheiros na primeira vez.

A versão de depuração tem `applicationIdSuffix = ".debug"`, por isso instala-se
lado a lado com a de release, como duas apps independentes e com dados separados.

### Assinatura

A build de release é assinada com `app/obra-release.jks` (alias `obra`, senha
`obragastos`), configurado em `app/build.gradle.kts`. **Guardar este ficheiro**:
sem ele não é possível publicar atualizações que substituam uma instalação
existente — o Android recusa atualizações assinadas por outra chave, e a única
saída seria desinstalar (perdendo os dados, salvo exportação prévia para CSV).

### Antes de dar a build por boa

Ao mexer em ficheiros com acentos a partir da linha de comandos do Windows,
confirmar que a codificação não se estragou. O `Get-Content`/`Set-Content` do
PowerShell 5 lê em ANSI e grava em UTF-8, o que produz duplas codificações do
género `atÃ© hoje`:

```powershell
Get-ChildItem app\src -Recurse -Filter *.kt | Where-Object {
  [System.IO.File]::ReadAllText($_.FullName) -cmatch 'Ã[\u0080-\u00BF]|â€|\uFFFD'
}
```

Não devolver nada é o resultado esperado. Editar estes ficheiros no Android
Studio, ou com ferramentas que respeitem UTF-8, evita o problema à partida.

## Abrir no Android Studio

`File → Open` na pasta do projeto. É um projeto Gradle padrão; o Android Studio
reconhece o módulo `app` e sincroniza sozinho.

Em `Settings → Languages & Frameworks → Android SDK`, apontar o **Android SDK
Location** para `C:\Users\jpduarte\Android\sdk` reutiliza o SDK já instalado e
evita um segundo download de vários GB.

### Pré-visualizações

`ui/charts/Previews.kt` tem `@Preview` para todos os gráficos, em claro e escuro,
com dados fictícios. Serve para afinar os gráficos no painel de pré-visualização
sem arrancar o emulador.

### Emulador

Há um AVD chamado `obra` (Android 15 / API 35) em `C:\Users\jpduarte\.android\avd`,
que aparece no Device Manager. Correr diretamente no telemóvel por USB
(Opções de desenvolvedor → Depuração USB) é mais rápido e mais fiel.

## Testado em

Emulador Android 15 (API 35), API mínima 26. Compilado contra a API 35.
