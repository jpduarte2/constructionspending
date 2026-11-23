package pt.jpduarte.obragastos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Escolher e ordenar os gráficos.
 *
 * Mostra todos os blocos que a aplicação conhece — também os que agora não têm
 * dados —, cada um com um interruptor para aparecer ou não no ecrã de gráficos
 * e uma pega para o arrastar. "Ver exemplo" desenha o bloco: com os dados
 * verdadeiros quando os há, senão com os de uma obra inventada ([Exemplo]),
 * para se perceber o que ele mostra antes de o escolher.
 *
 * As alterações gravam-se logo; não há botão de guardar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscolherGraficosScreen(vm: ObraViewModel, onSair: () -> Unit) {
    val dados by vm.dadosGraficos.collectAsState()
    val ordemGravada by vm.ordemGraficos.collectAsState()
    val escondidosGravados by vm.graficosEscondidos.collectAsState()

    val ordemBase = ordemUtil(ordemGravada)
    var ordem by remember { mutableStateOf(ordemBase) }
    var escondidos by remember { mutableStateOf(escondidosGravados) }
    LaunchedEffect(ordemBase) { ordem = ordemBase }
    LaunchedEffect(escondidosGravados) { escondidos = escondidosGravados }

    val mover: (Int, Int) -> Unit = { de, para ->
        if (de in ordem.indices && para in ordem.indices) {
            ordem = ordem.toMutableList().apply { add(para, removeAt(de)) }
        }
    }

    val lista = rememberLazyListState()
    val arrasto = rememberEstadoReordenacao(lista, mover)

    // Grava-se quando o dedo larga, não a cada troca: um arrasto do topo ao fundo
    // são dezenas de trocas e não vale a pena martelar a base de dados.
    LaunchedEffect(ordem, escondidos, arrasto.indiceArrastado) {
        if (arrasto.indiceArrastado == null && (ordem != ordemBase || escondidos != escondidosGravados)) {
            vm.definirGraficos(ordem, escondidos)
        }
    }

    var menu by remember { mutableStateOf(false) }
    val nVisiveis = ordem.count { it !in escondidos }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escolher gráficos") },
                navigationIcon = {
                    IconButton(onClick = onSair) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Mostrar todos") },
                                onClick = { menu = false; escondidos = emptySet() },
                            )
                            DropdownMenuItem(
                                text = { Text("Esconder todos") },
                                onClick = { menu = false; escondidos = ordem.toSet() },
                            )
                            DropdownMenuItem(
                                text = { Text("Repor a ordem de fábrica") },
                                onClick = { menu = false; ordem = CATALOGO_GRAFICOS.map { it.id } },
                            )
                        }
                    }
                },
            )
        },
    ) { interno ->
        // O texto fica fora da lista: assim os índices da lista são os da ordem,
        // que é o que a pega de arrasto espera.
        Column(Modifier.fillMaxSize().padding(top = interno.calculateTopPadding())) {
                Text(
                    "Liga os que queres ver no ecrã Gráficos e arrasta pela pega ☰ para os " +
                        "ordenar (ou toca na pega para um menu). $nVisiveis de ${ordem.size} ligados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                )
        LazyColumn(
            Modifier.fillMaxSize(),
            state = lista,
            contentPadding = PaddingValues(start = 4.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(ordem, key = { _, id -> id }) { i, id ->
                val def = definicaoDe(id) ?: return@itemsIndexed
                Row(
                    modifier = Modifier.blocoReordenavel(arrasto, i),
                    verticalAlignment = Alignment.Top,
                ) {
                    PegaArrasto(
                        estado = arrasto,
                        indice = i,
                        total = ordem.size,
                        chave = id,
                        aoMover = mover,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    CartaoEscolha(
                        def = def,
                        dados = dados,
                        ligado = id !in escondidos,
                        aoLigar = { ligar -> escondidos = if (ligar) escondidos - id else escondidos + id },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun CartaoEscolha(
    def: DefinicaoGrafico,
    dados: DadosGraficos,
    ligado: Boolean,
    aoLigar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var aberto by rememberSaveable(def.id) { mutableStateOf(false) }
    val temDados = def.temDados(dados)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Row(
                Modifier.clickable(role = Role.Switch) { aoLigar(!ligado) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).alpha(if (ligado) 1f else 0.6f)) {
                    Text(
                        def.titulo,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        def.descricao,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!temDados) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Ainda sem dados — aparece quando os houver.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = ligado, onCheckedChange = aoLigar)
            }
            Row(
                Modifier
                    .clickable { aberto = !aberto }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        aberto -> "Esconder exemplo"
                        temDados -> "Ver como fica"
                        else -> "Ver exemplo"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    if (aberto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(visible = aberto) {
                Column(Modifier.padding(end = 8.dp, bottom = 8.dp)) {
                    if (!temDados) {
                        Text(
                            "Com dados de exemplo:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    ) {
                        def.conteudo(if (temDados) dados else Exemplo.dados, AcoesGraficos.NENHUMA)
                    }
                }
            }
        }
    }
}
