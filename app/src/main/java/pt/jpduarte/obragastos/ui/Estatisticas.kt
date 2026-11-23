package pt.jpduarte.obragastos.ui

import pt.jpduarte.obragastos.data.Categoria
import pt.jpduarte.obragastos.data.Despesa
import pt.jpduarte.obragastos.data.EventoObra
import pt.jpduarte.obragastos.data.Pagamento
import pt.jpduarte.obragastos.data.RUBRICAS_DESPESA
import pt.jpduarte.obragastos.data.RUBRICA_PRESTACAO
import pt.jpduarte.obragastos.data.bancario
import pt.jpduarte.obragastos.data.custoRealCent
import pt.jpduarte.obragastos.data.data
import pt.jpduarte.obragastos.data.emEuros
import pt.jpduarte.obragastos.data.ivaEfetivoCent
import pt.jpduarte.obragastos.data.ivaParcial
import pt.jpduarte.obragastos.data.ivaPoupadoCent
import pt.jpduarte.obragastos.data.totalPagoCent
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

/*
 * Tudo o que os gráficos mostram, calculado aqui e não nos ecrãs: são funções
 * puras, testáveis sem Android, e o ecrã de escolha de gráficos usa as mesmas
 * contas para desenhar os exemplos com dados fictícios.
 *
 * As somas fazem-se em cêntimos e só o resultado passa a euros.
 */

/** Totais da obra até à data. */
data class ResumoObra(
    val totalPago: Double = 0.0,
    val totalBase: Double = 0.0,
    val ivaPago: Double = 0.0,
    val ivaPoupado: Double = 0.0,
    val nPagamentos: Int = 0,
) {
    /** Quanto custaria a obra se todo o IVA tivesse sido pago. */
    val totalComTodoIva: Double get() = totalBase + ivaPago + ivaPoupado

    /** A poupança em percentagem do que a obra teria custado. */
    val poupancaPct: Double
        get() = if (totalComTodoIva > 0) ivaPoupado / totalComTodoIva * 100.0 else 0.0
}

fun resumoDe(lista: List<Pagamento>) = ResumoObra(
    totalPago = lista.sumOf { it.totalPagoCent }.emEuros(),
    totalBase = lista.sumOf { it.valorBaseCent }.emEuros(),
    ivaPago = lista.sumOf { it.ivaEfetivoCent }.emEuros(),
    ivaPoupado = lista.sumOf { it.ivaPoupadoCent }.emEuros(),
    nPagamentos = lista.size,
)

/**
 * Gasto acumulado numa categoria. [total] conta só o que é obra — é o que se
 * compara com os orçamentos; [totalExtra] são os custos marcados como fora da
 * obra (terreno, licenças antigas), contados à parte. [pagamentos] traz-nos
 * todos, para se poder abrir a categoria e ver de onde vem o valor.
 */
data class GastoCategoria(
    val categoria: Categoria,
    val total: Double,
    val nPagamentos: Int,
    val totalExtra: Double,
    val nExtra: Int,
    val pagamentos: List<Pagamento> = emptyList(),
) {
    /** Tudo o que se gastou nesta rubrica, seja obra ou não. */
    val totalGeral: Double get() = total + totalExtra

    val nTodos: Int get() = nPagamentos + nExtra
}

/**
 * Gasto por especialidade. Ordena pelo total geral (obra + fora da obra),
 * senão uma rubrica que só tenha custos fora da obra ia parar ao fim como
 * se não tivesse nada.
 */
fun gastosPorCategoria(cats: List<Categoria>, pags: List<Pagamento>): List<GastoCategoria> {
    val agrupado = pags.groupBy { it.categoriaId }
    return cats.map { c ->
        val doGrupo = agrupado[c.id].orEmpty()
        val (obra, extra) = doGrupo.partition { it.contaParaObra }
        GastoCategoria(
            categoria = c,
            total = obra.sumOf { it.totalPagoCent }.emEuros(),
            nPagamentos = obra.size,
            totalExtra = extra.sumOf { it.totalPagoCent }.emEuros(),
            nExtra = extra.size,
            pagamentos = doGrupo.sortedByDescending { it.dataEpochDay },
        )
    }.sortedByDescending { it.totalGeral }
}

