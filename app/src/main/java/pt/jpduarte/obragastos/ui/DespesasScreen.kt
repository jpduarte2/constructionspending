package pt.jpduarte.obragastos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.jpduarte.obragastos.data.Despesa
import pt.jpduarte.obragastos.data.custoRealCent
import pt.jpduarte.obragastos.data.data
import pt.jpduarte.obragastos.data.emEuros
import pt.jpduarte.obragastos.ui.theme.LocalViz
import java.time.YearMonth

/**
 * Despesas da casa que não são construção: o banco, os seguros, as contas.
 *
 * O número principal é o **custo real** — o que se perdeu de facto. Uma
 * prestação do crédito é sobretudo capital, e o capital não é custo novo: é
 * devolver o dinheiro que já pagou a obra. Por isso aparece à parte, e nada
 * disto entra no custo da obra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DespesasScreen(
    vm: ObraViewModel,
    padding: PaddingValues,
    onNova: () -> Unit,
    onEditar: (Long) -> Unit,
    onVerGraficos: (rubrica: String?) -> Unit,
    onFixas: () -> Unit,
) {
    val despesas by vm.despesas.collectAsState()
    val fixas by vm.despesasFixas.collectAsState()
    val lancadas by vm.lancadas.collectAsState()
    val estadoSnackbar = remember { SnackbarHostState() }
    SnackbarDesfazer(vm, estadoSnackbar)

    // Ao abrir o separador, lança as fixas que entretanto venceram (a app pode
    // ter ficado aberta em segundo plano desde o mês passado).
    LaunchedEffect(Unit) { vm.lancarDespesasFixas() }
    LaunchedEffect(lancadas) {
        if (lancadas > 0) {
            vm.esquecerLancadas()
            estadoSnackbar.showSnackbar(
                if (lancadas == 1) "Lançada 1 despesa recorrente." else "Lançadas $lancadas despesas recorrentes.",
            )
        }
    }

    var filtro by rememberSaveable { mutableStateOf<String?>(null) }
    var soPorConfirmar by rememberSaveable { mutableStateOf(false) }
    val porConfirmar = despesas.count { it.aConfirmar }
    if (porConfirmar == 0) soPorConfirmar = false
    val rubricas = remember(despesas) { despesas.map { it.rubrica }.distinct().sortedBy { slotRubrica(it, despesas.map { d -> d.rubrica }) } }
    val visiveis = remember(despesas, filtro, soPorConfirmar) {
        despesas.filter { (filtro == null || it.rubrica == filtro) && (!soPorConfirmar || it.aConfirmar) }
    }
    val resumo = remember(visiveis) { resumoDespesas(visiveis) }
    val todas = remember(despesas) { despesas.map { it.rubrica }.toSet() }

    Scaffold(
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        snackbarHost = { SnackbarHost(estadoSnackbar) },
        topBar = { TopAppBar(title = { Text("Despesas da casa") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNova,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Despesa") },
            )
        },
    ) { interno ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = interno.calculateTopPadding() + 8.dp, bottom = 96.dp),
        ) {
            item {
                CartaoResumoDespesas(resumo, filtro, Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(12.dp))
                // Os gráficos ficam num ecrã à parte: a lista é o que se usa no
                // dia a dia, e não deve ficar empurrada para baixo.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (despesas.isNotEmpty()) {
                        OutlinedButton(onClick = { onVerGraficos(filtro) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ver gráficos", maxLines = 1)
                        }
                    }
                    OutlinedButton(onClick = onFixas, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.EventRepeat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (fixas.isEmpty()) "Recorrentes" else "Recorrentes (${fixas.size})", maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (porConfirmar > 0) {
                    AvisoPorConfirmar(
                        n = porConfirmar,
                        ativo = soPorConfirmar,
                        aoTocar = { soPorConfirmar = !soPorConfirmar },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (rubricas.size > 1) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(selected = filtro == null, onClick = { filtro = null }, label = { Text("Todas") })
                        }
                        items(rubricas) { r ->
                            FilterChip(
                                selected = filtro == r,
                                onClick = { filtro = if (filtro == r) null else r },
                                label = { Text(r) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (despesas.isEmpty()) {
                item { EstadoVazioDespesas() }
            } else if (visiveis.isEmpty()) {
                item {
                    Text(
                        "Nada a mostrar com este filtro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                visiveis.groupBy { YearMonth.from(it.data) }.forEach { (mes, doMes) ->
                    item(key = "cab-$mes") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(mes.texto(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    plural(doMes.size, "despesa", "despesas"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                doMes.sumOf { it.valorCent }.emEuros().eurCurto(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(doMes, key = { it.id }) { d ->
                        LinhaDespesa(d, slotRubrica(d.rubrica, todas), onClick = { onEditar(d.id) })
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoResumoDespesas(r: ResumoDespesas, filtro: String?, modifier: Modifier = Modifier) {
    CartaoDestaque(modifier) {
        Text(
            if (filtro == null) "Custo real das despesas" else "Custo real · $filtro",
            style = MaterialTheme.typography.labelLarge,
            color = corNoDestaqueSuave,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            r.custoReal.eur(),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = corNoDestaque,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DadoDestaque("Total pago", r.total.eurCurto())
            if (r.capital > 0) DadoDestaque("Capital amortizado", r.capital.eurCurto())
            DadoDestaque("Despesas", r.n.toString())
        }
        if (r.capital > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "O capital amortizado não é custo: é o empréstimo que pagou a obra a ser devolvido. " +
                    "Por isso fica fora do custo real.",
                style = MaterialTheme.typography.bodySmall,
                color = corNoDestaqueSuave,
            )
        }
    }
}

@Composable
private fun DadoDestaque(rotulo: String, valor: String) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = corNoDestaqueSuave)
        Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = corNoDestaque)
    }
}

@Composable
private fun LinhaDespesa(d: Despesa, slot: Int, onClick: () -> Unit) {
    val viz = LocalViz.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeCategoria(d.rubrica, viz.series[slot % viz.series.size])
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.descricao.ifBlank { d.rubrica },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (d.aConfirmar) {
                // Estado com ícone e texto, nunca só pela cor.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.PendingActions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Valor estimado — toca para confirmar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                buildString {
                    append(d.rubrica).append(" · ").append(d.data.textoCurto())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (d.fixaId != null) {
                Spacer(Modifier.height(4.dp))
                Selo("recorrente", viz.magnitude)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                d.valorCent.emEuros().eur(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (d.capitalCent > 0) {
                Text(
                    "custo ${d.custoRealCent.emEuros().eurCurto()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Despesas lançadas com valor estimado. Tocar mostra só essas (e tocar outra
 * vez volta a mostrar tudo), para se confirmarem de seguida.
 */
@Composable
private fun AvisoPorConfirmar(n: Int, ativo: Boolean, aoTocar: () -> Unit, modifier: Modifier = Modifier) {
    val viz = LocalViz.current
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = aoTocar),
        colors = CardDefaults.cardColors(containerColor = viz.aviso.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PendingActions, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (n == 1) "1 despesa por confirmar" else "$n despesas por confirmar",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (ativo) "A mostrar só estas. Toca para ver todas."
                    else "Lançadas com o valor da vez anterior. Toca para ver só estas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EstadoVazioDespesas() {
    EstadoVazioIlustrado(
        icone = Icons.Outlined.AccountBalance,
        titulo = "Ainda não há despesas",
        texto = "Regista aqui o que a casa custa sem ser obra: prestações e comissões do banco, " +
            "seguros, água, luz, impostos. Ficam à parte do custo da construção.",
    )
}
