package pt.jpduarte.obragastos.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.jpduarte.obragastos.data.ORIGEM_BANCARIO
import pt.jpduarte.obragastos.data.Categoria
import pt.jpduarte.obragastos.data.Definicoes
import pt.jpduarte.obragastos.data.Pagamento
import pt.jpduarte.obragastos.data.data
import pt.jpduarte.obragastos.data.emEuros
import pt.jpduarte.obragastos.data.ivaPoupado
import pt.jpduarte.obragastos.data.totalPago
import pt.jpduarte.obragastos.data.totalPagoCent
import pt.jpduarte.obragastos.ui.theme.LocalViz
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagamentosScreen(
    vm: ObraViewModel,
    padding: PaddingValues,
    onNovo: () -> Unit,
    onEditar: (Long) -> Unit,
    onEscolherGraficos: () -> Unit,
) {
    val pagamentos by vm.pagamentos.collectAsState()
    val categorias by vm.categorias.collectAsState()
    val resumo by vm.resumo.collectAsState()
    val resumoExtra by vm.resumoExtra.collectAsState()
    val tema by vm.tema.collectAsState()
    val escuro = isSystemInDarkTheme().let { sistema ->
        when (tema) {
            Definicoes.TEMA_CLARO -> false
            Definicoes.TEMA_ESCURO -> true
            else -> sistema
        }
    }

    var filtro by rememberSaveable { mutableStateOf(0L) } // 0 = todas

    /* ---------- exportar / importar ---------- */

    val estadoSnackbar = remember { SnackbarHostState() }
    SnackbarDesfazer(vm, estadoSnackbar)
    val resultado by vm.resultadoFicheiro.collectAsState()
    var menuAberto by remember { mutableStateOf(false) }
    var confirmarImportacao by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportador = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) vm.exportarPara(uri) }

    val importador = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) confirmarImportacao = uri }

    LaunchedEffect(resultado) {
        val r = resultado ?: return@LaunchedEffect
        vm.limparResultadoFicheiro()
        when (r) {
            is ObraViewModel.ResultadoFicheiro.Sucesso -> {
                // Depois de uma importação há uma cópia do que lá estava antes:
                // "Desfazer" repõe-na.
                val copia = r.copia
                if (copia == null) {
                    estadoSnackbar.showSnackbar(r.mensagem)
                } else {
                    val escolha = estadoSnackbar.showSnackbar(
                        r.mensagem,
                        actionLabel = "Desfazer",
                        duration = SnackbarDuration.Long,
                    )
                    if (escolha == SnackbarResult.ActionPerformed) vm.reporCopia(copia)
                }
            }
            is ObraViewModel.ResultadoFicheiro.Falha -> estadoSnackbar.showSnackbar("Erro: ${r.mensagem}")
        }
    }

    confirmarImportacao?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmarImportacao = null },
            title = { Text("Importar e substituir tudo?") },
            text = {
                Text(
                    "A importação apaga os pagamentos, despesas, categorias, orçamentos e o " +
                        "livro de obra que tens agora, e põe no lugar o que vier do " +
                        "ficheiro. Fica uma cópia do que há agora: logo a seguir podes desfazer.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.importarDe(uri)
                    confirmarImportacao = null
                }) { Text("Importar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarImportacao = null }) { Text("Cancelar") }
            },
        )
    }

    val visiveis = remember(pagamentos, filtro) {
        if (filtro == 0L) pagamentos else pagamentos.filter { it.categoriaId == filtro }
    }
    val porNome = remember(categorias) { categorias.associateBy { it.id } }

    Scaffold(
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        snackbarHost = { SnackbarHost(estadoSnackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Obra") },
                actions = {
                    IconButton(onClick = { menuAberto = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(
                        expanded = menuAberto,
                        onDismissRequest = { menuAberto = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Exportar para CSV") },
                            leadingIcon = {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                            },
                            onClick = {
                                menuAberto = false
                                exportador.launch(vm.nomeFicheiroSugerido())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Importar de CSV") },
                            leadingIcon = {
                                Icon(Icons.Outlined.FileUpload, contentDescription = null)
                            },
                            onClick = {
                                menuAberto = false
                                // Muitos gestores de ficheiros classificam CSV de
                                // maneiras diferentes; aceitamos tudo e validamos
                                // o conteúdo ao ler.
                                importador.launch(arrayOf("*/*"))
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Escolher gráficos") },
                            leadingIcon = {
                                Icon(Icons.Outlined.BarChart, contentDescription = null)
                            },
                            onClick = {
                                menuAberto = false
                                onEscolherGraficos()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (escuro) "Modo claro" else "Modo escuro") },
                            leadingIcon = {
                                Icon(
                                    if (escuro) Icons.Outlined.LightMode
                                    else Icons.Outlined.DarkMode,
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = {
                                Switch(
                                    checked = escuro,
                                    onCheckedChange = { vm.alternarTema(escuro) },
                                )
                            },
                            onClick = {
                                menuAberto = false
                                vm.alternarTema(escuro)
                            },
                        )
                        // Depois de escolher à mão, deixa de seguir o telemóvel;
                        // esta opção devolve esse comportamento.
                        if (tema != Definicoes.TEMA_SISTEMA) {
                            DropdownMenuItem(
                                text = { Text("Seguir o sistema") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.BrightnessAuto,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuAberto = false
                                    vm.seguirTemaDoSistema()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNovo,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Pagamento") },
            )
        },
    ) { interno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = interno.calculateTopPadding() + 8.dp,
                bottom = 96.dp,
            ),
        ) {
            item {
                CartaoTotal(resumo, resumoExtra, Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(12.dp))
            }

            if (categorias.size > 1) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = filtro == 0L,
                                onClick = { filtro = 0L },
                                label = { Text("Todas") },
                            )
                        }
                        items(categorias, key = { it.id }) { c ->
                            FilterChip(
                                selected = filtro == c.id,
                                onClick = { filtro = if (filtro == c.id) 0L else c.id },
                                label = { Text(c.nome) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (visiveis.isEmpty()) {
                item { EstadoVazio(temPagamentos = pagamentos.isNotEmpty()) }
            } else {
                // Agrupado por mês, com o subtotal do mês no cabeçalho.
                val grupos = visiveis.groupBy { YearMonth.from(it.data) }
                grupos.forEach { (mes, doMes) ->
                    item(key = "cab-$mes") {
                        CabecalhoMes(mes, doMes.sumOf { it.totalPagoCent }.emEuros())
                    }
                    items(doMes, key = { it.id }) { p ->
                        LinhaPagamento(
                            pagamento = p,
                            categoria = porNome[p.categoriaId],
                            onClick = { onEditar(p.id) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Figura principal do ecrã: o total da obra. Um número grande vale mais que um
 * gráfico de uma barra — e há exatamente um por vista.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoTotal(
    resumo: ResumoObra,
    extra: ResumoObra,
    modifier: Modifier = Modifier,
) {
    val viz = LocalViz.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Total gasto na obra",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                resumo.totalPago.eur(),
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MiniDado("Sem IVA", resumo.totalBase.eurCurto())
                MiniDado("IVA pago", resumo.ivaPago.eurCurto())
                if (resumo.ivaPoupado > 0) {
                    MiniDado("IVA poupado", resumo.ivaPoupado.eurCurto(), viz.tintaPositiva)
                }
                MiniDado("Pagamentos", resumo.nPagamentos.toString())
            }

            // Os custos fora da obra ficam à parte, para o total acima não mentir.
            if (extra.nPagamentos > 0) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Custos fora da obra",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            plural(extra.nPagamentos, "pagamento", "pagamentos"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        extra.totalPago.eur(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniDado(
    rotulo: String,
    valor: String,
    corValor: androidx.compose.ui.graphics.Color? = null,
) {
    Column {
        Text(
            rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = corValor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CabecalhoMes(mes: YearMonth, subtotal: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            mes.texto(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            subtotal.eurCurto(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinhaPagamento(
    pagamento: Pagamento,
    categoria: Categoria?,
    onClick: () -> Unit,
) {
    val viz = LocalViz.current
    val cor = categoria?.let { viz.series[it.corIndex % viz.series.size] } ?: viz.esbatido

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A cor vive no ponto, nunca no texto.
        Box(Modifier.size(10.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                pagamento.descricao.ifBlank { categoria?.nome ?: "Pagamento" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(categoria?.nome ?: "—")
                    append(" · ")
                    append(pagamento.data.textoCurto())
                    if (pagamento.fornecedor.isNotBlank()) append(" · ").append(pagamento.fornecedor)
                    if (!pagamento.contaParaObra) append(" · fora da obra")
                    if (pagamento.origemFundos == ORIGEM_BANCARIO) append(" · bancário")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                pagamento.totalPago.eur(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (pagamento.ivaPoupado > 0.0) {
                // Cobre tanto o IVA não pago como o que a parte não tributada
                // teria custado — em qualquer dos casos é dinheiro que não saiu.
                Text(
                    "poupou ${pagamento.ivaPoupado.eurCurto()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = viz.tintaPositiva,
                )
            } else {
                Text(
                    "c/ IVA ${pagamento.taxaIva.taxaTexto()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EstadoVazio(temPagamentos: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (temPagamentos) "Sem pagamentos nesta categoria" else "Ainda não registaste pagamentos",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (temPagamentos) "Escolhe outra categoria ou limpa o filtro."
            else "Toca em “Pagamento” para registar o primeiro.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
