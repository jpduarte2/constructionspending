package pt.jpduarte.obragastos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.jpduarte.obragastos.data.data
import pt.jpduarte.obragastos.data.totalPago
import pt.jpduarte.obragastos.ui.charts.BarraEmpilhada
import pt.jpduarte.obragastos.ui.charts.BarrasHorizontais
import pt.jpduarte.obragastos.ui.charts.ColunasEmpilhadas
import pt.jpduarte.obragastos.ui.charts.ColunasMensais
import pt.jpduarte.obragastos.ui.charts.Donut
import pt.jpduarte.obragastos.ui.charts.Fatia
import pt.jpduarte.obragastos.ui.charts.Legenda
import pt.jpduarte.obragastos.ui.charts.LinhaTempo
import pt.jpduarte.obragastos.ui.charts.MapaCalor
import pt.jpduarte.obragastos.ui.charts.Medidor
import pt.jpduarte.obragastos.ui.charts.Pilha
import pt.jpduarte.obragastos.ui.charts.Referencia
import pt.jpduarte.obragastos.ui.charts.SerieLinha
import pt.jpduarte.obragastos.ui.theme.LocalViz
import pt.jpduarte.obragastos.ui.theme.VizPalette
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * O que um bloco pode pedir ao ecrã — abrir os diálogos do orçamento e da área.
 * No ecrã de escolha os exemplos recebem [NENHUMA]: lá não se edita nada.
 */
internal class AcoesGraficos(
    val definirOrcamento: () -> Unit = {},
    val definirArea: () -> Unit = {},
) {
    companion object {
        val NENHUMA = AcoesGraficos()
    }
}

/**
 * Um bloco do ecrã de gráficos. O [id] é o que fica gravado na ordem e na lista
 * dos escondidos, por isso **nunca** deve mudar depois de a aplicação sair para
 * o mundo. [temDados] diz se há com que o desenhar — sem isso o bloco não
 * aparece, e no ecrã de escolha mostra-se com os dados de exemplo.
 */
internal class DefinicaoGrafico(
    val id: String,
    val titulo: String,
    val descricao: String,
    val temDados: (DadosGraficos) -> Boolean,
    val conteudo: @Composable (DadosGraficos, AcoesGraficos) -> Unit,
)

/**
 * A cor da categoria — a mesma que ela tem no ecrã *Categorias*. Usada em todos
 * os gráficos onde as barras representam rubricas, para que uma especialidade
 * se reconheça pela cor em qualquer sítio da aplicação.
 */
private fun GastoCategoria.cor(viz: VizPalette): Color =
    viz.series[categoria.corIndex % viz.series.size]

/**
 * Todos os blocos que a aplicação conhece, pela ordem de fábrica. Ids de blocos
 * retirados (como o antigo `iva_extra`, ou `despesas`, que passou para o
 * separador Despesas) desaparecem sozinhos das ordens já gravadas, porque
 * [ordemUtil] só aceita o que está aqui.
 */
