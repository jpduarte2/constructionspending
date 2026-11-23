package pt.jpduarte.obragastos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pt.jpduarte.obragastos.data.Regra
import pt.jpduarte.obragastos.data.UNIDADE_ANO
import pt.jpduarte.obragastos.data.UNIDADE_DIA
import pt.jpduarte.obragastos.data.UNIDADE_MES
import pt.jpduarte.obragastos.data.UNIDADE_SEMANA
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val PT: Locale = Locale.forLanguageTag("pt-PT")
private val dataLonga = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", PT)

/** As escolhas rápidas; o resto vai pelo "Personalizado…". */
private data class Opcao(val rotulo: String, val unidade: String, val intervalo: Int)

private val OPCOES = listOf(
    Opcao("Todos os meses", UNIDADE_MES, 1),
    Opcao("De 2 em 2 meses", UNIDADE_MES, 2),
    Opcao("De 3 em 3 meses", UNIDADE_MES, 3),
    Opcao("De 6 em 6 meses", UNIDADE_MES, 6),
    Opcao("Todos os anos", UNIDADE_ANO, 1),
)

private val UNIDADES = listOf(UNIDADE_DIA to "Dia", UNIDADE_SEMANA to "Semana", UNIDADE_MES to "Mês", UNIDADE_ANO to "Ano")

private fun nomeUnidade(u: String, n: Int): String = when (u) {
    UNIDADE_DIA -> if (n == 1) "dia" else "dias"
    UNIDADE_SEMANA -> if (n == 1) "semana" else "semanas"
    UNIDADE_ANO -> if (n == 1) "ano" else "anos"
    else -> if (n == 1) "mês" else "meses"
}

/**
 * A regra em português: "todos os meses ao dia 5", "de 2 em 2 semanas, à
 * quarta-feira", "todos os anos a 10 de outubro · 5 vezes".
 */
fun Regra.texto(inicio: LocalDate): String {
    val n = intervalo
    val base = when (unidade) {
        UNIDADE_DIA -> if (n == 1) "todos os dias" else "de $n em $n dias"
        UNIDADE_SEMANA -> {
            val dds = inicio.dayOfWeek.getDisplayName(TextStyle.FULL, PT)
            // "ao sábado", "ao domingo", "à quarta-feira"
            val artigo = if (inicio.dayOfWeek.value >= 6) "ao" else "à"
            (if (n == 1) "todas as semanas" else "de $n em $n semanas") + ", $artigo $dds"
        }
        UNIDADE_ANO -> {
            val mes = inicio.month.getDisplayName(TextStyle.FULL, PT)
            (if (n == 1) "todos os anos" else "de $n em $n anos") + " a $dia de $mes"
        }
        else -> (if (n == 1) "todos os meses" else "de $n em $n meses") + " ao dia $dia"
    }
    return base + when {
        fim != null -> " · até ${fim.texto()}"
        maxVezes != null -> " · ${plural(maxVezes, "vez", "vezes")}"
        else -> ""
    }
}

