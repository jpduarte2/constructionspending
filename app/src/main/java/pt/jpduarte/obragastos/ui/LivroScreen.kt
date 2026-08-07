package pt.jpduarte.obragastos.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import pt.jpduarte.obragastos.data.data
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
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(interno)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                Text(
                    "O livro está vazio",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Regista aqui o que vai acontecendo na obra — início das " +
                        "movimentações de terras, montagem do estaleiro, vistorias, " +
                        "atrasos. Fica com a data e a ordem que lhe deres.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                itemsIndexed(eventos, key = { _, e -> e.id }) { i, e ->
                    LinhaEvento(
                        evento = e,
                        primeiro = i == 0,
                        ultimo = i == eventos.lastIndex,
                        onClick = { aEditar = e },
                        onSubir = { vm.mover(e, -1) },
                        onDescer = { vm.mover(e, +1) },
                    )
                }
            }
        }
    }

    if (novo) {
        DialogoEvento(
            evento = null,
            onGuardar = { vm.guardar(it); novo = false },
            onFechar = { novo = false },
            onApagar = null,
        )
    }

    aEditar?.let { e ->
        DialogoEvento(
            evento = e,
            onGuardar = { vm.guardar(it); aEditar = null },
            onFechar = { aEditar = null },
            onApagar = { vm.apagar(e); aEditar = null },
        )
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
                    val yPonto = 22.dp.toPx()
                    val raio = 5.dp.toPx()
                    val traco = 2.dp.toPx()
                    if (!primeiro) {
                        drawLine(corLinha, Offset(x, 0f), Offset(x, yPonto - raio), traco)
                    }
                    if (!ultimo) {
                        drawLine(corLinha, Offset(x, yPonto + raio), Offset(x, size.height), traco)
                    }
                    drawCircle(corPonto, radius = raio, center = Offset(x, yPonto))
                },
        )

        Column(
            Modifier
                .weight(1f)
                .padding(start = 4.dp, top = 12.dp, bottom = 20.dp),
        ) {
            Text(
                evento.data.texto(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                evento.titulo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
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
        }

        Column(Modifier.padding(top = 4.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoEvento(
    evento: EventoObra?,
    onGuardar: (EventoObra) -> Unit,
    onFechar: () -> Unit,
    onApagar: (() -> Unit)?,
) {
    var dia by remember { mutableStateOf(evento?.data ?: LocalDate.now()) }
    var titulo by remember { mutableStateOf(evento?.titulo ?: "") }
    var descricao by remember { mutableStateOf(evento?.descricao ?: "") }
    var mostrarCalendario by remember { mutableStateOf(false) }
    var confirmarApagar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(if (evento == null) "Novo evento" else "Editar evento") },
        text = {
            Column {
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
            text = { Text("Esta ação não pode ser desfeita.") },
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
