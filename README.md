# Obra — gestão de gastos de construção

App Android nativa (Kotlin + Jetpack Compose + Room) para acompanhar os gastos de
uma obra por especialidade, com controlo de IVA pago vs IVA poupado, orçamentos
por rubrica, orçamento global, um livro de obra com fotos e as despesas da casa
que não são construção (crédito, seguros, contas). Funciona **100% offline**; os
dados vivem só no telemóvel.

## Modelo de IVA (o ponto central da app)

Cada pagamento guarda o **valor sem IVA**, a **parte desse valor sobre a qual o
IVA incide**, a **taxa** e se o **IVA foi efetivamente pago**. Os montantes
guardam-se em **cêntimos** (`valorBaseCent`, `baseTributavelCent`, desde a versão
11 da base de dados); as tabelas abaixo mostram-nos em euros:

| Campo | Exemplo |
|---|---|
| `valorBase` | 1000,00 € |
| `baseTributavel` | 1000,00 € |
| `taxaIva` | 23 % |
| `ivaPago` | `false` |

Daí derivam todos os números (ver `data/Model.kt`). O IVA de cada fatura é
**arredondado ao cêntimo**, como na própria fatura, e só depois somado — as somas
fazem-se sempre em cêntimos e só o resultado passa a euros, para os totais não
fugirem um cêntimo ao extrato:

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

Os orçamentos ficam em euros (`Double`): são estimativas, não se somam fatura a
fatura.

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

Ao eliminar uma categoria com pagamentos, o diálogo pergunta o que lhes fazer:
**passá-los para outra categoria** (a escolha por omissão) ou apagá-los também.
Sem isto o `ON DELETE CASCADE` levava-os sem perguntar.

## Fornecedor e "Desfazer"

Cada pagamento tem um campo **Fornecedor** opcional; o formulário sugere os já
usados, do mais frequente para o menos, para o mesmo nome não aparecer escrito de
três maneiras. Alimenta o gráfico *Maiores fornecedores* (que junta grafias que
só diferem em maiúsculas e espaços).

## Origem dos fundos

Cada pagamento diz de onde veio o dinheiro: **Pessoal** (poupanças, conta
própria — a escolha por omissão), **Bancário** (crédito do banco) ou **N/A** (não
se aplica — coisas que não se quer contabilizar na comparação), em
`Pagamento.origemFundos` (`pessoal` / `bancario` / `na`). Um pagamento N/A fica
fora do gráfico da origem dos fundos, mas **continua** nos totais da obra e nos
outros gráficos; o gráfico diz quanto ficou de fora. Na lista dos pagamentos
aparecem marcados com "· bancário" ou "· origem N/A". Alimenta o gráfico *Origem dos
fundos*: em cima, todo o dinheiro da casa (obra **e** fora da obra — o terreno é
muitas vezes pago pelo crédito) dividido entre pessoal e bancário; em baixo, só a
obra, mês a mês, em colunas empilhadas.

Eliminar um pagamento ou uma despesa já não pede confirmação: a lista mostra
"Desfazer" durante uns segundos, e o registo volta com o mesmo id.

## Despesas da casa

O separador **Despesas** é para o dinheiro da casa que não é construção:
prestações e comissões do banco, seguros, água, luz, impostos (tabela `despesas`).
Fica totalmente à parte dos pagamentos — não entra no custo da obra nem no
orçamento, e não tem IVA poupado (uma comissão bancária nem leva IVA; contá-la
como "poupei 23%" seria falso).

| Campo | O que é |
|---|---|
| `rubrica` | texto livre; o formulário sugere as de `RUBRICAS_DESPESA` e as já usadas |
| `valorCent` | o que saiu da conta |
| `capitalCent` | numa prestação do crédito, a parte que amortizou capital |

O número principal do separador é o **custo real** (`valorCent - capitalCent`).
A amortização de capital **não é custo**: é devolver o empréstimo que já pagou o
empreiteiro, e somá-la contava o mesmo dinheiro duas vezes. Os juros, comissões,
seguros e contas, esses, são dinheiro perdido. A separação capital/juros só
aparece na rubrica *Prestação do crédito*; o valor vem no extrato do crédito.

O botão **Ver gráficos**, por baixo do cartão do custo real, abre um ecrã à parte
(`DespesasGraficosScreen.kt`) com o custo real por rubrica, o custo real por mês e,
para as prestações com o capital indicado, a divisão **juros vs capital** mês a
mês. Respeita a rubrica escolhida no filtro da lista, e pode mudar-se lá. Estes
gráficos **não** aparecem no separador Gráficos, que é só da obra (o bloco
`despesas` que lá esteve desapareceu sozinho das ordens gravadas).

