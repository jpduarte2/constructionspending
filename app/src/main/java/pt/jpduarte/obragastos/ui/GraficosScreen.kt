package pt.jpduarte.obragastos.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import pt.jpduarte.obragastos.ui.charts.ColunasMensais
import pt.jpduarte.obragastos.ui.charts.Donut
import pt.jpduarte.obragastos.ui.charts.Fatia
import pt.jpduarte.obragastos.ui.charts.Legenda
import pt.jpduarte.obragastos.ui.charts.Medidor
import pt.jpduarte.obragastos.ui.theme.LocalViz
import pt.jpduarte.obragastos.ui.theme.VizPalette
import java.time.YearMonth

/**
 * Um bloco do ecrã de gráficos. O [id] é o que fica gravado na ordem escolhida,
 * por isso nunca deve mudar depois de a aplicação sair para o mundo.
 */
private class Bloco(val id: String, val conteudo: @Composable () -> Unit)

/**
 * A cor da categoria — a mesma que ela tem no ecrã *Categorias*. Usada em todos
 * os gráficos onde as barras representam rubricas, para que uma especialidade
 * se reconheça pela cor em qualquer sítio da aplicação.
 */
private fun GastoCategoria.cor(viz: VizPalette): Color =
    viz.series[categoria.corIndex % viz.series.size]

/**
 * Ordem de fábrica, e também a lista de blocos que a aplicação conhece.
 * Ids de blocos retirados (como o antigo `iva_extra`) desaparecem sozinhos das
 * ordens já gravadas, porque [ordemUtil] só aceita o que está aqui.
 */
private val ORDEM_FABRICA = listOf(
    "resumo",
    "orcamento",
    "extra",
    "iva",
    "categorias",
    "donut",
    "obra_vs_fora",
    "maiores",
    "mensal",
    "orcamentos",
)

/**
 * Junta a ordem gravada com a de fábrica: o que o utilizador escolheu manda,
 * e blocos novos (de uma versão posterior) entram no fim em vez de sumirem.
 */
