package pt.jpduarte.obragastos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.jpduarte.obragastos.data.ORIGEM_BANCARIO
import pt.jpduarte.obragastos.data.ORIGEM_NAO_APLICA
import pt.jpduarte.obragastos.data.ORIGEM_PESSOAL
import pt.jpduarte.obragastos.data.Pagamento
import pt.jpduarte.obragastos.data.TAXA_IVA_NORMAL
import pt.jpduarte.obragastos.data.baseTributavel
import pt.jpduarte.obragastos.data.centimos
import pt.jpduarte.obragastos.data.ivaParcial
import pt.jpduarte.obragastos.data.ivaPoupado
import pt.jpduarte.obragastos.data.totalPago
import pt.jpduarte.obragastos.data.valorBase
import pt.jpduarte.obragastos.data.valorIva
import pt.jpduarte.obragastos.ui.theme.LocalViz
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Taxas de IVA em vigor em Portugal continental, mais "sem IVA". */
private val TAXAS_COMUNS = listOf(23.0, 13.0, 6.0, 0.0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PagamentoFormScreen(
    vm: ObraViewModel,
    pagamentoId: Long,
    onSair: () -> Unit,
) {
    val categorias by vm.categorias.collectAsState()
    val todos by vm.pagamentos.collectAsState()
    val original = remember(pagamentoId) { vm.pagamentoDe(pagamentoId) }
    val aEditar = original != null

    var categoriaId by remember { mutableStateOf(original?.categoriaId ?: 0L) }
    var descricao by remember { mutableStateOf(original?.descricao ?: "") }
    var dia by remember { mutableStateOf(original?.let { LocalDate.ofEpochDay(it.dataEpochDay) } ?: LocalDate.now()) }
    var valorTexto by remember {
        mutableStateOf(original?.valorBase?.let { formatarEntrada(it) } ?: "")
    }
    var taxa by remember { mutableStateOf(original?.taxaIva ?: 23.0) }
    var taxaOutraTexto by remember {
        mutableStateOf(
            original?.taxaIva?.takeIf { it !in TAXAS_COMUNS }?.let { formatarEntrada(it) } ?: "",
        )
    }
    var taxaPersonalizada by remember { mutableStateOf(original?.taxaIva?.let { it !in TAXAS_COMUNS } ?: false) }
    // Muitas faturas só mostram o total. Nesse caso escreve-se o total e o valor
    // sem IVA é deduzido — a base é sempre o que fica guardado.
    var valorComIva by remember { mutableStateOf(false) }
    var ivaPago by remember { mutableStateOf(original?.ivaPago ?: true) }
    var contaParaObra by remember { mutableStateOf(original?.contaParaObra ?: true) }
    var notas by remember { mutableStateOf(original?.notas ?: "") }
    var fornecedor by remember { mutableStateOf(original?.fornecedor ?: "") }
    var origemFundos by remember { mutableStateOf(original?.origemFundos ?: ORIGEM_PESSOAL) }
    // Os fornecedores já usados, do mais frequente para o menos, para não ter
    // de escrever o mesmo nome (e com grafias diferentes) fatura após fatura.
    val fornecedoresUsados = remember(todos) {
        todos.map { it.fornecedor.trim() }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }

    // O IVA pode ter incidido só sobre parte do pagamento.
    var ivaSobreParte by remember { mutableStateOf(original?.ivaParcial ?: false) }
    var parteTexto by remember {
        mutableStateOf(
            original?.takeIf { it.ivaParcial }?.baseTributavel?.let { formatarEntrada(it) } ?: "",
        )
    }

    var mostrarCalendario by remember { mutableStateOf(false) }

    // Se a categoria ainda não foi escolhida, assume a primeira da lista.
    if (categoriaId == 0L && categorias.isNotEmpty()) categoriaId = categorias.first().id

    val taxaEfetiva = if (taxaPersonalizada) taxaOutraTexto.paraNumero() ?: 0.0 else taxa
    val valorEscrito = valorTexto.paraNumero() ?: 0.0
    val parte = parteTexto.paraNumero() ?: 0.0

    // "Sem IVA" não é isenção: assume-se que o normal seria pagar a taxa normal,
    // logo o pagamento fica registado a 23% com o IVA por pagar — ou seja, poupado.
    val semIva = taxaEfetiva <= 0.0
    val taxaGuardada = if (semIva) TAXA_IVA_NORMAL else taxaEfetiva

    // Só faz sentido retirar o IVA a um total quando há taxa a retirar.
    val totalTemIva = valorComIva && !semIva
    // O que se guarda é sempre o valor limpo: aqui desfaz-se o IVA do total escrito.
    val valorBase =
        if (totalTemIva) arredondarCentimos(valorEscrito / (1.0 + taxaEfetiva / 100.0))
        else valorEscrito
    // Quem escreve o total já pagou o IVA que lá vem dentro.
    val ivaPagoGuardado = when {
        semIva -> false
        totalTemIva -> true
        else -> ivaPago
    }

    // A base tributável incide sobre tudo ou sobre a parte indicada.
    val baseTributavel = when {
        semIva || totalTemIva -> valorBase
        ivaSobreParte -> parte
        else -> valorBase
    }
    val parteInvalida = taxaEfetiva > 0.0 && !totalTemIva && ivaSobreParte &&
        (parte <= 0.0 || parte > valorBase + 0.005)
    val podeGuardar = valorBase > 0.0 && categoriaId != 0L && !parteInvalida

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (aEditar) "Editar pagamento" else "Novo pagamento") },
                navigationIcon = {
                    IconButton(onClick = onSair) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (aEditar) {
                        // Sem confirmação: o ecrã dos pagamentos oferece "Desfazer".
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
            Rotulo("Especialidade")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categorias.forEach { c ->
                    FilterChip(
                        selected = categoriaId == c.id,
                        onClick = { categoriaId = c.id },
                        label = { Text(c.nome) },
                    )
                }
            }
            if (categorias.isEmpty()) {
                Text(
                    "Cria primeiro uma categoria no separador Categorias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = descricao,
                onValueChange = { descricao = it },
                label = { Text("Descrição") },
                placeholder = { Text("ex: 2.ª tranche da alvenaria") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = fornecedor,
                onValueChange = { fornecedor = it },
                label = { Text("Fornecedor (opcional)") },
                placeholder = { Text("ex: Construções Silva, Lda.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Sugestões que ainda batem com o que se está a escrever.
            val sugestoes = fornecedoresUsados
                .filter { it.contains(fornecedor.trim(), ignoreCase = true) && !it.equals(fornecedor.trim(), ignoreCase = true) }
                .take(6)
            if (sugestoes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sugestoes.forEach { f ->
                        FilterChip(selected = false, onClick = { fornecedor = f }, label = { Text(f) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = valorTexto,
                onValueChange = { valorTexto = it.filtrarNumerico() },
                label = { Text(if (totalTemIva) "Total pago" else "Valor do pagamento") },
                suffix = { Text("€") },
                supportingText = {
                    Text(
                        when {
                            totalTemIva ->
                                "Total da fatura — o IVA de ${taxaEfetiva.taxaTexto()}% já vem incluído."
                            semIva -> "Valor da fatura, sem IVA."
                            else -> "Sem IVA — o IVA acresce em baixo."
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!semIva) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !valorComIva,
                        onClick = { valorComIva = false },
                        label = { Text("Este valor não tem IVA") },
                    )
                    FilterChip(
                        selected = valorComIva,
                        onClick = { valorComIva = true },
                        label = { Text("Já tem IVA incluído") },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = dia.texto(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Data do pagamento") },
                trailingIcon = {
                    IconButton(onClick = { mostrarCalendario = true }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Escolher data")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            Rotulo("Origem dos fundos")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = origemFundos == ORIGEM_PESSOAL,
                    onClick = { origemFundos = ORIGEM_PESSOAL },
                    label = { Text("Pessoal") },
                )
                FilterChip(
                    selected = origemFundos == ORIGEM_BANCARIO,
                    onClick = { origemFundos = ORIGEM_BANCARIO },
                    label = { Text("Bancário") },
                )
                FilterChip(
                    selected = origemFundos == ORIGEM_NAO_APLICA,
                    onClick = { origemFundos = ORIGEM_NAO_APLICA },
                    label = { Text("N/A") },
                )
            }
            if (origemFundos == ORIGEM_NAO_APLICA) {
                Text(
                    "Não se aplica: fica fora do gráfico da origem dos fundos, mas conta nos totais.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            Rotulo("Taxa de IVA")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TAXAS_COMUNS.forEach { t ->
                    FilterChip(
                        selected = !taxaPersonalizada && taxa == t,
                        onClick = { taxaPersonalizada = false; taxa = t },
                        label = { Text(if (t == 0.0) "Sem IVA" else "${t.taxaTexto()}%") },
                    )
                }
                FilterChip(
                    selected = taxaPersonalizada,
                    onClick = { taxaPersonalizada = true },
                    label = { Text("Outra") },
                )
            }

            if (semIva) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sem IVA na fatura: conta como ${TAXA_IVA_NORMAL.taxaTexto()}% poupados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (taxaPersonalizada) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = taxaOutraTexto,
                    onValueChange = { taxaOutraTexto = it.filtrarNumerico() },
                    label = { Text("Taxa") },
                    suffix = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Com um total já com IVA não há nada a decidir: o IVA incidiu sobre
            // tudo e foi pago, senão o total não seria aquele.
            if (taxaEfetiva > 0.0 && !totalTemIva) {
                Spacer(Modifier.height(20.dp))
                Rotulo("O IVA incide sobre")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !ivaSobreParte,
                        onClick = { ivaSobreParte = false },
                        label = { Text("Todo o valor") },
                    )
                    FilterChip(
                        selected = ivaSobreParte,
                        onClick = { ivaSobreParte = true },
                        label = { Text("Parte do valor") },
                    )
                }
                if (ivaSobreParte) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = parteTexto,
                        onValueChange = { parteTexto = it.filtrarNumerico() },
                        label = { Text("Parte com IVA") },
                        suffix = { Text("€") },
                        isError = parteInvalida,
                        supportingText = {
                            Text(
                                if (parteInvalida)
                                    "Tem de ser maior que zero e não pode exceder ${valorBase.eur()}."
                                else
                                    "Quanto do pagamento foi tributado a ${taxaEfetiva.taxaTexto()}%.",
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "O IVA foi pago?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (ivaPago) "Sim — o IVA saiu da conta"
                            else "Não — o IVA conta como poupança",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = ivaPago, onCheckedChange = { ivaPago = it })
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Conta para a obra?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (contaParaObra) "Sim — entra no total e no orçamento da obra"
                        else "Não — custo da casa fora da obra (terreno, licenças, escrituras)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = contaParaObra, onCheckedChange = { contaParaObra = it })
            }

            Spacer(Modifier.height(20.dp))

            CartaoCalculo(
                Pagamento(
                    categoriaId = categoriaId,
                    descricao = "",
                    dataEpochDay = 0,
                    valorBaseCent = centimos(valorBase),
                    baseTributavelCent = centimos(baseTributavel),
                    taxaIva = taxaGuardada,
                    ivaPago = ivaPagoGuardado,
                ),
            )

            Spacer(Modifier.height(20.dp))

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
                        Pagamento(
                            id = original?.id ?: 0L,
                            categoriaId = categoriaId,
                            descricao = descricao.trim(),
                            dataEpochDay = dia.toEpochDay(),
                            valorBaseCent = centimos(valorBase),
                            baseTributavelCent = centimos(baseTributavel),
                            taxaIva = taxaGuardada,
                            ivaPago = ivaPagoGuardado,
                            contaParaObra = contaParaObra,
                            notas = notas.trim(),
                            fornecedor = fornecedor.trim(),
                            origemFundos = origemFundos,
                        ),
                    )
                    onSair()
                },
                enabled = podeGuardar,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (aEditar) "Guardar alterações" else "Registar pagamento")
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

/**
 * Mostra a decomposição em tempo real enquanto se escreve: base, IVA, e o que
 * efetivamente sai da conta — mais a poupança, quando o IVA não é pago.
 *
 * Recebe o pagamento tal como vai ser gravado e usa as mesmas contas do resto
 * da app (IVA arredondado ao cêntimo), para o que aqui se lê bater com os totais.
 */
@Composable
private fun CartaoCalculo(p: Pagamento) {
    val viz = LocalViz.current
    val valorBase = p.valorBase
    val baseTributavel = p.baseTributavel
    val taxa = p.taxaIva
    val iva = p.valorIva
    val total = p.totalPago
    val parcial = p.ivaParcial
    // Poupa-se o IVA não pago e também o que a parte não tributada teria custado.
    val poupado = p.ivaPoupado

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            LinhaCalculo("Valor sem IVA", valorBase.eur())
            if (taxa > 0.0) {
                // Com IVA parcial mostramos as duas parcelas, para se ver de
                // onde vem o montante do imposto.
                if (parcial) {
                    LinhaCalculo("· com IVA", baseTributavel.eur())
                    LinhaCalculo("· sem IVA", (valorBase - baseTributavel).eur())
                }
                LinhaCalculo(
                    if (parcial) "IVA ${taxa.taxaTexto()}% sobre ${baseTributavel.eurCurto()}"
                    else "IVA ${taxa.taxaTexto()}%",
                    iva.eur(),
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "Sai da conta",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    total.eur(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (poupado > 0.0) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "IVA poupado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        poupado.eur(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = viz.tintaPositiva,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinhaCalculo(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            rotulo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun Rotulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/* --------------------------- entrada numérica --------------------------- */

/** Aceita dígitos e um único separador decimal (vírgula ou ponto). */
internal fun String.filtrarNumerico(): String {
    val limpo = filter { it.isDigit() || it == ',' || it == '.' }
    val primeiroSep = limpo.indexOfFirst { it == ',' || it == '.' }
    if (primeiroSep < 0) return limpo
    val cabeca = limpo.substring(0, primeiroSep + 1)
    val cauda = limpo.substring(primeiroSep + 1).filter { it.isDigit() }
    return cabeca + cauda
}

internal fun String.paraNumero(): Double? = replace(',', '.').toDoubleOrNull()

/**
 * Arredonda aos cêntimos. Ao deduzir a base de um total com IVA o resultado é
 * quase sempre uma dízima infinita (123 / 1,23 = 100,000000…), e guardá-la
 * inteira faria os totais fugirem uns cêntimos ao que está na fatura.
 */
internal fun arredondarCentimos(v: Double): Double = Math.round(v * 100.0) / 100.0

/** 1500.0 -> "1500"; 1500.5 -> "1500,5" (para reeditar sem ruído). */
internal fun formatarEntrada(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')