/** Um pagamento com o nome e a cor da categoria já resolvidos, para listagens. */
data class PagamentoDetalhe(
    val pagamento: Pagamento,
    val categoria: String,
    val corIndex: Int = 0,
)

/** Quanto cada categoria gastou num mês (id da categoria -> euros). */
data class MesCategorias(val mes: YearMonth, val valores: Map<Long, Double>) {
    val total: Double get() = valores.values.sum()
}

/** Uma classe de IVA (23%, sem IVA, parcial, …) com o valor sem IVA e o nº de faturas. */
data class FatiaTaxa(val rotulo: String, val valor: Double, val n: Int, val slot: Int)

data class Fornecedor(val nome: String, val total: Double, val n: Int)

data class AtividadeDia(val pagamentos: Int = 0, val eventos: Int = 0) {
    val total: Int get() = pagamentos + eventos
}

/** O que saiu de dinheiro pessoal e o que veio do banco, em euros. */
data class OrigemFundos(val pessoal: Double = 0.0, val bancario: Double = 0.0) {
    val total: Double get() = pessoal + bancario
}

data class OrigemMes(val mes: YearMonth, val origem: OrigemFundos)

data class GastoRubrica(val rubrica: String, val custoReal: Double, val capital: Double, val n: Int, val slot: Int)

/** O que uma prestação do crédito foi num mês: juros (e afins) e capital amortizado. */
data class PrestacaoMes(val mes: YearMonth, val juros: Double, val capital: Double)

data class ResumoDespesas(
    val total: Double = 0.0,
    val custoReal: Double = 0.0,
    val capital: Double = 0.0,
    val n: Int = 0,
    val porRubrica: List<GastoRubrica> = emptyList(),
    /** Custo real por mês, sem saltos. */
    val porMes: List<Pair<YearMonth, Double>> = emptyList(),
    /**
     * As prestações do crédito mês a mês, separadas em juros e capital — só as
     * que têm o capital indicado; sem ele não há divisão a mostrar.
     */
    val prestacoes: List<PrestacaoMes> = emptyList(),
)

/**
 * Tudo o que o ecrã de gráficos precisa, já calculado. Só a obra: as despesas
 * da casa têm os seus gráficos no separador Despesas ([resumoDespesas]).
 */
data class DadosGraficos(
    val resumo: ResumoObra = ResumoObra(),
    val resumoExtra: ResumoObra = ResumoObra(),
    val porCategoria: List<GastoCategoria> = emptyList(),
    /** Gasto de obra por mês, cronológico e sem saltos. */
    val porMes: List<Pair<YearMonth, Double>> = emptyList(),
    /** O mesmo, mas acumulado: o valor de cada mês é tudo o que já se gastou até lá. */
    val acumulado: List<Pair<YearMonth, Double>> = emptyList(),
    val ivaPoupadoAcumulado: List<Pair<YearMonth, Double>> = emptyList(),
    val porMesCategoria: List<MesCategorias> = emptyList(),
    val maiores: List<PagamentoDetalhe> = emptyList(),
    val orcamentoTotal: Double = 0.0,
    val areaConstrucao: Double = 0.0,
    val taxas: List<FatiaTaxa> = emptyList(),
    val fornecedores: List<Fornecedor> = emptyList(),
    val nSemFornecedor: Int = 0,
    val atividade: Map<LocalDate, AtividadeDia> = emptyMap(),
    val hoje: LocalDate = LocalDate.now(),
    /** Origem do dinheiro de **toda a casa** (obra e fora da obra). */
    val origem: OrigemFundos = OrigemFundos(),
    /** Origem do dinheiro da obra, mês a mês e sem saltos (os mesmos meses de [porMes]). */
    val origemPorMes: List<OrigemMes> = emptyList(),
) {
    val comGastos: List<GastoCategoria> get() = porCategoria.filter { it.total > 0.0 }
    val comOrcamento: List<GastoCategoria> get() = porCategoria.filter { it.categoria.orcamento > 0.0 }
    val extras: List<GastoCategoria> get() = porCategoria.filter { it.totalExtra > 0.0 }
}