### Despesas recorrentes

Uma despesa recorrente — a prestação, a comissão de manutenção, a internet, o
seguro anual, com valor fixo ou variável — marca-se no formulário com **"Repete"**
e escolhe-se como: **todos os meses, de 2 em 2, de 3 em 3, de 6 em 6 meses, todos
os anos**, ou **Personalizado…**, onde se define "repetir a cada N dias / semanas
/ meses / anos" e quando **termina** (nunca, numa data, ou depois de N vezes,
contando a primeira) — num painel que sobe do fundo, a partir da data de
vencimento. O dia do mês das repetições mensais e anuais é o dessa data; no ecrã
das recorrentes muda-se em **"Próximo vencimento"**, e a recorrente recomeça aí.
A própria despesa é a primeira vez. Fica uma linha na tabela `despesas_fixas`
(o nome ficou de quando só havia repetição mensal), e as despesas que ela lança
levam o `fixaId` (sem chave estrangeira: deixar de repetir não apaga o que já
foi lançado).

Cada recorrente guarda a data de início, o índice da próxima vez (`proximo`) e
quantas já lançou (`lancadas`). A data da vez k calcula-se sempre a partir do
início, e não da anterior — um débito ao dia 31 volta ao dia 31 depois de
fevereiro. A app **lança sozinha** as que já venceram ao abrir e ao entrar no
separador Despesas (`lancamentosEmFalta` em `data/Recorrencia.kt`), nunca a mesma
vez duas vezes; se a app não for aberta durante meses, lança-as todas de uma vez.
Nas repetições **mensais e anuais**, um débito ao sábado ou domingo passa para
**segunda-feira**, como fazem os bancos; nas diárias e semanais fica no dia
escolhido (de propósito: "todos os dias" acumularia três à segunda). Os feriados
não contam.

- **Valor fixo**: entra com esse valor, já confirmada.
- **Valor variável** (luz, água, prestação de taxa variável): entra com o último
  valor conhecido e a marca **"a confirmar"** (`despesas.aConfirmar`). Um aviso
  no topo da lista diz quantas há e filtra-as; abrir uma e carregar em
  **Confirmar** grava o valor verdadeiro, que passa a ser a referência da vez
  seguinte.