private fun ordemUtil(gravada: List<String>): List<String> {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraficosScreen(vm: ObraViewModel, padding: PaddingValues) {
    val resumo by vm.resumo.collectAsState()
    val resumoExtra by vm.resumoExtra.collectAsState()
    val porCategoria by vm.porCategoria.collectAsState()
    val porMes by vm.porMes.collectAsState()
    val maiores by vm.maioresPagamentos.collectAsState()
    val orcamentoTotal by vm.orcamentoTotal.collectAsState()
    val ordemGravada by vm.ordemGraficos.collectAsState()

    var editarOrcamento by remember { mutableStateOf(false) }

    if (editarOrcamento) {
        DialogoOrcamentoTotal(
            atual = orcamentoTotal,
            onGuardar = { vm.definirOrcamentoTotal(it); editarOrcamento = false },
            onFechar = { editarOrcamento = false },
        )
    }

    /* ---------- que blocos há para mostrar ---------- */

    val extras = porCategoria.filter { it.totalExtra > 0.0 }
    val comGastos = porCategoria.filter { it.total > 0.0 }
    val comOrcamento = porCategoria.filter { it.categoria.orcamento > 0.0 }

    val blocos = buildList {
        add(Bloco("resumo") { BlocoResumo(resumo, resumoExtra) })
        add(
            Bloco("orcamento") {
                CartaoOrcamentoTotal(
                    gasto = resumo.totalPago,
                    orcamento = orcamentoTotal,
                    onDefinir = { editarOrcamento = true },
                )
            },
        )
        if (resumoExtra.nPagamentos > 0) {
            add(Bloco("extra") { BlocoCustosFora(resumoExtra, extras) })
        }
        if (resumo.ivaPago + resumo.ivaPoupado > 0.0) {
            add(Bloco("iva") { BlocoIva(resumo) })
        }
        if (comGastos.isNotEmpty()) {
            add(Bloco("categorias") { BlocoPorEspecialidade(comGastos) })
        }
        // Com uma só rubrica o anel seria um círculo inteiro: não diz nada.
        if (comGastos.size > 1) {
            add(Bloco("donut") { BlocoPesoCategorias(comGastos) })
        }
        if (resumoExtra.totalPago > 0.0 && resumo.totalPago > 0.0) {
            add(Bloco("obra_vs_fora") { BlocoObraVsFora(resumo, resumoExtra) })
        }
        if (maiores.size > 1) {
            add(Bloco("maiores") { BlocoMaioresPagamentos(maiores) })
        }
        if (porMes.size > 1) {
            add(Bloco("mensal") { BlocoPorMes(porMes) })
        }
        if (comOrcamento.isNotEmpty()) {
            add(Bloco("orcamentos") { BlocoOrcamentoPorEspecialidade(comOrcamento) })
        }
    }

    /* ---------- ordem escolhida pelo utilizador ---------- */

    val ordemBase = ordemUtil(ordemGravada)
    var ordem by remember { mutableStateOf(ordemBase) }
    LaunchedEffect(ordemBase) { ordem = ordemBase }

    val visiveis = ordem.mapNotNull { id -> blocos.firstOrNull { it.id == id } }

    // Só se arrastam os blocos visíveis, mas a ordem gravada tem de continuar a
    // incluir os escondidos — senão bastava um mês sem pagamentos para o bloco
    // dos meses voltar ao sítio de fábrica. Os escondidos ficam nas suas casas e
    // só as casas dos visíveis são reescritas.
    val mover: (Int, Int) -> Unit = { de, para ->
        val ids = visiveis.map { it.id }
        if (de in ids.indices && para in ids.indices) {
            val nova = ids.toMutableList().apply { add(para, removeAt(de)) }
            val casas = ordem.withIndex().filter { it.value in ids }.map { it.index }
            ordem = ordem.toMutableList().apply {
                casas.forEachIndexed { i, casa -> this[casa] = nova[i] }
            }
        }
    }

    val lista = rememberLazyListState()
    val arrasto = rememberEstadoReordenacao(lista, mover)

    // Grava-se quando o dedo larga, não a cada troca: um arrasto do topo ao fundo
    // são dezenas de trocas e não vale a pena martelar a base de dados.
    LaunchedEffect(ordem, arrasto.indiceArrastado) {
        if (arrasto.indiceArrastado == null && ordem != ordemBase) {
            vm.definirOrdemGraficos(ordem)
        }
    }

    Scaffold(
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text("Gráficos") }) },
    ) { interno ->

        LazyColumn(
            Modifier.fillMaxSize(),
            state = lista,
            contentPadding = PaddingValues(
                start = 4.dp,
                end = 16.dp,
                top = interno.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // Sem pagamentos quase tudo desaparece; damos a razão em vez de
            // deixar o ecrã meio vazio.
            if (resumo.nPagamentos == 0) {
                item(key = "aviso") {
                    Text(
                        if (resumoExtra.nPagamentos > 0)
                            "Ainda não há pagamentos que contem para a obra — os gráficos aparecem quando registares o primeiro."
                        else
                            "Registra alguns pagamentos e os gráficos aparecem aqui.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
            }

            itemsIndexed(visiveis, key = { _, bloco -> bloco.id }) { i, bloco ->
                Row(
                    modifier = Modifier.blocoReordenavel(arrasto, i),
                    verticalAlignment = Alignment.Top,
                ) {
                    PegaArrasto(
                        estado = arrasto,
                        indice = i,
                        total = visiveis.size,
                        chave = bloco.id,
                        aoMover = mover,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Box(Modifier.weight(1f)) { bloco.conteudo() }
                }
            }
        }
    }
}

/* ------------------------------- blocos ------------------------------- */

@Composable
private fun BlocoResumo(resumo: ResumoObra, resumoExtra: ResumoObra) {
    Card(
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

@Composable
private fun BlocoPorMes(porMes: List<Pair<YearMonth, Double>>) {
    var tabela by rememberSaveable { mutableStateOf(false) }
    Secao(
        titulo = "Gastos por mês",
        subtitulo = "Toca numa coluna para ver o valor do mês.",
    ) {
        ColunasMensais(dados = porMes.map { (mes, valor) -> Fatia(mes.textoCurto(), valor) })
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { tabela = !tabela }, contentPadding = PaddingValues(0.dp)) {
            Text(if (tabela) "Esconder tabela" else "Ver como tabela")
        }
        // Vista em tabela: os mesmos números em texto, para quem não consegue
        // ler o gráfico.
        AnimatedVisibility(visible = tabela) {
            Column {
                porMes.forEach { (mes, valor) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            mes.texto(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            valor.eur(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
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

@Composable
private fun DialogoOrcamentoTotal(
    atual: Double,
    onGuardar: (Double) -> Unit,
    onFechar: () -> Unit,
) {
    var texto by remember {
        mutableStateOf(atual.takeIf { it > 0.0 }?.let { formatarEntrada(it) } ?: "")
    }
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Orçamento da obra") },
        text = {
            Column {
                Text(
                    "O valor que estimas gastar do início ao fim. Podes alterá-lo " +
                        "sempre que quiseres — serve de referência, não trava nada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.filtrarNumerico() },
                    label = { Text("Orçamento previsto") },
                    suffix = { Text("€") },
                    placeholder = { Text("300000") },
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

@Composable
private fun Indicador(rotulo: String, valor: String, modifier: Modifier = Modifier) {
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
private fun Secao(
    titulo: String,
    subtitulo: String? = null,
    conteudo: @Composable () -> Unit,
) {
    Card(
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