internal val CATALOGO_GRAFICOS: List<DefinicaoGrafico> = listOf(
    DefinicaoGrafico(
        "resumo", "Custo da construção",
        "O total gasto na obra e o total da casa, com os custos fora da obra.",
        { true },
    ) { d, _ -> BlocoResumo(d.resumo, d.resumoExtra) },
    DefinicaoGrafico(
        "orcamento", "Orçamento da obra",
        "Quanto do orçamento global já foi consumido e quanto falta.",
        { true },
    ) { d, a -> CartaoOrcamentoTotal(d.resumo.totalPago, d.orcamentoTotal, a.definirOrcamento) },
    DefinicaoGrafico(
        "comparacao_mensal", "Este mês em comparação",
        "O gasto do mês corrente face ao mês anterior e à média mensal.",
        { it.porMes.isNotEmpty() },
    ) { d, _ -> BlocoComparacao(d) },
    DefinicaoGrafico(
        "custo_m2", "Custo por m²",
        "O custo da construção a dividir pela área — o número que toda a gente compara.",
        { true },
    ) { d, a -> BlocoCustoM2(d, a.definirArea) },
    DefinicaoGrafico(
        "extra", "Custos fora da obra",
        "Terreno, licenças, escrituras: dinheiro da casa que não é construção.",
        { it.resumoExtra.nPagamentos > 0 },
    ) { d, _ -> BlocoCustosFora(d.resumoExtra, d.extras) },
    DefinicaoGrafico(
        "iva", "IVA pago vs IVA poupado",
        "Do IVA que as faturas implicavam, quanto saiu da conta e quanto se evitou.",
        { it.resumo.ivaPago + it.resumo.ivaPoupado > 0.0 },
    ) { d, _ -> BlocoIva(d.resumo) },
    DefinicaoGrafico(
        "iva_acumulado", "IVA poupado ao longo do tempo",
        "A poupança de IVA a crescer mês a mês.",
        { it.resumo.ivaPoupado > 0.0 && it.porMes.size > 1 },
    ) { d, _ -> BlocoIvaAcumulado(d) },
    DefinicaoGrafico(
        "taxas_iva", "Faturas por taxa de IVA",
        "Quanto da obra foi faturado a 23%, 13%, 6%, sem IVA ou com IVA só sobre parte.",
        { it.taxas.isNotEmpty() },
    ) { d, _ -> BlocoTaxas(d) },
    DefinicaoGrafico(
        "categorias", "Gastos por especialidade",
        "Total pago em cada rubrica, da maior para a menor.",
        { it.comGastos.isNotEmpty() },
    ) { d, _ -> BlocoPorEspecialidade(d.comGastos) },
    DefinicaoGrafico(
        "donut", "Peso de cada especialidade",
        "Quanto vale cada rubrica no total da obra, em anel.",
        { it.comGastos.size > 1 },
    ) { d, _ -> BlocoPesoCategorias(d.comGastos) },
    DefinicaoGrafico(
        "obra_vs_fora", "Obra vs custos fora da obra",
        "O peso da construção no total gasto na casa.",
        { it.resumoExtra.totalPago > 0.0 && it.resumo.totalPago > 0.0 },
    ) { d, _ -> BlocoObraVsFora(d.resumo, d.resumoExtra) },
    DefinicaoGrafico(
        "origem_fundos", "Origem dos fundos",
        "Quanto saiu do bolso e quanto veio do banco — no total e mês a mês.",
        { it.origem.total > 0.0 },
    ) { d, _ -> BlocoOrigemFundos(d) },
    DefinicaoGrafico(
        "maiores", "Maiores pagamentos",
        "As cinco faturas que mais pesaram.",
        { it.maiores.size > 1 },
    ) { d, _ -> BlocoMaioresPagamentos(d.maiores) },
    DefinicaoGrafico(
        "fornecedores", "Maiores fornecedores",
        "A quem se pagou mais, somando todas as faturas de cada um.",
        { it.fornecedores.isNotEmpty() },
    ) { d, _ -> BlocoFornecedores(d) },
    DefinicaoGrafico(
        "mensal", "Gastos por mês",
        "Quanto se pagou em cada mês da obra.",
        { it.porMes.size > 1 },
    ) { d, _ -> BlocoPorMes(d.porMes) },
    DefinicaoGrafico(
        "mensal_categorias", "Gastos por mês e especialidade",
        "O mesmo gasto mensal, repartido pelas rubricas — mostra quando entrou cada uma.",
        { it.porMes.size > 1 && it.comGastos.size > 1 },
    ) { d, _ -> BlocoMensalCategorias(d) },
    DefinicaoGrafico(
        "acumulado", "Gasto acumulado",
        "O total da obra a subir mês a mês, com o orçamento como teto.",
        { it.porMes.size > 1 },
    ) { d, _ -> BlocoAcumulado(d) },
    DefinicaoGrafico(
        "previsao", "Previsão",
        "Ao ritmo dos últimos meses, quando se chega ao orçamento.",
        { it.previsao() != null },
    ) { d, _ -> BlocoPrevisao(d) },
    DefinicaoGrafico(
        "atividade", "Atividade na obra",
        "Pagamentos e eventos do livro, dia a dia, em calendário.",
        { it.atividade.isNotEmpty() },
    ) { d, _ -> BlocoAtividade(d) },
    DefinicaoGrafico(
        "orcamentos", "Orçamento por especialidade",
        "Percentagem consumida do orçamento de cada rubrica.",
        { it.comOrcamento.isNotEmpty() },
    ) { d, _ -> BlocoOrcamentoPorEspecialidade(d.comOrcamento) },
    DefinicaoGrafico(
        "orcamento_vs_real", "Orçamento vs real",
        "Orçamento e gasto de cada rubrica na mesma escala, com o desvio em euros.",
        { it.comOrcamento.isNotEmpty() },
    ) { d, _ -> BlocoOrcamentoVsReal(d.comOrcamento) },
)

private val ORDEM_FABRICA = CATALOGO_GRAFICOS.map { it.id }

/**
 * Junta a ordem gravada com a de fábrica: o que o utilizador escolheu manda,
 * e blocos novos (de uma versão posterior) entram junto do seu vizinho de
 * fábrica em vez de sumirem.
 */
