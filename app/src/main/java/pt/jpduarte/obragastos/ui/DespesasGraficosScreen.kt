package pt.jpduarte.obragastos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.jpduarte.obragastos.data.RUBRICA_PRESTACAO
import pt.jpduarte.obragastos.ui.charts.BarrasHorizontais
import pt.jpduarte.obragastos.ui.charts.ColunasEmpilhadas
import pt.jpduarte.obragastos.ui.charts.ColunasMensais
import pt.jpduarte.obragastos.ui.charts.Fatia
import pt.jpduarte.obragastos.ui.charts.Legenda
import pt.jpduarte.obragastos.ui.charts.Pilha
import pt.jpduarte.obragastos.ui.theme.LocalViz

/**
 * Os gráficos das despesas da casa, num ecrã à parte da lista — abre-se pelo
 * botão "Ver gráficos" do separador Despesas. Ficam fora do separador Gráficos
 * de propósito: esse é sobre a obra, e estas despesas não são obra.
 *
 * O filtro de rubrica vem da lista (se lá estava uma escolhida) e pode mudar-se
 * aqui; todos os gráficos passam a contar só essa rubrica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DespesasGraficosScreen(vm: ObraViewModel, rubricaInicial: String?, onSair: () -> Unit) {
    val despesas by vm.despesas.collectAsState()
    var filtro by rememberSaveable { mutableStateOf(rubricaInicial) }

    val todas = remember(despesas) { despesas.map { it.rubrica }.toSet() }
    val rubricas = remember(todas) { todas.sortedBy { slotRubrica(it, todas) } }
    val visiveis = remember(despesas, filtro) { if (filtro == null) despesas else despesas.filter { it.rubrica == filtro } }
    val r = remember(visiveis) { resumoDespesas(visiveis) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gráficos das despesas") },
                navigationIcon = {
                    IconButton(onClick = onSair) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { interno ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = interno.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (rubricas.size > 1) {
                item(key = "filtro") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { FilterChip(selected = filtro == null, onClick = { filtro = null }, label = { Text("Todas") }) }
                        items(rubricas) { rb ->
                            FilterChip(
                                selected = filtro == rb,
                                onClick = { filtro = if (filtro == rb) null else rb },
                                label = { Text(rb) },
                            )
                        }
                    }
                }
            }

            if (r.n == 0) {
                item(key = "vazio") {
                    Text(
                        "Sem despesas para mostrar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                return@LazyColumn
            }

            item(key = "resumo") {
                Moldura {
                    Secao(
                        titulo = if (filtro == null) "Custo real" else "Custo real · $filtro",
                        subtitulo = "O que se perdeu de facto. O capital amortizado não conta: é o empréstimo a ser devolvido.",
                    ) {
                        Text(
                            r.custoReal.eur(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Indicador("Total pago", r.total.eurCurto())
                            if (r.capital > 0) Indicador("Capital amortizado", r.capital.eurCurto())
                            Indicador("Despesas", r.n.toString())
                        }
                    }
                }
            }

            // Só faz sentido com mais de uma rubrica — com uma, é uma barra só.
            if (r.porRubrica.count { it.custoReal > 0 } > 1) {
                item(key = "rubricas") { Moldura { BlocoPorRubrica(r) } }
            }
            if (r.porMes.size > 1) {
                item(key = "mensal") { Moldura { BlocoCustoPorMes(r) } }
            }
            if (r.prestacoes.isNotEmpty() && (filtro == null || filtro == RUBRICA_PRESTACAO)) {
                item(key = "prestacoes") { Moldura { BlocoJurosCapital(r.prestacoes) } }
            }
        }
    }
}

@Composable
private fun Moldura(conteudo: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 16.dp)) { conteudo() }
}

@Composable
private fun BlocoPorRubrica(r: ResumoDespesas) {
    val viz = LocalViz.current
    Secao(
        titulo = "Custo real por rubrica",
        subtitulo = "Da que mais custou para a que menos. As cores são as das rubricas na lista.",
    ) {
        BarrasHorizontais(
            dados = r.porRubrica.filter { it.custoReal > 0 }
                .map { Fatia(it.rubrica, it.custoReal, viz.series[it.slot % viz.series.size]) },
        )
    }
}

@Composable
private fun BlocoCustoPorMes(r: ResumoDespesas) {
    Secao(
        titulo = "Custo real por mês",
        subtitulo = "Toca numa coluna para ver o valor do mês.",
    ) {
        ColunasMensais(dados = r.porMes.map { (m, v) -> Fatia(m.textoCurto(), v) })
        TabelaAlternavel(r.porMes.map { (m, v) -> m.texto() to v.eur() })
    }
}

/**
 * Cada prestação partida em juros (e o que mais vier com ela — seguros,
 * comissões) e capital. Ao longo do crédito a parte do capital vai crescendo;
 * é o gráfico que mostra quanto da prestação é dinheiro perdido.
 */
@Composable
private fun BlocoJurosCapital(prestacoes: List<PrestacaoMes>) {
    val viz = LocalViz.current
    val corJuros = viz.series[1]
    val corCapital = viz.series[0]
    val juros = prestacoes.sumOf { it.juros }
    val capital = prestacoes.sumOf { it.capital }
    Secao(
        titulo = "Prestação: juros vs capital",
        subtitulo = "Cada coluna é um mês. Toca numa para ver a divisão.",
    ) {
        ColunasEmpilhadas(
            pilhas = prestacoes.map {
                Pilha(it.mes.textoCurto(), listOf(Fatia("Capital", it.capital, corCapital), Fatia("Juros e afins", it.juros, corJuros)))
            },
            rotuloLeitura = { prestacoes[it].mes.texto() },
        )
        Spacer(Modifier.height(12.dp))
        Legenda(listOf("Capital" to corCapital, "Juros e afins" to corJuros))
        Spacer(Modifier.height(12.dp))
        Text(
            "Das prestações pagas (${(juros + capital).eurCurto()}), ${juros.eurCurto()} " +
                "foram juros e afins — ${(juros / (juros + capital) * 100).pct()} de cada euro.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
