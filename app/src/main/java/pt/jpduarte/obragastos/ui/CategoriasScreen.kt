package pt.jpduarte.obragastos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.jpduarte.obragastos.data.Categoria
import pt.jpduarte.obragastos.data.Pagamento
import pt.jpduarte.obragastos.data.data
import pt.jpduarte.obragastos.data.totalPago
import pt.jpduarte.obragastos.ui.charts.Medidor
import pt.jpduarte.obragastos.ui.theme.LocalViz
import pt.jpduarte.obragastos.ui.theme.N_CORES_CATEGORIA

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriasScreen(vm: ObraViewModel, padding: PaddingValues) {
    val gastos by vm.porCategoria.collectAsState()
    var aEditar by remember { mutableStateOf<Categoria?>(null) }
    var novo by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text("Categorias") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { novo = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Categoria") },
            )
        },
    ) { interno ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = interno.calculateTopPadding() + 8.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Toca numa categoria para ver os pagamentos, ou no lápis para lhe " +
                        "mudar o nome, a cor ou o orçamento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(gastos, key = { it.categoria.id }) { g ->
                CartaoCategoria(g, onEditar = { aEditar = g.categoria })
            }
        }
    }

    if (novo) {
        val usadas = gastos.mapTo(mutableSetOf()) { it.categoria.corIndex }
        DialogoCategoria(
            categoria = null,
            // A primeira cor ainda livre, para duas categorias não nascerem iguais.
            corSugerida = (0 until N_CORES_CATEGORIA).firstOrNull { it !in usadas }
                ?: (gastos.size % N_CORES_CATEGORIA),
            onGuardar = { vm.guardar(it); novo = false },
            onFechar = { novo = false },
            onApagar = null,
        )
    }

    aEditar?.let { c ->
        // Todos os pagamentos, obra e fora da obra: o CASCADE leva-os a todos.
        val nPagamentos = gastos.firstOrNull { it.categoria.id == c.id }?.nTodos ?: 0
        DialogoCategoria(
            categoria = c,
            corSugerida = c.corIndex,
            onGuardar = { vm.guardar(it); aEditar = null },
            onFechar = { aEditar = null },
            onApagar = { destino -> vm.apagar(c, destino); aEditar = null },
            nPagamentos = nPagamentos,
            outras = gastos.map { it.categoria }.filter { it.id != c.id },
        )
    }
}

