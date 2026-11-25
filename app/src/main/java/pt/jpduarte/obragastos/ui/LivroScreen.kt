package pt.jpduarte.obragastos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.jpduarte.obragastos.data.Definicoes
import pt.jpduarte.obragastos.data.EventoObra
import pt.jpduarte.obragastos.data.Fotos
import pt.jpduarte.obragastos.data.data
import pt.jpduarte.obragastos.data.juntarFotos
import pt.jpduarte.obragastos.data.listaFotos
import pt.jpduarte.obragastos.ui.theme.LocalViz
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Livro de obra: uma cronologia do que aconteceu, à parte do dinheiro.
 *
 * Por omissão mostra o mais recente em cima — é o que interessa ver ao abrir.
 * O menu da barra de topo troca para ordem cronológica ou para a arrumação
 * manual; as setas de subir/descer também passam o livro a manual, porque
 * mexer à mão é dizer que já não se quer a ordem automática.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivroScreen(vm: ObraViewModel, padding: PaddingValues) {
    val eventos by vm.eventos.collectAsState()
    val ordem by vm.ordemLivro.collectAsState()

    var aEditar by remember { mutableStateOf<EventoObra?>(null) }
    var novo by remember { mutableStateOf(false) }
    var verFotos by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    // Quanto ocupam as fotos — recalculado quando os eventos mudam.
    val nFotos = eventos.sumOf { it.listaFotos.size }
    val espaco by produceState(0L, eventos) { value = vm.espacoFotos() }

    Scaffold(
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text("Livro de obra") },
                actions = {
                    if (eventos.size > 1) {
                        MenuOrdemLivro(
                            modo = ordem,
                            aoEscolher = { vm.definirOrdemLivro(it) },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { novo = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Evento") },
            )
        },
    ) { interno ->
        if (eventos.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(interno)) {
                Spacer(Modifier.height(32.dp))
                EstadoVazioIlustrado(
                    icone = Icons.Outlined.MenuBook,
                    titulo = "O livro está vazio",
                    texto = "Regista aqui o que vai acontecendo na obra — início das " +
                        "movimentações de terras, montagem do estaleiro, vistorias, " +
                        "atrasos. Fica com a data e a ordem que lhe deres.",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = interno.calculateTopPadding() + 8.dp,
                    bottom = 96.dp,
                ),
            ) {
                if (nFotos > 0) {
                    item(key = "espaco") {
                        Text(
                            "${plural(nFotos, "foto", "fotos")} · ${Fotos.tamanhoTexto(espaco)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                    }
                }
                itemsIndexed(eventos, key = { _, e -> e.id }) { i, e ->
                    LinhaEvento(
                        evento = e,
                        primeiro = i == 0,
                        ultimo = i == eventos.lastIndex,
                        onClick = { aEditar = e },
                        onSubir = { vm.mover(e, -1) },
                        onDescer = { vm.mover(e, +1) },
                        onVerFoto = { j -> verFotos = e.listaFotos to j },
                    )
                }
            }
        }
    }

    if (novo) {
        DialogoEvento(
            vm = vm,
            evento = null,
            onGuardar = { ev, retiradas -> vm.guardar(ev, retiradas); novo = false },
            onFechar = { novo = false },
            onApagar = null,
        )
    }

    aEditar?.let { e ->
        DialogoEvento(
            vm = vm,
            evento = e,
            onGuardar = { ev, retiradas -> vm.guardar(ev, retiradas); aEditar = null },
            onFechar = { aEditar = null },
            onApagar = { vm.apagar(e); aEditar = null },
        )
    }

    verFotos?.let { (nomes, i) ->
        VisualizadorFotos(nomes, i, onFechar = { verFotos = null })
    }
}

@Composable
private fun MenuOrdemLivro(modo: String, aoEscolher: (String) -> Unit) {
    var aberto by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { aberto = true }) {
            Icon(Icons.Outlined.Sort, contentDescription = "Ordenar o livro")
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            OpcaoOrdem("Mais recente primeiro", Definicoes.LIVRO_RECENTE, modo) {
                aberto = false; aoEscolher(it)
            }
            OpcaoOrdem("Mais antigo primeiro", Definicoes.LIVRO_ANTIGO, modo) {
                aberto = false; aoEscolher(it)
            }
            OpcaoOrdem("Ordem que eu definir", Definicoes.LIVRO_MANUAL, modo) {
                aberto = false; aoEscolher(it)
            }
        }
    }
}

@Composable
private fun OpcaoOrdem(
    texto: String,
    valor: String,
    escolhido: String,
    aoEscolher: (String) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(texto) },
        onClick = { aoEscolher(valor) },
        // O visto ocupa sempre o espaço, mesmo invisível, para os rótulos
        // ficarem todos alinhados.
        leadingIcon = {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = if (valor == escolhido) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
            )
        },
    )
}