/** Meses de [primeiro] a [ultimo], inclusive. */
private fun meses(primeiro: YearMonth, ultimo: YearMonth): List<YearMonth> = buildList {
    var m = primeiro
    while (!m.isAfter(ultimo)) {
        add(m)
        m = m.plusMonths(1)
    }
}

/**
 * Soma por mês **sem saltos** — os meses sem movimento aparecem a zero, para
 * que a evolução não minta sobre o ritmo.
 */
private fun <T> somaPorMes(lista: List<T>, mes: (T) -> YearMonth, cent: (T) -> Long): List<Pair<YearMonth, Double>> {
    if (lista.isEmpty()) return emptyList()
    val soma = lista.groupBy(mes).mapValues { (_, v) -> v.sumOf(cent) }
    return meses(soma.keys.min(), soma.keys.max()).map { it to (soma[it] ?: 0L).emEuros() }
}

private fun acumular(serie: List<Pair<YearMonth, Double>>): List<Pair<YearMonth, Double>> {
    var soma = 0.0
    return serie.map { (m, v) -> soma += v; m to soma }
}

/** Ordem fixa das classes de IVA: a cor segue a classe, não a posição. */
private val CLASSES_TAXA = listOf("23%", "13%", "6%", "Outra taxa", "IVA parcial", "Sem IVA")

private fun classeTaxa(p: Pagamento): String = when {
    !p.ivaPago -> "Sem IVA"
    p.ivaParcial -> "IVA parcial"
    p.taxaIva == 23.0 -> "23%"
    p.taxaIva == 13.0 -> "13%"
    p.taxaIva == 6.0 -> "6%"
    else -> "Outra taxa"
}

/**
 * Slot de cor de uma rubrica de despesa: as sugeridas têm o seu, pela ordem de
 * [RUBRICAS_DESPESA]; as inventadas pelo utilizador seguem-se, por ordem alfabética.
 */
fun slotRubrica(rubrica: String, todas: Collection<String>): Int {
    val i = RUBRICAS_DESPESA.indexOf(rubrica)
    if (i >= 0) return i
    val proprias = todas.filter { it !in RUBRICAS_DESPESA }.distinct().sorted()
    return RUBRICAS_DESPESA.size + proprias.indexOf(rubrica).coerceAtLeast(0)
}

fun resumoDespesas(despesas: List<Despesa>): ResumoDespesas {
    if (despesas.isEmpty()) return ResumoDespesas()
    val rubricas = despesas.map { it.rubrica }.toSet()
    return ResumoDespesas(
        total = despesas.sumOf { it.valorCent }.emEuros(),
        custoReal = despesas.sumOf { it.custoRealCent }.emEuros(),
        capital = despesas.sumOf { it.valorCent - it.custoRealCent }.emEuros(),
        n = despesas.size,
        porRubrica = despesas.groupBy { it.rubrica }.map { (r, ds) ->
            GastoRubrica(
                rubrica = r,
                custoReal = ds.sumOf { it.custoRealCent }.emEuros(),
                capital = ds.sumOf { it.valorCent - it.custoRealCent }.emEuros(),
                n = ds.size,
                slot = slotRubrica(r, rubricas),
            )
        }.sortedByDescending { it.custoReal + it.capital },
        porMes = somaPorMes(despesas, { YearMonth.from(it.data) }, { it.custoRealCent }),
        prestacoes = run {
            val prest = despesas.filter { it.rubrica == RUBRICA_PRESTACAO && it.capitalCent > 0 }
            val juros = somaPorMes(prest, { YearMonth.from(it.data) }, { it.custoRealCent })
            val capital = somaPorMes(prest, { YearMonth.from(it.data) }, { it.valorCent - it.custoRealCent })
            juros.zip(capital) { (m, j), (_, c) -> PrestacaoMes(m, j, c) }
        },
    )
}