@Composable
private fun CartaoCategoria(g: GastoCategoria, onEditar: () -> Unit) {
    val viz = LocalViz.current
    val cor = viz.series[g.categoria.corIndex % viz.series.size]
    val orcamento = g.categoria.orcamento
    var aberto by rememberSaveable(g.categoria.id) { mutableStateOf(false) }
    val podeAbrir = g.pagamentos.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // A faixa na cor da categoria, como nos pagamentos.
            Box(Modifier.width(6.dp).fillMaxHeight().background(cor))
            Column(
                Modifier
                    .clickable(enabled = podeAbrir) { aberto = !aberto }
                    .padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Com orçamento: um anel com a percentagem gasta (verde, laranja
                    // perto do limite, vermelho acima). Sem orçamento: a inicial.
                    if (orcamento > 0.0) {
                        val fracao = g.total / orcamento
                        AnelProgresso(fracao, corOrcamento(fracao), tamanho = 52.dp) {
                            Text(
                                (fracao * 100).pct(0),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (fracao > 1.0) viz.critico else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        IconeCategoria(g.categoria.nome, cor, tamanho = 44.dp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            g.categoria.nome,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            g.totalGeral.eurCurto(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onEditar, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Editar ${g.categoria.nome}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (podeAbrir) {
                        Icon(
                            if (aberto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (aberto) "Fechar pagamentos" else "Ver pagamentos",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    resumoDaCategoria(g),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (orcamento > 0.0) {
                    // O anel mede-se contra o gasto de obra: o orçamento é da
                    // obra, e os custos fora dela não devem consumi-lo.
                    Spacer(Modifier.height(2.dp))
                    val restante = orcamento - g.total
                    Text(
                        if (restante >= 0)
                            "Faltam ${restante.eurCurto()} de ${orcamento.eurCurto()}"
                        else
                            "Excedeu o orçamento em ${(-restante).eurCurto()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (restante >= 0) MaterialTheme.colorScheme.onSurfaceVariant
                        else viz.critico,
                    )
                }

                AnimatedVisibility(visible = aberto) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        g.pagamentos.forEach { p ->
                            LinhaPagamentoCategoria(p)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Uma linha por baixo do nome que diz de onde vem o total: quantos pagamentos
 * são, e quanto é obra e quanto não é — mas só menciona a divisão quando ela
 * existe, para não carregar o ecrã de quem nunca usa custos fora da obra.
 */
private fun resumoDaCategoria(g: GastoCategoria): String = when {
    g.nTodos == 0 -> "Sem pagamentos"
    g.nExtra == 0 -> plural(g.nTodos, "pagamento", "pagamentos")
    g.nPagamentos == 0 ->
        "${plural(g.nTodos, "pagamento", "pagamentos")} · tudo fora da obra"
    else ->
        "${plural(g.nTodos, "pagamento", "pagamentos")} · " +
            "obra ${g.total.eurCurto()} · fora da obra ${g.totalExtra.eurCurto()}"
}

@Composable
private fun LinhaPagamentoCategoria(p: Pagamento) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                p.descricao.ifBlank { "(sem descrição)" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (p.contaParaObra) p.data.texto()
                else "${p.data.texto()} · fora da obra",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            p.totalPago.eur(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoCategoria(
    categoria: Categoria?,
    corSugerida: Int,
    onGuardar: (Categoria) -> Unit,
    onFechar: () -> Unit,
    onApagar: ((destino: Long?) -> Unit)?,
    nPagamentos: Int = 0,
    outras: List<Categoria> = emptyList(),
) {
    val viz = LocalViz.current
    var nome by remember { mutableStateOf(categoria?.nome ?: "") }
    var cor by remember { mutableIntStateOf(categoria?.corIndex ?: corSugerida) }
    var orcamentoTexto by remember {
        mutableStateOf(categoria?.orcamento?.takeIf { it > 0.0 }?.let { formatarEntrada(it) } ?: "")
    }
    var confirmarApagar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(if (categoria == null) "Nova categoria" else "Editar categoria") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome") },
                    placeholder = { Text("ex: Serralheiro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = orcamentoTexto,
                    onValueChange = { orcamentoTexto = it.filtrarNumerico() },
                    label = { Text("Orçamento (opcional)") },
                    suffix = { Text("€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Rotulo("Cor")
                Spacer(Modifier.height(8.dp))
                SeletorCor(selecionada = cor, aoEscolher = { cor = it })
                if (onApagar != null && nPagamentos > 0) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (nPagamentos == 1)
                            "Esta categoria tem um pagamento. Ao eliminá-la podes passá-lo para outra categoria."
                        else
                            "Esta categoria tem $nPagamentos pagamentos. Ao eliminá-la podes passá-los para outra categoria.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = {
                    onGuardar(
                        Categoria(
                            id = categoria?.id ?: 0L,
                            nome = nome.trim(),
                            corIndex = cor,
                            orcamento = orcamentoTexto.paraNumero() ?: 0.0,
                        ),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onApagar != null) {
                    IconButton(onClick = { confirmarApagar = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onFechar) { Text("Cancelar") }
            }
        },
    )

    if (confirmarApagar && onApagar != null) {
        // Por omissão os pagamentos mudam de categoria em vez de desaparecerem:
        // apagá-los tem de ser uma escolha explícita.
        var destino by remember { mutableStateOf(outras.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { confirmarApagar = false },
            title = { Text("Eliminar categoria?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (nPagamentos == 0) {
                        Text("Esta ação não pode ser desfeita.")
                    } else {
                        Text(
                            if (nPagamentos == 1) "O que fazer ao pagamento desta categoria?"
                            else "O que fazer aos $nPagamentos pagamentos desta categoria?",
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            outras.forEach { o ->
                                FilterChip(
                                    selected = destino == o.id,
                                    onClick = { destino = o.id },
                                    label = { Text("Passar para ${o.nome}") },
                                )
                            }
                            FilterChip(
                                selected = destino == null,
                                onClick = { destino = null },
                                label = { Text("Apagá-los também") },
                            )
                        }
                        if (destino == null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Os pagamentos são apagados e não há como desfazer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = viz.critico,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmarApagar = false; onApagar(if (nPagamentos == 0) null else destino) }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar") }
            },
        )
    }
}

/**
 * Escolha de um slot da paleta categórica. Devolve o índice, não a cor: é o
 * índice que se guarda, para a mesma categoria ter a sua cor no tema claro e no
 * escuro.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeletorCor(selecionada: Int, aoEscolher: (Int) -> Unit) {
    val viz = LocalViz.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        viz.series.forEachIndexed { i, c ->
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(c)
                    .then(
                        if (i == selecionada) Modifier.border(
                            3.dp,
                            MaterialTheme.colorScheme.onSurface,
                            CircleShape,
                        ) else Modifier,
                    )
                    .clickable { aoEscolher(i) },
            )
        }
    }
}
