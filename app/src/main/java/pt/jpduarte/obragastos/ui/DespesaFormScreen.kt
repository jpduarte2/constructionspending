package pt.jpduarte.obragastos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.jpduarte.obragastos.data.Despesa
import pt.jpduarte.obragastos.data.RUBRICAS_DESPESA
import pt.jpduarte.obragastos.data.RUBRICA_PRESTACAO
import pt.jpduarte.obragastos.data.Regra
import pt.jpduarte.obragastos.data.regra
import pt.jpduarte.obragastos.data.centimos
import pt.jpduarte.obragastos.data.emEuros
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Rubricas em que, por omissão, uma despesa fixa é de valor variável. */
private val RUBRICAS_VARIAVEIS = setOf(RUBRICA_PRESTACAO, "Luz", "Água", "Gás")

@Composable
private fun AvisoFormulario(titulo: String, texto: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Registar ou editar uma despesa. Com [copiaDe] o formulário abre preenchido
 * com essa despesa um mês à frente — é o caso da prestação ou da conta da luz,
 * que se repetem todos os meses quase iguais.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DespesaFormScreen(
    vm: ObraViewModel,
    despesaId: Long,
    copiaDe: Long,
    onSair: () -> Unit,
    onRepetir: (Long) -> Unit,
) {
    val todas by vm.despesas.collectAsState()
    val fixas by vm.despesasFixas.collectAsState()
    val original = remember(despesaId) { vm.despesaDe(despesaId) }
    // A fixa que lançou esta despesa, se foi lançada por uma.
    val fixa = original?.fixaId?.let { id -> fixas.firstOrNull { it.id == id } }
    val modelo = remember(copiaDe) { vm.despesaDe(copiaDe) }
    val base = original ?: modelo
    val aEditar = original != null

    // Sugeridas primeiro, pela ordem fixa; depois as que o utilizador inventou.
    val rubricas = remember(todas) {
        RUBRICAS_DESPESA + todas.map { it.rubrica }.filter { it !in RUBRICAS_DESPESA }.distinct().sorted()
    }

    var rubrica by remember { mutableStateOf(base?.rubrica ?: RUBRICA_PRESTACAO) }
    var outraRubrica by remember { mutableStateOf(false) }
    var descricao by remember { mutableStateOf(base?.descricao ?: "") }
    var dia by remember {
        mutableStateOf(
            when {
                original != null -> LocalDate.ofEpochDay(original.dataEpochDay)
                modelo != null -> LocalDate.ofEpochDay(modelo.dataEpochDay).plusMonths(1)
                else -> LocalDate.now()
            },
        )
    }
    var valorTexto by remember { mutableStateOf(base?.valorCent?.let { formatarEntrada(it.emEuros()) } ?: "") }
    var temCapital by remember { mutableStateOf(base?.let { it.capitalCent > 0 } ?: true) }
    var capitalTexto by remember {
        mutableStateOf(base?.capitalCent?.takeIf { it > 0 }?.let { formatarEntrada(it.emEuros()) } ?: "")
    }
    var notas by remember { mutableStateOf(base?.notas ?: "") }
    var mostrarCalendario by remember { mutableStateOf(false) }

    // Repetição: só se pode ligar numa despesa que ainda não é recorrente.
    var repete by remember { mutableStateOf(false) }
    var regra by remember { mutableStateOf(Regra(dia = dia.dayOfMonth)) }
    var variavel by remember { mutableStateOf<Boolean?>(null) }

    val valor = valorTexto.paraNumero() ?: 0.0
    // A divisão capital/juros só existe nas prestações do crédito.
    val eCredito = rubrica == RUBRICA_PRESTACAO
    val capital = if (eCredito && temCapital) capitalTexto.paraNumero() ?: 0.0 else 0.0
    val capitalInvalido = capital > valor + 0.005
    // Por omissão, variável nas rubricas cujo valor muda de mês para mês.
    val ehVariavel = variavel ?: (rubrica in RUBRICAS_VARIAVEIS)
    val podeGuardar = valor > 0.0 && rubrica.isNotBlank() && !capitalInvalido

    fun montar(id: Long) = Despesa(
        id = id,
        rubrica = rubrica.trim(),
        descricao = descricao.trim(),
        dataEpochDay = dia.toEpochDay(),
        valorCent = centimos(valor),
        capitalCent = centimos(capital),
        notas = notas.trim(),
        fixaId = original?.fixaId,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (aEditar) "Editar despesa" else "Nova despesa") },
                navigationIcon = {
                    IconButton(onClick = onSair) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (aEditar) {
                        // Sem confirmação: o ecrã das despesas oferece "Desfazer".
                        IconButton(onClick = { vm.apagar(original!!); onSair() }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { interno ->
        Column(
            Modifier
                .padding(interno)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            if (original?.aConfirmar == true) {
                AvisoFormulario(
                    "Valor estimado",
                    "Esta despesa foi lançada sozinha com o valor da vez anterior. Corrige o valor " +
                        "(e o capital, se for a prestação) e carrega em Confirmar.",
                )
                Spacer(Modifier.height(16.dp))
            } else if (fixa != null) {
                AvisoFormulario(
                    "Despesa recorrente",
                    "Repete-se ${fixa.regra().texto(LocalDate.ofEpochDay(fixa.inicioEpochDay))}. Para mudar ou parar, usa " +
                        "\"Recorrentes\" no separador Despesas.",
                )
                Spacer(Modifier.height(16.dp))
            }
            Rotulo("Rubrica")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rubricas.forEach { r ->
                    FilterChip(
                        selected = !outraRubrica && rubrica == r,
                        onClick = { outraRubrica = false; rubrica = r },
                        label = { Text(r) },
                    )
                }
                FilterChip(
                    selected = outraRubrica,
                    onClick = { outraRubrica = true; rubrica = "" },
                    label = { Text("Nova…") },
                )
            }
            if (outraRubrica) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = rubrica,
                    onValueChange = { rubrica = it },
                    label = { Text("Nome da rubrica") },
                    placeholder = { Text("ex: Condomínio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = descricao,
                onValueChange = { descricao = it },
                label = { Text("Descrição (opcional)") },
                placeholder = { Text(if (eCredito) "ex: Prestação de março" else "ex: Fatura de março") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = valorTexto,
                onValueChange = { valorTexto = it.filtrarNumerico() },
                label = { Text("Valor pago") },
                suffix = { Text("€") },
                supportingText = { Text("O que saiu da conta, com tudo incluído.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = dia.texto(),
                onValueChange = {},
                readOnly = true,
                label = { Text(if (repete) "Data de vencimento" else "Data") },
                trailingIcon = {
                    IconButton(onClick = { mostrarCalendario = true }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Escolher data")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (eCredito) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Separar o capital amortizado",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "O capital não é custo: é devolver o empréstimo. Vem no extrato do crédito.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = temCapital, onCheckedChange = { temCapital = it })
                }
                if (temCapital) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = capitalTexto,
                        onValueChange = { capitalTexto = it.filtrarNumerico() },
                        label = { Text("Desta prestação, capital amortizado") },
                        suffix = { Text("€") },
                        isError = capitalInvalido,
                        supportingText = {
                            Text(
                                if (capitalInvalido) "Não pode ser maior que o valor pago."
                                else "Custo real (juros, seguros, comissões): ${(valor - capital).coerceAtLeast(0.0).eur()}",
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (original?.fixaId == null) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Repete",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "A app lança-a sozinha nas próximas vezes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = repete, onCheckedChange = { repete = it })
                }
                if (repete) {
                    Spacer(Modifier.height(8.dp))
                    // O dia do mês é sempre o da data: é a data de vencimento.
                    EditorRepeticao(regra = regra.copy(dia = dia.dayOfMonth), inicio = dia, aoMudar = { regra = it })
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "O valor muda de vez para vez",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (ehVariavel) "Entra com o valor da vez anterior, marcada \"a confirmar\"."
                                else "Entra sempre com este valor, já confirmada.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = ehVariavel, onCheckedChange = { variavel = it })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = notas,
                onValueChange = { notas = it },
                label = { Text("Notas (opcional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    vm.guardar(
                        montar(original?.id ?: 0L),
                        repeticao = if (repete) regra.copy(dia = dia.dayOfMonth) else null,
                        valorVariavel = ehVariavel,
                    )
                    onSair()
                },
                enabled = podeGuardar,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    when {
                        original?.aConfirmar == true -> "Confirmar"
                        aEditar -> "Guardar alterações"
                        else -> "Registar despesa"
                    },
                )
            }
            // Numa fixa a repetição já é automática; o botão manual só confunde.
            if (aEditar && original?.fixaId == null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        // Grava o que está no ecrã antes de abrir a cópia.
                        if (podeGuardar) vm.guardar(montar(original!!.id))
                        onRepetir(original!!.id)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text("  Repetir no mês seguinte")
                }
            }
        }
    }

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
}
