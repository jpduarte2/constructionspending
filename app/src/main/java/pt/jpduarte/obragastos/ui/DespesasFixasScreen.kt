package pt.jpduarte.obragastos.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.jpduarte.obragastos.data.DespesaFixa
import pt.jpduarte.obragastos.data.RUBRICA_PRESTACAO
import pt.jpduarte.obragastos.data.centimos
import pt.jpduarte.obragastos.data.emEuros
import pt.jpduarte.obragastos.data.comRegra
import pt.jpduarte.obragastos.data.dataNominal
import pt.jpduarte.obragastos.data.proximoDebito
import pt.jpduarte.obragastos.data.regra
import pt.jpduarte.obragastos.ui.theme.LocalViz
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * As despesas fixas: o que se repete todos os meses, quando é o próximo débito,
 * e como as mudar, pausar ou parar. Criam-se no formulário de uma despesa
 * ("Repete todos os meses"), que é onde já se escreve o valor e a rubrica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DespesasFixasScreen(vm: ObraViewModel, onSair: () -> Unit, onNova: () -> Unit) {
    val fixas by vm.despesasFixas.collectAsState()
    val despesas by vm.despesas.collectAsState()
    val todas = remember(despesas, fixas) { (despesas.map { it.rubrica } + fixas.map { it.rubrica }).toSet() }
    var aEditar by remember { mutableStateOf<DespesaFixa?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Despesas recorrentes") },
                navigationIcon = {
                    IconButton(onClick = onSair) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNova,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova recorrente") },
            )
        },
    ) { interno ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = interno.calculateTopPadding() + 8.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "A app lança estas despesas sozinha, em cada vencimento. As de valor variável " +
                        "entram com o valor da vez anterior e ficam \"a confirmar\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (fixas.isEmpty()) {
                item {
                    Text(
                        "Ainda não há despesas recorrentes. Toca em \"Nova recorrente\", preenche a despesa do mês " +
                            "e liga \"Repete todos os meses\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(fixas, key = { it.id }) { f ->
                CartaoFixa(f, slotRubrica(f.rubrica, todas), onClick = { aEditar = f })
            }
        }
    }

    aEditar?.let { f ->
        DialogoFixa(
            fixa = f,
            onGuardar = { vm.guardar(it); aEditar = null },
            onPausar = { vm.pausar(f, pausada = f.ativa); aEditar = null },
            onApagar = { vm.apagar(f); aEditar = null },
            onFechar = { aEditar = null },
        )
    }
}

@Composable
private fun CartaoFixa(f: DespesaFixa, slot: Int, onClick: () -> Unit) {
    val viz = LocalViz.current
    val proximo = proximoDebito(f)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(viz.series[slot % viz.series.size]))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).alpha(if (f.ativa) 1f else 0.6f)) {
                Text(
                    f.descricao.ifBlank { f.rubrica },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${f.rubrica} · ${f.regra().texto(LocalDate.ofEpochDay(f.inicioEpochDay))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        !f.ativa -> "Em pausa"
                        proximo == null -> "Terminada"
                        else -> "Próximo débito: ${proximo.texto()}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    f.valorCent.emEuros().eur(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (f.valorVariavel) "valor variável" else "valor fixo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoFixa(
    fixa: DespesaFixa,
    onGuardar: (DespesaFixa) -> Unit,
    onPausar: () -> Unit,
    onApagar: () -> Unit,
    onFechar: () -> Unit,
) {
    var descricao by remember { mutableStateOf(fixa.descricao) }
    var valorTexto by remember { mutableStateOf(formatarEntrada(fixa.valorCent.emEuros())) }
    var capitalTexto by remember {
        mutableStateOf(fixa.capitalCent.takeIf { it > 0 }?.let { formatarEntrada(it.emEuros()) } ?: "")
    }
    var variavel by remember { mutableStateOf(fixa.valorVariavel) }
    var regra by remember { mutableStateOf(fixa.regra()) }
    var confirmarApagar by remember { mutableStateOf(false) }
    // O próximo vencimento: é a partir dele que a repetição conta. Se for
    // mudado, a recorrente recomeça nessa data e o dia do mês passa a ser o dela.
    var vencimento by remember { mutableStateOf(proximoDebito(fixa) ?: fixa.dataNominal(fixa.proximo)) }
    var vencimentoMudado by remember { mutableStateOf(false) }
    var calendario by remember { mutableStateOf(false) }
    val regraAMostrar = if (vencimentoMudado) regra.copy(dia = vencimento.dayOfMonth) else regra

    val valor = valorTexto.paraNumero() ?: 0.0
    val capital = capitalTexto.paraNumero() ?: 0.0
    val valido = valor > 0.0 && capital <= valor + 0.005

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(fixa.rubrica) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Descrição (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = valorTexto,
                    onValueChange = { valorTexto = it.filtrarNumerico() },
                    label = { Text(if (variavel) "Último valor" else "Valor") },
                    suffix = { Text("€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (fixa.rubrica == RUBRICA_PRESTACAO) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = capitalTexto,
                        onValueChange = { capitalTexto = it.filtrarNumerico() },
                        label = { Text("Capital amortizado") },
                        suffix = { Text("€") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vencimento.texto(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Próximo vencimento") },
                    trailingIcon = {
                        IconButton(onClick = { calendario = true }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Escolher data")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Rotulo("Repete")
                EditorRepeticao(regra = regraAMostrar, inicio = vencimento, aoMudar = { regra = it })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "O valor muda de vez para vez",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = variavel, onCheckedChange = { variavel = it })
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPausar) { Text(if (fixa.ativa) "Pausar" else "Retomar") }
                    TextButton(onClick = { confirmarApagar = true }) {
                        Text("Deixar de repetir", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valido,
                onClick = {
                    val base = if (vencimentoMudado) {
                        // Recomeça na data escolhida: é a ocorrência 0 da regra nova.
                        fixa.copy(
                            unidade = regra.unidade,
                            intervalo = regra.intervalo,
                            dia = vencimento.dayOfMonth,
                            inicioEpochDay = vencimento.toEpochDay(),
                            proximo = 0,
                        )
                    } else {
                        fixa.comRegra(regra.unidade, regra.intervalo, regra.dia)
                    }
                    onGuardar(
                        base.copy(
                            descricao = descricao.trim(),
                            valorCent = centimos(valor),
                            capitalCent = if (fixa.rubrica == RUBRICA_PRESTACAO) centimos(capital) else 0,
                            valorVariavel = variavel,
                            fimEpochDay = regra.fim?.toEpochDay(),
                            maxOcorrencias = regra.maxVezes,
                        ),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
    )

    if (calendario) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = vencimento.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { calendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let {
                        vencimento = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        vencimentoMudado = true
                    }
                    calendario = false
                }) { Text("Escolher") }
            },
            dismissButton = { TextButton(onClick = { calendario = false }) { Text("Cancelar") } },
        ) { DatePicker(state = estado) }
    }

    if (confirmarApagar) {
        AlertDialog(
            onDismissRequest = { confirmarApagar = false },
            title = { Text("Deixar de repetir?") },
            text = { Text("Não se lançam mais despesas desta recorrente. As que já foram lançadas ficam na lista.") },
            confirmButton = {
                TextButton(onClick = { confirmarApagar = false; onApagar() }) {
                    Text("Deixar de repetir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar") } },
        )
    }
}