internal fun ordemUtil(gravada: List<String>): List<String> {
    val conhecidos = gravada.filter { it in ORDEM_FABRICA }.distinct()
    if (conhecidos.isEmpty()) return ORDEM_FABRICA

    val resultado = conhecidos.toMutableList()
    ORDEM_FABRICA.forEachIndexed { i, id ->
        if (id !in resultado) {
            // Um bloco novo não vai para o fim: entra a seguir ao vizinho de
            // fábrica mais próximo que já esteja na lista, para aparecer onde
            // faz sentido mesmo em quem já tinha organizado o ecrã à mão.
            val anterior = ORDEM_FABRICA.take(i).lastOrNull { it in resultado }
            resultado.add(if (anterior == null) 0 else resultado.indexOf(anterior) + 1, id)
        }
    }
    return resultado
}

internal fun definicaoDe(id: String): DefinicaoGrafico? = CATALOGO_GRAFICOS.firstOrNull { it.id == id }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraficosScreen(vm: ObraViewModel, padding: PaddingValues, onEscolher: () -> Unit) {
    val dados by vm.dadosGraficos.collectAsState()
    val ordemGravada by vm.ordemGraficos.collectAsState()
    val escondidos by vm.graficosEscondidos.collectAsState()

    var editarOrcamento by remember { mutableStateOf(false) }
    var editarArea by remember { mutableStateOf(false) }

    if (editarOrcamento) {
        DialogoValor(
            titulo = "Orçamento da obra",
            explicacao = "O valor que estimas gastar do início ao fim. Podes alterá-lo " +
                "sempre que quiseres — serve de referência, não trava nada.",
            rotulo = "Orçamento previsto",
            sufixo = "€",
            exemplo = "300000",
            atual = dados.orcamentoTotal,
            onGuardar = { vm.definirOrcamentoTotal(it); editarOrcamento = false },
            onFechar = { editarOrcamento = false },
        )
    }
    if (editarArea) {
        DialogoValor(
            titulo = "Área de construção",
            explicacao = "A área bruta de construção da casa, como vem no projeto. " +
                "Serve para calcular o custo por metro quadrado.",
            rotulo = "Área",
            sufixo = "m²",
            exemplo = "200",
            atual = dados.areaConstrucao,
            onGuardar = { vm.definirAreaConstrucao(it); editarArea = false },
            onFechar = { editarArea = false },
        )
    }
    val acoes = remember { AcoesGraficos({ editarOrcamento = true }, { editarArea = true }) }

    // Visíveis: pela ordem escolhida, sem os escondidos, e só os que têm dados.
    val visiveis = ordemUtil(ordemGravada)
        .filter { it !in escondidos }
        .mapNotNull { definicaoDe(it) }
        .filter { it.temDados(dados) }

    Scaffold(
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text("Gráficos") },
                actions = {
                    IconButton(onClick = onEscolher) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Escolher e ordenar gráficos")
                    }
                },
            )
        },
    ) { interno ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = interno.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Sem pagamentos quase tudo desaparece; damos a razão em vez de
            // deixar o ecrã meio vazio.
            if (dados.resumo.nPagamentos == 0) {
                item(key = "aviso") {
                    Text(
                        if (dados.resumoExtra.nPagamentos > 0)
                            "Ainda não há pagamentos que contem para a obra — os gráficos aparecem quando registares o primeiro."
                        else
                            "Regista alguns pagamentos e os gráficos aparecem aqui.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
            }
            if (visiveis.isEmpty()) {
                item(key = "nada") {
                    Column(Modifier.padding(4.dp)) {
                        Text(
                            "Não há gráficos para mostrar.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = onEscolher, contentPadding = PaddingValues(0.dp)) {
                            Text("Escolher gráficos")
                        }
                    }
                }
            }
            items(visiveis, key = { it.id }) { bloco -> bloco.conteudo(dados, acoes) }
        }
    }
}

/* ------------------------------- blocos ------------------------------- */