@Composable
private fun LinhaEvento(
    evento: EventoObra,
    primeiro: Boolean,
    ultimo: Boolean,
    onClick: () -> Unit,
    onSubir: () -> Unit,
    onDescer: () -> Unit,
    onVerFoto: (Int) -> Unit,
) {
    val viz = LocalViz.current
    val corPonto = viz.magnitude
    val corLinha = MaterialTheme.colorScheme.outlineVariant

    // IntrinsicSize.Min dá à goteira a altura do conteúdo, para o traço da
    // cronologia ligar o ponto deste evento ao do seguinte.
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .width(28.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2f
                    val yPonto = 28.dp.toPx()
                    val raio = 6.dp.toPx()
                    val halo = 11.dp.toPx()
                    val traco = 2.dp.toPx()
                    if (!primeiro) {
                        drawLine(corLinha, Offset(x, 0f), Offset(x, yPonto - halo), traco)
                    }
                    if (!ultimo) {
                        drawLine(corLinha, Offset(x, yPonto + halo), Offset(x, size.height), traco)
                    }
                    drawCircle(corPonto.copy(alpha = 0.22f), radius = halo, center = Offset(x, yPonto))
                    drawCircle(corPonto, radius = raio, center = Offset(x, yPonto))
                },
        )

        // Cada evento num cartão ao lado da linha do tempo.
        Column(
            Modifier
                .weight(1f)
                .padding(start = 6.dp, top = 6.dp, bottom = 12.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp),
        ) {
            Text(
                evento.data.texto(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = corPonto,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                evento.titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (evento.descricao.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    evento.descricao,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val fotos = evento.listaFotos
            if (fotos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // Até quatro miniaturas; a quarta diz quantas mais há.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fotos.take(MINIATURAS).forEachIndexed { j, nome ->
                        val resto = fotos.size - MINIATURAS
                        Box(contentAlignment = Alignment.Center) {
                            Miniatura(nome, TAMANHO_MINIATURA, onClick = { onVerFoto(j) })
                            if (j == MINIATURAS - 1 && resto > 0) {
                                Box(
                                    Modifier
                                        .size(TAMANHO_MINIATURA)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .clickable { onVerFoto(j) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("+$resto", color = Color.White, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(top = 10.dp)) {
            IconButton(onClick = onSubir, enabled = !primeiro, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.ArrowUpward,
                    contentDescription = "Mover para cima",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDescer, enabled = !ultimo, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.ArrowDownward,
                    contentDescription = "Mover para baixo",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private const val MINIATURAS = 4

private val TAMANHO_MINIATURA = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoEvento(
    vm: ObraViewModel,
    evento: EventoObra?,
    onGuardar: (EventoObra, retiradas: List<String>) -> Unit,
    onFechar: () -> Unit,
    onApagar: (() -> Unit)?,
) {
    var dia by remember { mutableStateOf(evento?.data ?: LocalDate.now()) }
    var titulo by remember { mutableStateOf(evento?.titulo ?: "") }
    var descricao by remember { mutableStateOf(evento?.descricao ?: "") }
    var mostrarCalendario by remember { mutableStateOf(false) }
    var confirmarApagar by remember { mutableStateOf(false) }

    // As fotos juntadas aqui já estão gravadas em disco; se o diálogo for
    // cancelado apagam-se. As retiradas só se apagam ao guardar.
    val fotos = remember { mutableStateListOf<String>().apply { addAll(evento?.listaFotos.orEmpty()) } }
    val novas = remember { mutableStateListOf<String>() }
    val retiradas = remember { mutableStateListOf<String>() }
    var verFoto by remember { mutableStateOf<Int?>(null) }
    val cancelar = {
        vm.descartarFotos(novas.toList())
        onFechar()
    }

    AlertDialog(
        onDismissRequest = cancelar,
        title = { Text(if (evento == null) "Novo evento" else "Editar evento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = dia.texto(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data") },
                    trailingIcon = {
                        IconButton(onClick = { mostrarCalendario = true }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Escolher data")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("O que aconteceu") },
                    placeholder = { Text("ex: Início da movimentação de terras") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Detalhes (opcional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Fotos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                EditorFotos(
                    vm = vm,
                    fotos = fotos,
                    aoJuntar = { fotos += it; novas += it },
                    aoRetirar = { fotos -= it; retiradas += it },
                    aoVer = { verFoto = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = titulo.isNotBlank(),
                onClick = {
                    onGuardar(
                        EventoObra(
                            id = evento?.id ?: 0L,
                            dataEpochDay = dia.toEpochDay(),
                            titulo = titulo.trim(),
                            descricao = descricao.trim(),
                            // Num evento existente mantemos a posição que tem.
                            ordem = evento?.ordem ?: 0,
                            fotos = juntarFotos(fotos),
                        ),
                        retiradas.toList(),
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
                TextButton(onClick = cancelar) { Text("Cancelar") }
            }
        },
    )

    verFoto?.let { i -> VisualizadorFotos(fotos.toList(), i, onFechar = { verFoto = null }) }

    if (mostrarCalendario) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = dia.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { mostrarCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { ms ->
                        dia = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    mostrarCalendario = false
                }) { Text("Escolher") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCalendario = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = estado) }
    }

    if (confirmarApagar && onApagar != null) {
        AlertDialog(
            onDismissRequest = { confirmarApagar = false },
            title = { Text("Eliminar evento?") },
            text = {
                Text(
                    if (fotos.isEmpty()) "Esta ação não pode ser desfeita."
                    else "As fotos do evento também são apagadas. Esta ação não pode ser desfeita.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarApagar = false; onApagar() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar") }
            },
        )
    }
}