/**
 * Escolha de como uma despesa se repete: as opções mais comuns num toque, e
 * "Personalizado…" para o resto (a cada N dias / semanas / meses / anos, e
 * quando termina). [inicio] é a data de vencimento a partir da qual se repete —
 * dá o dia da semana e o mês nas repetições semanais e anuais. O dia do mês
 * ([Regra.dia]) é de quem chama: vem da data de vencimento.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorRepeticao(regra: Regra, inicio: LocalDate, aoMudar: (Regra) -> Unit) {
    var personalizar by remember { mutableStateOf(false) }
    val rapida = OPCOES.firstOrNull { it.unidade == regra.unidade && it.intervalo == regra.intervalo }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OPCOES.forEach { o ->
            FilterChip(
                selected = rapida == o,
                onClick = { aoMudar(regra.copy(unidade = o.unidade, intervalo = o.intervalo)) },
                label = { Text(o.rotulo) },
            )
        }
        FilterChip(
            selected = rapida == null,
            onClick = { personalizar = true },
            label = { Text("Personalizado…") },
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        regra.texto(inicio).replaceFirstChar { it.uppercase() } + ".",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    // O fim também se muda daqui, sem passar pelas outras opções.
    if (rapida != null) {
        TextButton(onClick = { personalizar = true }, contentPadding = PaddingValues(0.dp)) {
            Text(if (regra.fim == null && regra.maxVezes == null) "Definir quando termina" else "Mudar quando termina")
        }
    }

    if (personalizar) {
        PainelRepeticao(
            regra = regra,
            inicio = inicio,
            onConcluir = { aoMudar(it); personalizar = false },
            onFechar = { personalizar = false },
        )
    }
}

/** [−] N [+] dentro de uma caixa, como um campo. */
@Composable
private fun Contador(valor: Int, aoMudar: (Int) -> Unit) {
    Row(
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { aoMudar((valor - 1).coerceAtLeast(1)) }, enabled = valor > 1) {
            Icon(Icons.Filled.Remove, contentDescription = "Menos")
        }
        Text(
            valor.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        IconButton(onClick = { aoMudar(valor + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Mais")
        }
    }
}

private enum class Fim { NUNCA, DATA, VEZES }

/**
 * A repetição personalizada, num painel que sobe do fundo: a partir de quando,
 * de quanto em quanto tempo, e quando termina.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PainelRepeticao(
    regra: Regra,
    inicio: LocalDate,
    onConcluir: (Regra) -> Unit,
    onFechar: () -> Unit,
) {
    var unidade by remember { mutableStateOf(regra.unidade) }
    var intervalo by remember { mutableStateOf(regra.intervalo) }
    var fim by remember {
        mutableStateOf(
            when {
                regra.fim != null -> Fim.DATA
                regra.maxVezes != null -> Fim.VEZES
                else -> Fim.NUNCA
            },
        )
    }
    var data by remember { mutableStateOf(regra.fim ?: inicio.plusYears(1)) }
    var vezes by remember { mutableStateOf(regra.maxVezes ?: 12) }
    var calendario by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onFechar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    "Escolher repetição",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(onClick = onFechar, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "A partir de: ${inicio.format(dataLonga)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(20.dp))
            // Tudo encostado à esquerda: assim "semanas" cabe na mesma linha.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Repetir a cada", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(12.dp))
                Contador(intervalo) { intervalo = it }
                Spacer(Modifier.width(12.dp))
                Text(
                    nomeUnidade(unidade, intervalo),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                UNIDADES.forEachIndexed { i, (u, rotulo) ->
                    SegmentedButton(
                        selected = unidade == u,
                        onClick = { unidade = u },
                        shape = SegmentedButtonDefaults.itemShape(i, UNIDADES.size),
                        icon = {},
                    ) { Text(rotulo) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // A regra escrita por extenso, para confirmar o que se escolheu.
            val previa = Regra(unidade, intervalo, regra.dia).texto(inicio)
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(10.dp))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(previa.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            Text("Termina", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OpcaoFim("Nunca", fim == Fim.NUNCA) { fim = Fim.NUNCA }
                OpcaoFim("Em ${data.format(dataLonga)}", fim == Fim.DATA) { fim = Fim.DATA; calendario = true }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .clip10()
                    .clickable { fim = Fim.VEZES }
                    .then(
                        if (fim == Fim.VEZES) {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Depois de", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(10.dp))
                Contador(vezes) { vezes = it; fim = Fim.VEZES }
                Spacer(Modifier.width(10.dp))
                Text(if (vezes == 1) "vez" else "vezes", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    onConcluir(
                        regra.copy(
                            unidade = unidade,
                            intervalo = intervalo,
                            fim = if (fim == Fim.DATA) data else null,
                            maxVezes = if (fim == Fim.VEZES) vezes else null,
                        ),
                    )
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("CONCLUÍDO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (calendario) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { calendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { data = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    calendario = false
                }) { Text("Escolher") }
            },
            dismissButton = { TextButton(onClick = { calendario = false }) { Text("Cancelar") } },
        ) { DatePicker(state = estado) }
    }
}

private fun Modifier.clip10(): Modifier = this.then(Modifier.clip(RoundedCornerShape(10.dp)))

/** Uma opção do "Termina": contorno em cor de destaque quando escolhida. */
@Composable
private fun OpcaoFim(rotulo: String, escolhida: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            if (escolhida) 1.5.dp else 1.dp,
            if (escolhida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(rotulo, color = MaterialTheme.colorScheme.onSurface)
    }
}