fun calcularDadosGraficos(
    categorias: List<Categoria>,
    pagamentos: List<Pagamento>,
    eventos: List<EventoObra>,
    orcamentoTotal: Double,
    areaConstrucao: Double,
    hoje: LocalDate = LocalDate.now(),
): DadosGraficos {
    // Evolução da obra: os custos fora da obra são de outra natureza (e muitas
    // vezes de outra época) e distorceriam o ritmo.
    val obra = pagamentos.filter { it.contaParaObra }
    val porId = categorias.associateBy { it.id }
    val porMes = somaPorMes(obra, { YearMonth.from(it.data) }, { it.totalPagoCent })
    val ivaPorMes = somaPorMes(obra, { YearMonth.from(it.data) }, { it.ivaPoupadoCent })

    val porMesCategoria = run {
        val agrupado = obra.groupBy { YearMonth.from(it.data) }
        porMes.map { (m, _) ->
            MesCategorias(
                m,
                agrupado[m].orEmpty().groupBy { it.categoriaId }
                    .mapValues { (_, v) -> v.sumOf { it.totalPagoCent }.emEuros() },
            )
        }
    }

    val taxas = obra.groupBy(::classeTaxa).map { (classe, ps) ->
        FatiaTaxa(classe, ps.sumOf { it.valorBaseCent }.emEuros(), ps.size, CLASSES_TAXA.indexOf(classe))
    }.sortedBy { it.slot }

    // Fornecedores de toda a casa, juntando grafias diferentes do mesmo nome
    // ("Silva Lda" e "silva lda "); mostra-se a grafia mais usada.
    val comFornecedor = pagamentos.filter { it.fornecedor.isNotBlank() }
    val fornecedores = comFornecedor.groupBy { it.fornecedor.trim().lowercase() }.map { (_, ps) ->
        Fornecedor(
            nome = ps.groupingBy { it.fornecedor.trim() }.eachCount().maxBy { it.value }.key,
            total = ps.sumOf { it.totalPagoCent }.emEuros(),
            n = ps.size,
        )
    }.sortedByDescending { it.total }

    fun origemDe(ps: List<Pagamento>): OrigemFundos {
        val (banco, pessoal) = ps.partition { it.bancario }
        return OrigemFundos(
            pessoal = pessoal.sumOf { it.totalPagoCent }.emEuros(),
            bancario = banco.sumOf { it.totalPagoCent }.emEuros(),
        )
    }
    val origemPorMes = run {
        val agrupado = obra.groupBy { YearMonth.from(it.data) }
        porMes.map { (m, _) -> OrigemMes(m, origemDe(agrupado[m].orEmpty())) }
    }

    val atividade = mutableMapOf<LocalDate, AtividadeDia>()
    obra.forEach { p -> atividade.merge(p.data, AtividadeDia(pagamentos = 1)) { a, _ -> a.copy(pagamentos = a.pagamentos + 1) } }
    eventos.forEach { e ->
        val d = LocalDate.ofEpochDay(e.dataEpochDay)
        atividade.merge(d, AtividadeDia(eventos = 1)) { a, _ -> a.copy(eventos = a.eventos + 1) }
    }

    return DadosGraficos(
        resumo = resumoDe(obra),
        resumoExtra = resumoDe(pagamentos.filterNot { it.contaParaObra }),
        porCategoria = gastosPorCategoria(categorias, pagamentos),
        porMes = porMes,
        acumulado = acumular(porMes),
        ivaPoupadoAcumulado = acumular(ivaPorMes),
        porMesCategoria = porMesCategoria,
        // Obra **e** fora da obra: a pergunta aqui é "quais foram as maiores
        // faturas da casa", não "da construção".
        maiores = pagamentos.sortedByDescending { it.totalPagoCent }.take(5).map { p ->
            val c = porId[p.categoriaId]
            PagamentoDetalhe(p, c?.nome ?: "Sem categoria", c?.corIndex ?: 0)
        },
        orcamentoTotal = orcamentoTotal,
        areaConstrucao = areaConstrucao,
        taxas = taxas,
        fornecedores = fornecedores,
        nSemFornecedor = pagamentos.size - comFornecedor.size,
        atividade = atividade,
        hoje = hoje,
        origem = origemDe(pagamentos),
        origemPorMes = origemPorMes,
    )
}