@Composable
private fun BlocoResumo(resumo: ResumoObra, resumoExtra: ResumoObra) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Custo da construção até hoje",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                resumo.totalPago.eur(),
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            Indicador(
                "Custo de obra TOTAL",
                (resumo.totalPago + resumoExtra.totalPago).eur(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Construção + custos fora da obra",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlocoCustosFora(
    resumoExtra: ResumoObra,
    extras: List<GastoCategoria>,
) {
    val viz = LocalViz.current
    Secao(
        titulo = "Custos fora da obra",
        subtitulo = "Dinheiro da casa que não entra no total nem no orçamento da obra — terreno, licenças, escrituras.",
    ) {
        Text(
            resumoExtra.totalPago.eur(),
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            plural(resumoExtra.nPagamentos, "pagamento", "pagamentos"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (extras.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            BarrasHorizontais(
                dados = extras
                    .sortedByDescending { it.totalExtra }
                    .map { Fatia(it.categoria.nome, it.totalExtra, it.cor(viz)) },
            )
        }
    }
}

@Composable
private fun BlocoIva(resumo: ResumoObra) {
    val viz = LocalViz.current
    Secao(
        titulo = "IVA pago vs IVA poupado",
        subtitulo = "Do IVA total que as faturas da obra implicavam, quanto saiu da conta e quanto se evitou.",
    ) {
        BarraEmpilhada(
            partes = listOf(
                Fatia("IVA pago", resumo.ivaPago, viz.series[0]),
                Fatia("IVA poupado", resumo.ivaPoupado, viz.series[1]),
            ),
        )
        Spacer(Modifier.height(12.dp))
        Legenda(
            listOf(
                "IVA pago · ${resumo.ivaPago.eurCurto()}" to viz.series[0],
                "IVA poupado · ${resumo.ivaPoupado.eurCurto()}" to viz.series[1],
            ),
        )

        if (resumo.ivaPoupado > 0.0) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Text(
                "Poupaste ${resumo.ivaPoupado.eur()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = viz.tintaPositiva,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Com todo o IVA pago, a obra teria custado " +
                    "${resumo.totalComTodoIva.eurCurto()} — " +
                    "a poupança vale ${resumo.poupancaPct.pct(1)} desse valor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** O IVA poupado acumulado — a mesma cor que "IVA poupado" tem na barra do IVA. */
@Composable
private fun BlocoIvaAcumulado(d: DadosGraficos) {
    val viz = LocalViz.current
    val serie = d.ivaPoupadoAcumulado
    Secao(
        titulo = "IVA poupado ao longo do tempo",
        subtitulo = "Quanto IVA já se tinha evitado no fim de cada mês. Toca no gráfico para escolher um mês.",
    ) {
        LinhaTempo(
            rotulosX = serie.map { it.first.rotuloEixo() },
            series = listOf(SerieLinha("IVA poupado", serie.map { it.second }, viz.series[1])),
            rotuloLeitura = { "IVA poupado até ao fim de ${serie[it].first.texto()}" },
        )
        TabelaAlternavel(serie.map { (m, v) -> m.texto() to v.eur() })
    }
}

@Composable
private fun BlocoTaxas(d: DadosGraficos) {
    val viz = LocalViz.current
    val total = d.taxas.sumOf { it.valor }
    Secao(
        titulo = "Faturas por taxa de IVA",
        subtitulo = "Valor sem IVA da obra, repartido pela forma como o IVA foi tratado.",
    ) {
        BarraEmpilhada(partes = d.taxas.map { Fatia(it.rotulo, it.valor, viz.series[it.slot % viz.series.size]) })
        Spacer(Modifier.height(14.dp))
        d.taxas.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(viz.series[t.slot % viz.series.size]))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.rotulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        plural(t.n, "fatura", "faturas"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    t.valor.eurCurto(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    (if (total > 0) t.valor / total * 100 else 0.0).pct(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
            }
        }
    }
}

@Composable
private fun BlocoPorEspecialidade(comGastos: List<GastoCategoria>) {
    val viz = LocalViz.current
    Secao(
        titulo = "Gastos por especialidade",
        subtitulo = "Total pago em cada rubrica, da maior para a menor.",
    ) {
        BarrasHorizontais(
            dados = comGastos.map { Fatia(it.categoria.nome, it.total, it.cor(viz)) },
        )
    }
}

/**
 * Peso relativo de cada rubrica dentro da obra. Conta só gastos de obra: os
 * custos fora da obra têm bloco próprio, e misturar as duas perguntas num só
 * anel confundiria as duas.
 */
@Composable
private fun BlocoPesoCategorias(comGastos: List<GastoCategoria>) {
    val viz = LocalViz.current
    val dados = comGastos
        .filter { it.total > 0.0 }
        .map { g -> Fatia(g.categoria.nome, g.total, g.cor(viz)) }
    Secao(
        titulo = "Peso de cada especialidade",
        subtitulo = "Quanto vale cada rubrica no total gasto na obra. As fatias muito pequenas juntam-se em «Outros».",
    ) {
        Donut(dados = dados, textoCentro = "total")
    }
}

/** Quanto do dinheiro da casa foi construção e quanto foi tudo o resto. */
@Composable
private fun BlocoObraVsFora(resumo: ResumoObra, resumoExtra: ResumoObra) {
    val viz = LocalViz.current
    val total = resumo.totalPago + resumoExtra.totalPago
    Secao(
        titulo = "Obra vs custos fora da obra",
        subtitulo = "O peso da construção no total gasto na casa.",
    ) {
        BarraEmpilhada(
            partes = listOf(
                Fatia("Obra", resumo.totalPago, viz.series[0]),
                Fatia("Fora da obra", resumoExtra.totalPago, viz.series[1]),
            ),
        )
        Spacer(Modifier.height(12.dp))
        Legenda(
            listOf(
                "Obra · ${resumo.totalPago.eurCurto()}" to viz.series[0],
                "Fora da obra · ${resumoExtra.totalPago.eurCurto()}" to viz.series[1],
            ),
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                "Total gasto na casa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                total.eur(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Pessoal vs bancário. Em cima, todo o dinheiro da casa (obra e fora da obra —
 * o terreno é muitas vezes pago pelo crédito); em baixo, só a obra, mês a mês,
 * para se ver quando entrou o dinheiro do banco.
 */
@Composable
private fun BlocoOrigemFundos(d: DadosGraficos) {
    val viz = LocalViz.current
    val o = d.origem
    val corPessoal = viz.series[0]
    val corBanco = viz.series[1]
    Secao(
        titulo = "Origem dos fundos",
        subtitulo = "Todo o dinheiro gasto na casa, obra e fora da obra: quanto foi pessoal e quanto bancário.",
    ) {
        BarraEmpilhada(
            partes = listOf(
                Fatia("Pessoal", o.pessoal, corPessoal),
                Fatia("Bancário", o.bancario, corBanco),
            ),
        )
        Spacer(Modifier.height(12.dp))
        Legenda(
            listOf(
                "Pessoal · ${o.pessoal.eurCurto()} (${(o.pessoal / o.total * 100).pct()})" to corPessoal,
                "Bancário · ${o.bancario.eurCurto()} (${(o.bancario / o.total * 100).pct()})" to corBanco,
            ),
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                if (o.naoAplica > 0.0) "Total pessoal + bancário" else "Total gasto na casa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                o.total.eur(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (o.naoAplica > 0.0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Ficam de fora ${o.naoAplica.eurCurto()} em pagamentos marcados como N/A.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val meses = d.origemPorMes
        if (meses.size > 1) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Obra, mês a mês",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Toca numa coluna para ver a divisão do mês.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ColunasEmpilhadas(
                pilhas = meses.map { m ->
                    Pilha(
                        m.mes.textoCurto(),
                        listOf(
                            Fatia("Pessoal", m.origem.pessoal, corPessoal),
                            Fatia("Bancário", m.origem.bancario, corBanco),
                        ),
                    )
                },
                rotuloLeitura = { meses[it].mes.texto() },
            )
            TabelaAlternavel(
                meses.map { m ->
                    m.mes.texto() to "${m.origem.pessoal.eurCurto()} pessoal · ${m.origem.bancario.eurCurto()} bancário"
                },
            )
        }
    }
}

/**
 * As maiores faturas individuais. A barra é relativa à maior delas — serve de
 * comparação entre si, não contra o total da obra.
 */
@Composable
private fun BlocoMaioresPagamentos(maiores: List<PagamentoDetalhe>) {
    val viz = LocalViz.current
    val topo = maiores.firstOrNull()?.pagamento?.totalPago ?: return
    Secao(
        titulo = "Maiores pagamentos",
        subtitulo = "As faturas que mais pesaram, da maior para a menor.",
    ) {
        maiores.forEachIndexed { i, d ->
            val p = d.pagamento
            if (i > 0) Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    p.descricao.ifBlank { d.categoria },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    p.totalPago.eurCurto(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(((p.totalPago / topo).toFloat()).coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(viz.series[d.corIndex % viz.series.size]),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(d.categoria)
                    append(" · ")
                    append(p.data.texto())
                    if (!p.contaParaObra) append(" · fora da obra")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Nº de fornecedores com barra própria; os restantes juntam-se em «Outros». */
private const val MAX_FORNECEDORES = 8

@Composable
private fun BlocoFornecedores(d: DadosGraficos) {
    val principais = d.fornecedores.take(MAX_FORNECEDORES)
    val resto = d.fornecedores.drop(MAX_FORNECEDORES)
    Secao(
        titulo = "Maiores fornecedores",
        subtitulo = "Total pago a cada um, obra e fora da obra. Um só tom: o comprimento é que conta.",
    ) {
        BarrasHorizontais(
            dados = principais.map { Fatia("${it.nome} · ${plural(it.n, "fatura", "faturas")}", it.total) } +
                if (resto.isNotEmpty()) listOf(Fatia("Outros (${resto.size})", resto.sumOf { it.total })) else emptyList(),
        )
        if (d.nSemFornecedor > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${plural(d.nSemFornecedor, "pagamento", "pagamentos")} sem fornecedor indicado — " +
                    "acrescenta-o no pagamento para entrar aqui.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlocoPorMes(porMes: List<Pair<YearMonth, Double>>) {
    Secao(
        titulo = "Gastos por mês",
        subtitulo = "Toca numa coluna para ver o valor do mês.",
    ) {
        ColunasMensais(dados = porMes.map { (mes, valor) -> Fatia(mes.textoCurto(), valor) })
        TabelaAlternavel(porMes.map { (m, v) -> m.texto() to v.eur() })
    }
}

@Composable
private fun BlocoMensalCategorias(d: DadosGraficos) {
    val viz = LocalViz.current
    // A maior rubrica fica na base de todas as colunas, para a base ser estável.
    val rubricas = d.comGastos
    Secao(
        titulo = "Gastos por mês e especialidade",
        subtitulo = "Cada coluna é um mês; as cores são as das categorias. Toca numa coluna para ver a decomposição.",
    ) {
        ColunasEmpilhadas(
            pilhas = d.porMesCategoria.map { m ->
                Pilha(m.mes.textoCurto(), rubricas.map { g -> Fatia(g.categoria.nome, m.valores[g.categoria.id] ?: 0.0, g.cor(viz)) })
            },
            rotuloLeitura = { d.porMesCategoria[it].mes.texto() },
        )
        Spacer(Modifier.height(12.dp))
        Legenda(rubricas.map { it.categoria.nome to it.cor(viz) })
    }
}

/** "Jan 26" na viragem do ano, "Fev" nos outros — o eixo não precisa de mais. */
internal fun YearMonth.rotuloEixo(): String =
    if (monthValue == 1) "${textoCurto()} ${year % 100}" else textoCurto()

@Composable
private fun BlocoAcumulado(d: DadosGraficos) {
    val viz = LocalViz.current
    val serie = d.acumulado
    val total = serie.last().second
    Secao(
        titulo = "Gasto acumulado",
        subtitulo = if (d.orcamentoTotal > 0) "Tudo o que já se gastou na obra, mês a mês. O tracejado é o orçamento."
        else "Tudo o que já se gastou na obra, mês a mês.",
    ) {
        LinhaTempo(
            rotulosX = serie.map { it.first.rotuloEixo() },
            series = listOf(SerieLinha("Gasto acumulado", serie.map { it.second }, viz.magnitude)),
            referencia = d.orcamentoTotal.takeIf { it > 0 }?.let { Referencia("Orçamento ${it.eurEixo()}", it) },
            rotuloLeitura = { "Gasto até ao fim de ${serie[it].first.texto()}" },
        )
        if (d.orcamentoTotal > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${(total / d.orcamentoTotal * 100).pct(1)} do orçamento usado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TabelaAlternavel(serie.map { (m, v) -> m.texto() to v.eur() })
    }
}

@Composable
private fun BlocoPrevisao(d: DadosGraficos) {
    val viz = LocalViz.current
    val p = d.previsao() ?: return
    val real = d.acumulado
    val ultimo = real.lastIndex
    val meses = real.map { it.first } + p.projecao.drop(1).map { it.first }
    Secao(
        titulo = "Previsão",
        subtitulo = "A linha contínua é o real; a tracejada continua-a ao ritmo dos últimos meses.",
    ) {
        LinhaTempo(
            rotulosX = meses.map { it.rotuloEixo() },
            series = listOf(
                SerieLinha("Real", real.map { it.second } + List(meses.size - real.size) { null }, viz.magnitude),
                SerieLinha(
                    "Previsão",
                    List(ultimo) { null } + p.projecao.map { it.second },
                    viz.magnitude,
                    tracejada = true,
                ),
            ),
            referencia = d.orcamentoTotal.takeIf { it > 0 }?.let { Referencia("Orçamento ${it.eurEixo()}", it) },
            rotuloLeitura = { i -> if (i <= ultimo) "Real até ${meses[i].texto()}" else "Previsto para ${meses[i].texto()}" },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Ritmo recente: ${p.ritmoMensal.eurCurto()} por mês",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                p.jaExcedeu -> "O orçamento já foi ultrapassado. A este ritmo, cada mês de obra soma mais ${p.ritmoMensal.eurCurto()}."
                p.mesEsgota != null -> "A este ritmo, o orçamento de ${d.orcamentoTotal.eurCurto()} esgota-se em ${p.mesEsgota.texto().lowercase()}."
                else -> "Define o orçamento da obra para ver quando se esgota. Daqui a seis meses, a este ritmo, o total seria " +
                    "${p.projecao.last().second.eurCurto()}."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "É uma estimativa simples: média dos últimos três meses completos, assumindo que o ritmo se mantém.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlocoAtividade(d: DadosGraficos) {
    val primeiro = d.atividade.keys.min()
    val ultimo = maxOf(d.hoje, d.atividade.keys.max())
    // No máximo um ano para trás: mais do que isso não cabe nem interessa.
    val inicio = maxOf(primeiro, ultimo.minusWeeks(52))
    val dias = d.atividade.filterKeys { !it.isBefore(inicio) }
    Secao(
        titulo = "Atividade na obra",
        subtitulo = "Cada quadrado é um dia: pagamentos de obra e eventos do livro. " +
            "${plural(dias.size, "dia", "dias")} com movimento.",
    ) {
        MapaCalor(
            inicio = inicio,
            fim = ultimo,
            contagem = { d.atividade[it]?.total ?: 0 },
            descricao = { dia ->
                val a = d.atividade[dia] ?: return@MapaCalor "nada registado"
                listOfNotNull(
                    a.pagamentos.takeIf { it > 0 }?.let { plural(it, "pagamento", "pagamentos") },
                    a.eventos.takeIf { it > 0 }?.let { plural(it, "evento", "eventos") },
                ).joinToString(", ")
            },
        )
    }
}

@Composable
private fun BlocoOrcamentoPorEspecialidade(comOrcamento: List<GastoCategoria>) {
    val viz = LocalViz.current
    Secao(
        titulo = "Orçamento por especialidade",
        subtitulo = "Quanto já foi gasto face ao que estava previsto.",
    ) {
        comOrcamento.forEachIndexed { i, g ->
            if (i > 0) Spacer(Modifier.height(16.dp))
            val orc = g.categoria.orcamento
            val excedeu = g.total > orc
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Um ponto com a cor da categoria: aqui o enchimento do medidor
                // codifica a severidade, por isso a identidade vive no ponto.
                Box(Modifier.size(10.dp).clip(CircleShape).background(g.cor(viz)))
                Spacer(Modifier.width(8.dp))
                Text(
                    g.categoria.nome,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${g.total.eurCurto()} / ${orc.eurCurto()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (excedeu) viz.critico
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Medidor(gasto = g.total, limite = orc)
            if (excedeu) {
                Spacer(Modifier.height(4.dp))
                // Estado nunca só pela cor: vem com texto.
                Text(
                    "Excedeu em ${(g.total - orc).eurCurto()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = viz.critico,
                )
            }
        }
    }
}

/**
 * Orçamento e real de todas as rubricas **na mesma escala** — ao contrário dos
 * medidores, que mostram percentagens, aqui vê-se que derrapar 10% no pedreiro
 * pesa mais do que derrapar 30% no pintor. A barra é o gasto; o traço vertical
 * marca o orçamento; o que passa do traço fica na cor crítica, com texto.
 */
@Composable
private fun BlocoOrcamentoVsReal(comOrcamento: List<GastoCategoria>) {
    val viz = LocalViz.current
    val traco = MaterialTheme.colorScheme.onSurface
    val linhas = comOrcamento.sortedByDescending { it.categoria.orcamento }
    val escala = linhas.maxOf { maxOf(it.total, it.categoria.orcamento) }.coerceAtLeast(0.01)
    Secao(
        titulo = "Orçamento vs real",
        subtitulo = "Todas as rubricas na mesma escala. A barra é o gasto, o traço é o orçamento.",
    ) {
        linhas.forEachIndexed { i, g ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            val orc = g.categoria.orcamento
            val desvio = g.total - orc
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(g.cor(viz)))
                Spacer(Modifier.width(8.dp))
                Text(
                    g.categoria.nome,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${g.total.eurCurto()} de ${orc.eurCurto()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                val r = 4.dp.toPx()
                val xOrc = (size.width * (orc / escala)).toFloat()
                val xReal = (size.width * (g.total / escala)).toFloat()
                // pista até ao orçamento: o espaço que havia para gastar
                drawRoundRect(viz.grade, Offset.Zero, Size(xOrc, size.height), CornerRadius(r, r))
                val dentro = minOf(xReal, xOrc)
                if (dentro > 0f) drawRoundRect(viz.magnitude, Offset.Zero, Size(dentro, size.height), CornerRadius(r, r))
                if (xReal > xOrc) {
                    drawRoundRect(viz.critico, Offset(xOrc, 0f), Size(xReal - xOrc, size.height), CornerRadius(r, r))
                    drawRect(viz.critico, Offset(xOrc, 0f), Size(minOf(r, xReal - xOrc), size.height))
                }
                drawLine(
                    traco,
                    Offset(xOrc, -3.dp.toPx()),
                    Offset(xOrc, size.height + 3.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (desvio > 0) "${desvio.eurCurto()} acima do orçamento"
                else "Faltam ${abs(desvio).eurCurto()}",
                style = MaterialTheme.typography.bodySmall,
                color = if (desvio > 0) viz.critico else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlocoComparacao(d: DadosGraficos) {
    val c = d.comparacao() ?: return
    Secao(
        titulo = "Este mês em comparação",
        subtitulo = "Gasto de obra em ${c.mes.texto().lowercase()}, até hoje.",
    ) {
        Text(
            c.esteMes.eur(),
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        LinhaComparacao("Mês anterior", c.mesAnterior, c.esteMes)
        Spacer(Modifier.height(8.dp))
        LinhaComparacao("Média mensal", c.media, c.esteMes)
    }
}

/**
 * Uma referência e a diferença para ela. Gastar mais não é por si só mau —
 * numa obra os meses de estrutura custam mais — por isso a seta e o texto dizem
 * a direção, sem cores de alarme.
 */
@Composable
private fun LinhaComparacao(rotulo: String, referencia: Double, atual: Double) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                referencia.eurCurto(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (referencia > 0.0) {
            val dif = atual - referencia
            Icon(
                if (dif >= 0) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${if (dif >= 0) "+" else "−"}${abs(dif / referencia * 100).pct()} " +
                    "(${if (dif >= 0) "+" else "−"}${abs(dif).eurCurto()})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BlocoCustoM2(d: DadosGraficos, onDefinir: () -> Unit) {
    val area = d.areaConstrucao
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDefinir),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Custo por m²",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (area > 0.0) "${area.taxaTexto()} m² · toca para alterar"
                        else "Toca para indicar a área de construção",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Alterar a área",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (area <= 0.0) return@Card
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    (d.resumo.totalPago / area).eurCurto(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "por m², até hoje",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (d.orcamentoTotal > 0.0) {
                    Indicador("Previsto (orçamento)", "${(d.orcamentoTotal / area).eurCurto()}/m²")
                }
                if (d.resumoExtra.totalPago > 0.0) {
                    Indicador(
                        "Com custos fora da obra",
                        "${((d.resumo.totalPago + d.resumoExtra.totalPago) / area).eurCurto()}/m²",
                    )
                }
            }
        }
    }
}

/**
 * Uma razão contra um limite — o consumo do orçamento da obra toda. É um
 * medidor, não um gráfico: há um número e um teto, e o que interessa é a
 * distância entre os dois.
 */
@Composable
private fun CartaoOrcamentoTotal(
    gasto: Double,
    orcamento: Double,
    onDefinir: () -> Unit,
) {
    val viz = LocalViz.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDefinir),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Orçamento da obra",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (orcamento > 0.0) "Toca para alterar"
                        else "Toca para definir o valor previsto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Alterar orçamento",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (orcamento <= 0.0) return@Card

            val pct = gasto / orcamento * 100.0
            val restante = orcamento - gasto
            val excedeu = restante < 0

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    pct.pct(1),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (excedeu) viz.critico else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "consumido",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            Medidor(gasto = gasto, limite = orcamento, altura = 14)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${gasto.eurCurto()} de ${orcamento.eurCurto()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // O estado nunca vive só na cor da barra: vem com texto.
                Text(
                    if (excedeu) "Excedeu ${(-restante).eurCurto()}"
                    else "Faltam ${restante.eurCurto()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (excedeu) viz.critico else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Diálogo de um só número — o orçamento da obra, a área de construção. */
@Composable
private fun DialogoValor(
    titulo: String,
    explicacao: String,
    rotulo: String,
    sufixo: String,
    exemplo: String,
    atual: Double,
    onGuardar: (Double) -> Unit,
    onFechar: () -> Unit,
) {
    var texto by remember {
        mutableStateOf(atual.takeIf { it > 0.0 }?.let { formatarEntrada(it) } ?: "")
    }
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(titulo) },
        text = {
            Column {
                Text(
                    explicacao,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.filtrarNumerico() },
                    label = { Text(rotulo) },
                    suffix = { Text(sufixo) },
                    placeholder = { Text(exemplo) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(texto.paraNumero() ?: 0.0) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
    )
}

/**
 * "Ver como tabela": os mesmos números em texto, para quem não consegue (ou não
 * quer) ler o gráfico.
 */
@Composable
internal fun TabelaAlternavel(linhas: List<Pair<String, String>>) {
    var tabela by rememberSaveable { mutableStateOf(false) }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = { tabela = !tabela }, contentPadding = PaddingValues(0.dp)) {
        Text(if (tabela) "Esconder tabela" else "Ver como tabela")
    }
    AnimatedVisibility(visible = tabela) {
        Column {
            linhas.forEach { (rotulo, valor) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        rotulo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        valor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun Indicador(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            valor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun Secao(
    titulo: String,
    subtitulo: String? = null,
    conteudo: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitulo != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            conteudo()
        }
    }
}