O botão **Recorrentes** do separador Despesas mostra-as todas (ecrã "Despesas
recorrentes"; no código continuam a chamar-se `DespesaFixa`), com o próximo débito, e
deixa mudar o valor e a repetição (com o mesmo editor do formulário), **pausar**
(ao retomar recomeça no próximo débito, sem recuperar os que passaram em pausa)
ou **deixar de repetir**. Mudar a repetição recomeça a contar a partir do
próximo débito que estava previsto (`comRegra`).

"Repetir no mês seguinte" (no formulário de uma despesa existente) abre uma cópia
com a data um mês à frente — é o caso da prestação e das contas mensais.

A cor de cada rubrica sai de `slotRubrica`: as sugeridas têm slot fixo pela ordem
da lista, as inventadas seguem-se por ordem alfabética.

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

### Fotos

Cada evento pode ter fotos, da galeria ou da câmara (`EventoObra.fotos`, nomes
separados por `|`). Uma foto de telemóvel tem 3 a 8 MB; ao entrar é **reduzida**
(lado maior com 1600 px, JPEG a 80%, endireitada pelo EXIF) e fica com uns 200 a
300 KB — o original não é tocado e continua na galeria. O topo do livro mostra
quantas fotos há e quanto ocupam.

Os ficheiros vivem em `files/fotos/` (ver `data/Fotos.kt`) e entram na cópia de
segurança do Android (`backup_rules.xml`, `data_extraction_rules.xml`). Fotos
juntadas num diálogo que acaba cancelado são apagadas; as retiradas a um evento
só se apagam quando se guarda; apagar um evento apaga as suas fotos.

O CSV, sendo texto, **não leva as fotos** — só os nomes dos ficheiros. Importar
no mesmo telemóvel volta a ligá-las aos eventos; noutro telemóvel os eventos
entram sem fotos.

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
  ficheiro inteiro, ou não se perde nada do que lá estava. Antes de substituir,
  o estado atual é gravado em `files/copias/antes-de-importar-<data>.csv` (ficam
  as cinco mais recentes), e a mensagem de sucesso traz **Desfazer**, que repõe
  essa cópia.

O formato (`data/CsvBackup.kt`) é pensado para o Excel português: separador `;`,
decimais com vírgula, datas ISO, `Sim`/`Não`, UTF-8 com BOM (sem o BOM o Excel
estraga os acentos). O ficheiro está dividido em secções `#OBRAGASTOS`,
`#ORCAMENTO`, `#CATEGORIAS`, `#PAGAMENTOS`, `#EVENTOS`, `#DESPESAS`.

O formato está na **versão 5**: a 5 juntou a origem dos fundos (`Pessoal`/
`Bancário`/`N/A`) no fim de `#PAGAMENTOS` — sem ela o pagamento entra como pessoal; a 4 juntou às recorrentes a unidade, o
intervalo, o início, a contagem e o máximo de vezes (os ficheiros da 3, só
mensais, continuam a ler-se a partir do último mês lançado); a 3 trouxe `#DESPESAS_FIXAS` e, no fim de
`#DESPESAS`, a chave da fixa que lançou cada despesa e a marca "a confirmar". A 2
trouxe `#DESPESAS`, a área de construção em
`#ORCAMENTO`, o fornecedor no fim de `#PAGAMENTOS` e os nomes das fotos no fim de
`#EVENTOS`. Colunas novas vão sempre para o fim e têm valor por omissão, por isso
os ficheiros da versão 1 continuam a importar-se (há um teste para isso).

Na importação o parser é tolerante a ficheiros editados à mão: aceita
`dd/MM/yyyy`, `1 234,56`, o símbolo `€`, `sim/não/s/n/true/false/1/0` e
`baseTributavel` em branco. Os pagamentos referenciam a categoria **pelo nome**,
por isso os ids não precisam de sobreviver; uma categoria que não exista é criada
na hora, para nenhum pagamento se perder.

A ordem e a escolha dos gráficos são preferências do ecrã e **não** fazem parte
do backup — uma importação não as apaga.

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
│   ├── CsvBackup.kt             formato de exportação/importação em CSV
│   ├── Fotos.kt                 redução, gravação e leitura das fotos do livro
│   └── Recorrencia.kt           recorrentes: datas de cada vez, o que falta lançar
└── ui/
    ├── ObraApp.kt               navegação e barra inferior (5 abas)
    ├── ObraViewModel.kt         estado, gravação, importação com cópia prévia
    ├── Estatisticas.kt          contas puras: totais, dados dos gráficos, previsão
    ├── Exemplo.kt               obra inventada para os exemplos dos gráficos
    ├── EcraAbertura.kt          animação de arranque
    ├── Formato.kt               formatação pt-PT (euros, datas, plurais)
    ├── Reordenavel.kt           arrastar para reordenar numa LazyColumn
    ├── Desfazer.kt              snackbar "Desfazer" depois de apagar
    ├── PagamentosScreen.kt      lista agrupada por mês, menu de opções
    ├── PagamentoFormScreen.kt   registar / editar pagamento
    ├── DespesasScreen.kt        despesas da casa (crédito, seguros, contas)
    ├── DespesaFormScreen.kt     registar / editar / repetir despesa
    ├── DespesasGraficosScreen.kt  gráficos das despesas (botão "Ver gráficos")
    ├── DespesasFixasScreen.kt   despesas recorrentes: próximo débito, pausar, parar
    ├── EditorRepeticao.kt       "Repete": opções rápidas e repetição personalizada
    ├── GraficosScreen.kt        catálogo de gráficos e o ecrã que os mostra
    ├── EscolherGraficosScreen.kt  escolher, ordenar e pré-visualizar gráficos
    ├── LivroScreen.kt           livro de obra (cronologia de eventos)
    ├── FotosUi.kt               miniaturas, visualizador, galeria/câmara
    ├── CategoriasScreen.kt      especialidades, cores e orçamentos
    ├── charts/Charts.kt         barras, colunas, donut, medidor (Canvas)
    ├── charts/Evolucao.kt       linha temporal, colunas empilhadas, mapa de calor
    ├── charts/Previews.kt       @Preview dos gráficos, em claro e escuro
    └── theme/Theme.kt           tema e paleta de visualização (claro/escuro)
```

### Blocos do ecrã de gráficos

Todos os blocos vivem num só sítio, `CATALOGO_GRAFICOS` em `GraficosScreen.kt`:
identificador, título, descrição, quando tem dados, e como se desenha. O ecrã
*Gráficos* e o ecrã **Escolher gráficos** usam a mesma lista.

O *Escolher gráficos* abre-se pelo ícone ⚙ (afinação) na barra dos Gráficos ou
pelo menu ⋮ dos Pagamentos. Mostra **todos** os blocos, mesmo os que ainda não
têm dados, cada um com:

- um interruptor para aparecer ou não (`definicoes.graficosEscondidos`);
- uma pega ☰ para arrastar (ou tocar, para um menu com *mover para cima / topo /
  baixo / fim*, que também serve leitores de ecrã); a ordem fica em
  `definicoes.ordemGraficos`;
- "Ver como fica": desenha o bloco com os dados verdadeiros, ou — se ainda não os
  houver — com os de uma obra inventada (`Exemplo.kt`), para se perceber o que ele
  mostra antes de o escolher.

As alterações gravam-se logo. O menu ⋮ do ecrã tem *Mostrar todos*, *Esconder
todos* e *Repor a ordem de fábrica*.

Os identificadores ficam gravados na base de dados — **nunca os mudar** depois de
a app estar instalada:

| Id | Bloco | Aparece quando |
|---|---|---|
| `resumo` | Custo da construção | sempre |
| `orcamento` | Orçamento da obra (toca para definir) | sempre |
| `comparacao_mensal` | Este mês vs mês anterior e vs média mensal | há gastos de obra |
| `custo_m2` | Custo por m² (toca para indicar a área) | sempre |
| `extra` | Custos fora da obra | há custos fora da obra |
| `iva` | IVA pago vs IVA poupado | há IVA |
| `iva_acumulado` | IVA poupado ao longo do tempo (linha) | há IVA poupado e mais de um mês |
| `taxas_iva` | Faturas por taxa de IVA (23/13/6%, sem IVA, parcial) | há gastos de obra |
| `categorias` | Gastos por especialidade | há gastos de obra |
| `donut` | Peso de cada especialidade | **mais de uma** categoria com gastos |
| `obra_vs_fora` | Obra vs custos fora da obra | existem gastos dos **dois** tipos |
| `origem_fundos` | Origem dos fundos: pessoal vs bancário (total da casa e obra mês a mês) | há pagamentos |
| `maiores` | Maiores pagamentos | há mais de um pagamento |
| `fornecedores` | Maiores fornecedores | algum pagamento tem fornecedor |
| `mensal` | Gastos por mês | a obra abrange mais de um mês |
| `mensal_categorias` | Gastos por mês e especialidade (colunas empilhadas) | mais de um mês e de uma categoria |
| `acumulado` | Gasto acumulado, com o orçamento tracejado | mais de um mês |
| `previsao` | Previsão: quando se chega ao orçamento | mais de um mês e ritmo > 0 |
| `atividade` | Atividade na obra (mapa de calor por dia) | há pagamentos de obra ou eventos |
| `orcamentos` | Orçamento por especialidade (medidores, %) | alguma categoria tem orçamento |
| `orcamento_vs_real` | Orçamento vs real, na mesma escala, com o desvio em € | alguma categoria tem orçamento |

Um bloco escondido mantém o lugar na ordem, e um bloco sem dados também — para
que um mês sem pagamentos não faça o gráfico mensal voltar ao sítio de fábrica.
Um bloco novo entra a seguir ao seu vizinho de fábrica mais próximo, em vez de
ir parar ao fim. Um bloco retirado (como o antigo `iva_extra`) desaparece sozinho
das ordens gravadas, porque `ordemUtil` só aceita os ids do catálogo.

As contas de todos os gráficos estão em `Estatisticas.kt` (funções puras, com
testes). A **previsão** é deliberadamente simples: média dos últimos três meses
**completos** (o mês corrente fica de fora, porque a meio ainda vai a meio),
projetada a partir do último acumulado; diz em que mês se chega ao orçamento, ou
o total daqui a seis meses se não houver orçamento.

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

Os gráficos novos seguem as mesmas regras: um só eixo Y, linhas de 2 px com
marcador só no mês escolhido (toca-se para escolher, e a leitura aparece por
cima), a projeção tracejada na mesma cor da série real, o orçamento como linha
tracejada neutra, o mapa de calor na rampa sequencial de um só tom, e as classes
de IVA e as rubricas de despesa com slot fixo (a cor segue a classe, não a
posição). Os blocos de séries temporais têm "Ver como tabela".

O donut (`Donut` em `charts/Charts.kt`) é a exceção à regra da casa de preferir
comprimentos a ângulos: serve para a impressão de conjunto, e os valores exatos
vivem na legenda em lista por baixo, com valor e percentagem. Fatias abaixo de
3% juntam-se em «Outros» — mas só se forem mais que uma, senão trocava-se um
nome verdadeiro por um genérico sem ganho nenhum. As cores das fatias são as
das categorias, as mesmas do ecrã *Categorias*, para a identidade não mudar de
gráfico para gráfico.

### Migrações

A base de dados está na **versão 14** (`VERSAO_BD`). As migrações estão em
`ObraDatabase` e são todas explícitas (nunca `fallbackToDestructiveMigration`, que
apagaria os dados):

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
| 9 | só em builds de desenvolvimento (separador de links, retirado) |
| 10 | larga as tabelas dos links; igual para quem vem da 8 ou da 9 |
| 11 | pagamentos em cêntimos (a tabela é recriada — é filha, não toca nas categorias), `pagamentos.fornecedor`, `eventos.fotos`, `definicoes.graficosEscondidos`, `definicoes.areaConstrucao`, tabela `despesas` |
| 12 | tabela `despesas_fixas`; `despesas.fixaId` (nulo) e `despesas.aConfirmar` (0) |
| 13 | `despesas_fixas` recriada: unidade, intervalo, início, próxima vez, vezes lançadas, máximo de vezes; as mensais antigas continuam exatamente onde estavam |
| 14 | `pagamentos.origemFundos`; entra a `pessoal` em tudo o que já existia |

**Cópia antes de migrar.** Ao arrancar, se a base de dados no telemóvel for de
uma versão anterior, `copiarAntesDeMigrar` copia-a tal como está para
`files/copias/antes-da-v<N>/` antes de o Room lhe tocar. Se uma migração correr
mal, os dados antigos continuam lá. (A versão lê-se do cabeçalho do SQLite, sem
abrir o ficheiro.)

**Esquemas e testes.** `exportSchema = true`: cada versão deixa o seu esquema em
`app/schemas/` (a partir da 10). `MigracaoTest` cria uma base de dados com o
esquema exato da 10, enche-a, migra e confere valor a valor — e abre-a também
pelo caminho verdadeiro da app (`ObraDatabase.get`), confirmando a cópia prévia.
Ao acrescentar um campo: subir `VERSAO_BD`, escrever a migração, compilar (gera
o esquema novo) e acrescentar o caso ao teste.

A instalação de raiz cria a linha das definições com `SQL_CRIAR_DEFINICOES`, o
mesmo SQL do DAO. Até à versão 10 a semente esquecia-se de uma coluna e o
`INSERT OR IGNORE` engolia o erro, deixando a linha por criar (a app usava os
valores por omissão até se gravar a primeira definição); a migração 11 cria-a a
quem a não tem.

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
.\gradlew.bat --offline :app:testDebugUnitTest   # testes (migração, IVA, CSV, gráficos)
.\gradlew.bat --offline clean                  # se algo ficar num estado estranho
```

Os testes correm na JVM com o Robolectric (sem emulador). Na primeira vez o
Robolectric descarrega uma imagem do Android, por isso precisa de rede.

A build de release passa pelo **R8** (`isMinifyEnabled` e `isShrinkResources`):
o APK tem cerca de 1,6 MB (eram 11 MB, quase todos de ícones não usados).
`versionCode` sobe a cada APK que vai para o telemóvel (está na 5, `1.4`).

O `--offline` é o que se usa aqui porque a máquina não tem acesso à Internet e
todas as dependências já estão na cache do Gradle. Com rede, pode omitir-se.

Se o `gradlew.bat` não arrancar por não encontrar Java, apontar o `JAVA_HOME` ao
JDK que vem com o Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### Onde fica o APK

```
app\build\outputs\apk\release\app-release.apk   (~1,6 MB)
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

A build de release é assinada com `app/obra-release.jks`. O caminho, o alias e as
palavras-passe estão em `keystore.properties`, na raiz do projeto, que **não**
vai para o git (tal como o `.jks`); `app/build.gradle.kts` lê-o de lá. Sem esse
ficheiro a app compila na mesma, só o release é que sai por assinar. **Guardar o
`.jks` e o `keystore.properties`** num sítio seguro, fora do projeto:
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