/* ------------------------------ previsão ------------------------------ */

data class Previsao(
    /** Média mensal dos últimos meses completos (até 3). */
    val ritmoMensal: Double,
    /** Mês em que, a este ritmo, se chega ao orçamento. null sem orçamento ou se já passou. */
    val mesEsgota: YearMonth?,
    val jaExcedeu: Boolean,
    /** Continuação do acumulado a este ritmo, a começar no último mês real. */
    val projecao: List<Pair<YearMonth, Double>>,
)

/** Até onde a projeção se estende no gráfico, quando não há orçamento a atingir. */
private const val MESES_PROJECAO_SEM_ORCAMENTO = 6
private const val MESES_PROJECAO_MAX = 24

/**
 * Projeta o acumulado ao ritmo dos últimos meses. O mês corrente fica de fora
 * da média sempre que possível: a meio do mês ainda vai a meio, e puxava o
 * ritmo para baixo.
 */
fun DadosGraficos.previsao(): Previsao? {
    if (porMes.size < 2) return null
    val atual = YearMonth.from(hoje)
    val completos = porMes.filter { it.first.isBefore(atual) }.ifEmpty { porMes }
    val ritmo = completos.takeLast(3).map { it.second }.average()
    if (ritmo <= 0.0) return null

    val (ultimoMes, gasto) = acumulado.last()
    val restante = orcamentoTotal - gasto
    val jaExcedeu = orcamentoTotal > 0.0 && restante <= 0.0
    val mesesAteOrcamento = if (orcamentoTotal > 0.0 && !jaExcedeu) ceil(restante / ritmo).toInt() else null
    val horizonte = (mesesAteOrcamento ?: MESES_PROJECAO_SEM_ORCAMENTO).coerceIn(1, MESES_PROJECAO_MAX)

    return Previsao(
        ritmoMensal = ritmo,
        mesEsgota = mesesAteOrcamento?.let { ultimoMes.plusMonths(it.toLong()) },
        jaExcedeu = jaExcedeu,
        projecao = (0..horizonte).map { i -> ultimoMes.plusMonths(i.toLong()) to gasto + ritmo * i },
    )
}

/* ---------------------------- comparação ---------------------------- */

data class Comparacao(
    val mes: YearMonth,
    val esteMes: Double,
    val mesAnterior: Double,
    /** Média dos meses anteriores ao corrente (ou de todos, se só há o corrente). */
    val media: Double,
)

fun DadosGraficos.comparacao(): Comparacao? {
    if (porMes.isEmpty()) return null
    val atual = YearMonth.from(hoje)
    val valor = porMes.toMap()
    val anteriores = porMes.filter { it.first.isBefore(atual) }.ifEmpty { porMes }
    return Comparacao(
        mes = atual,
        esteMes = valor[atual] ?: 0.0,
        mesAnterior = valor[atual.minusMonths(1)] ?: 0.0,
        media = anteriores.map { it.second }.average(),
    )
}
