package pt.jpduarte.obragastos.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.jpduarte.obragastos.ui.eurCurto
import pt.jpduarte.obragastos.ui.eurEixo
import pt.jpduarte.obragastos.ui.theme.LocalViz
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/* ------------------------------------------------------------------ */
/*  Linha temporal — acumulados e projeções                           */
/* ------------------------------------------------------------------ */

/**
 * Uma série de uma linha temporal. [valores] alinha com os rótulos do eixo X;
 * `null` = a série não tem ponto nesse mês (é assim que a projeção começa onde
 * o real acaba). [tracejada] para o que é estimativa e não aconteceu.
 */
data class SerieLinha(
    val rotulo: String,
    val valores: List<Double?>,
    val cor: Color,
    val tracejada: Boolean = false,
)

/** Uma linha horizontal de referência — um teto, como o orçamento. */
data class Referencia(val rotulo: String, val valor: Double)

/**
 * Evolução no tempo com um só eixo Y. Linhas de 2px, sem marcadores a não ser
 * no mês selecionado; tocar escolhe o mês e a leitura aparece por cima, com o
 * valor de cada série — o equivalente táctil da mira.
 */
@Composable
fun LinhaTempo(
    rotulosX: List<String>,
    series: List<SerieLinha>,
    modifier: Modifier = Modifier,
    referencia: Referencia? = null,
    rotuloLeitura: (Int) -> String = { rotulosX[it] },
) {
    if (rotulosX.isEmpty() || series.isEmpty()) return
    val viz = LocalViz.current
    val medidor = rememberTextMeasurer()
    val superficie = MaterialTheme.colorScheme.surface
    val estEixo = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = viz.esbatido)

    // Começa no último mês com valores reais, que é o que interessa ver primeiro.
    val ultimoReal = series.first().valores.indexOfLast { it != null }.coerceAtLeast(0)
    var selecionado by remember(rotulosX, series) { mutableStateOf(ultimoReal) }

    val maxDados = series.flatMap { it.valores }.filterNotNull().maxOrNull() ?: 0.0
    val maxEscala = escalaLimpa(maxOf(maxDados, referencia?.valor ?: 0.0))

    Column(modifier) {
        // Leitura do mês escolhido: cada série com o seu valor, a cor no ponto.
        Text(
            rotuloLeitura(selecionado),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            series.forEach { s ->
                val v = s.valores.getOrNull(selecionado) ?: return@forEach
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 12.dp, height = 3.dp).background(s.cor))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        v.eurCurto(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        val larguraEixoY = 56.dp
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(rotulosX) {
                    detectTapGestures { pos ->
                        val esq = larguraEixoY.toPx()
                        val largura = size.width - esq
                        if (largura > 0 && rotulosX.size > 1) {
                            val passo = largura / (rotulosX.size - 1)
                            selecionado = Math.round((pos.x - esq) / passo).coerceIn(0, rotulosX.lastIndex)
                        } else {
                            selecionado = 0
                        }
                    }
                },
        ) {
            val esq = larguraEixoY.toPx()
            val alturaEixoX = 18.dp.toPx()
            val topo = 6.dp.toPx()
            val base = size.height - alturaEixoX
            val alturaPlot = base - topo
            val largura = size.width - esq - 4.dp.toPx()
            val passo = if (rotulosX.size > 1) largura / (rotulosX.size - 1) else 0f
            fun x(i: Int) = esq + if (rotulosX.size > 1) passo * i else largura / 2f
            fun y(v: Double) = base - (alturaPlot * (v / maxEscala)).toFloat()

            // grelha recessiva: 0, meio, máximo
            listOf(0.0, 0.5, 1.0).forEach { frac ->
                val yy = y(maxEscala * frac)
                drawLine(
                    color = if (frac == 0.0) viz.eixo else viz.grade,
                    start = Offset(esq, yy),
                    end = Offset(size.width, yy),
                    strokeWidth = 1.dp.toPx(),
                )
                val txt = medidor.measure((maxEscala * frac).eurEixo(), estEixo)
                drawText(txt, topLeft = Offset(esq - 8.dp.toPx() - txt.size.width, yy - txt.size.height / 2f))
            }

            // Referência (orçamento): tracejado discreto, com o nome por cima.
            referencia?.let { r ->
                val yy = y(r.valor)
                drawLine(
                    color = viz.esbatido,
                    start = Offset(esq, yy),
                    end = Offset(size.width, yy),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
                val txt = medidor.measure(r.rotulo, estEixo)
                drawText(txt, topLeft = Offset(size.width - txt.size.width, yy - txt.size.height - 2.dp.toPx()))
            }

            // guia vertical do mês selecionado
            drawLine(
                color = viz.grade,
                start = Offset(x(selecionado), topo),
                end = Offset(x(selecionado), base),
                strokeWidth = 1.dp.toPx(),
            )

            series.forEach { s ->
                val caminho = Path()
                var aberto = false
                s.valores.forEachIndexed { i, v ->
                    if (v == null) {
                        aberto = false
                    } else if (!aberto) {
                        caminho.moveTo(x(i), y(v)); aberto = true
                    } else {
                        caminho.lineTo(x(i), y(v))
                    }
                }
                drawPath(
                    caminho,
                    color = s.cor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (s.tracejada) {
                            PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                        } else null,
                    ),
                )
                val pontos = s.valores.count { it != null }
                s.valores.getOrNull(selecionado)?.let { v ->
                    // marcador com anel da cor da superfície, para se destacar da linha
                    drawCircle(superficie, radius = 6.dp.toPx(), center = Offset(x(selecionado), y(v)))
                    drawCircle(s.cor, radius = 4.dp.toPx(), center = Offset(x(selecionado), y(v)))
                }
                // Uma série de um só ponto não desenha linha: marca-se o ponto.
                if (pontos == 1) {
                    val i = s.valores.indexOfFirst { it != null }
                    drawCircle(s.cor, radius = 4.dp.toPx(), center = Offset(x(i), y(s.valores[i]!!)))
                }
            }

            // Rótulos do eixo X: só os que cabem, espaçados por igual.
            val larguraRotulo = rotulosX.maxOf { medidor.measure(it, estEixo).size.width } + 6.dp.toPx()
            val cada = if (passo <= 0f) 1 else maxOf(1, Math.ceil((larguraRotulo / passo).toDouble()).toInt())
            rotulosX.forEachIndexed { i, r ->
                // O mês escolhido já vem escrito na leitura por cima; forçá-lo
                // aqui fazia-o encavalitar-se no vizinho.
                if (i % cada != 0) return@forEachIndexed
                val txt = medidor.measure(r, estEixo)
                val cx = (x(i) - txt.size.width / 2f).coerceIn(esq - 4.dp.toPx(), size.width - txt.size.width)
                drawText(txt, topLeft = Offset(cx, base + 4.dp.toPx()))
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Colunas empilhadas — composição de cada mês                       */
/* ------------------------------------------------------------------ */

/** Uma coluna: o rótulo do eixo e as parcelas, de baixo para cima. */
data class Pilha(val rotulo: String, val partes: List<Fatia>) {
    val total: Double get() = partes.sumOf { it.valor }
}

/**
 * Composição mês a mês. Cada parcela tem a cor da sua categoria (a mesma de
 * toda a app) e uma folga de 2px na cor da superfície. Tocar numa coluna mostra
 * por baixo a decomposição desse mês em texto, por ordem de valor.
 */
@Composable
fun ColunasEmpilhadas(
    pilhas: List<Pilha>,
    modifier: Modifier = Modifier,
    rotuloLeitura: (Int) -> String = { pilhas[it].rotulo },
) {
    if (pilhas.isEmpty()) return
    val viz = LocalViz.current
    val medidor = rememberTextMeasurer()
    val superficie = MaterialTheme.colorScheme.surface
    val estEixo = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = viz.esbatido)

    var selecionado by remember(pilhas) { mutableStateOf(pilhas.lastIndex) }
    val maxEscala = remember(pilhas) { escalaLimpa(pilhas.maxOf { it.total }) }

    Column(modifier) {
        val larguraEixoY = 56.dp
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(pilhas) {
                    detectTapGestures { pos ->
                        val esq = larguraEixoY.toPx()
                        val largura = size.width - esq
                        if (largura > 0 && pos.x >= esq) {
                            selecionado = ((pos.x - esq) / (largura / pilhas.size)).toInt().coerceIn(0, pilhas.lastIndex)
                        }
                    }
                },
        ) {
            val esq = larguraEixoY.toPx()
            val base = size.height - 18.dp.toPx()
            val largura = size.width - esq
            listOf(0.0, 0.5, 1.0).forEach { frac ->
                val yy = base - (base * frac).toFloat()
                drawLine(
                    if (frac == 0.0) viz.eixo else viz.grade,
                    Offset(esq, yy), Offset(size.width, yy), 1.dp.toPx(),
                )
                val txt = medidor.measure((maxEscala * frac).eurEixo(), estEixo)
                drawText(txt, topLeft = Offset(esq - 8.dp.toPx() - txt.size.width, yy - txt.size.height / 2f))
            }

            val faixa = largura / pilhas.size
            val larguraCol = minOf(faixa * 0.6f, 24.dp.toPx())
            val folga = 2.dp.toPx()
            val r = 4.dp.toPx()
            pilhas.forEachIndexed { i, p ->
                val x = esq + faixa * i + (faixa - larguraCol) / 2f
                val alfa = if (i == selecionado) 1f else 0.45f
                var topo = base
                val visiveis = p.partes.filter { it.valor > 0.0 }
                visiveis.forEachIndexed { j, parte ->
                    val h = (base * (parte.valor / maxEscala)).toFloat()
                    val cor = (parte.cor ?: viz.magnitude).copy(alpha = alfa)
                    val y0 = topo - h
                    if (j == visiveis.lastIndex) {
                        // só a parcela de cima tem a ponta arredondada
                        colunaTopoArredondado(x, y0, larguraCol, h, cor)
                    } else {
                        drawRect(cor, Offset(x, y0), Size(larguraCol, h))
                    }
                    topo = y0
                    if (j < visiveis.lastIndex && h > folga) {
                        drawRect(superficie, Offset(x, topo), Size(larguraCol, folga))
                    }
                }
                val txt = medidor.measure(p.rotulo, estEixo)
                if (txt.size.width <= faixa - 2.dp.toPx()) {
                    drawText(txt, topLeft = Offset(x + larguraCol / 2f - txt.size.width / 2f, base + 4.dp.toPx()))
                }
            }
        }

        // Decomposição do mês escolhido, em texto.
        val sel = pilhas.getOrNull(selecionado) ?: return@Column
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                sel.total.eurCurto(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "em ${rotuloLeitura(selecionado)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        sel.partes.filter { it.valor > 0.0 }.sortedByDescending { it.valor }.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(f.cor ?: viz.magnitude))
                Spacer(Modifier.width(8.dp))
                Text(
                    f.rotulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    f.valor.eurCurto(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Mapa de calor — atividade por dia                                 */
/* ------------------------------------------------------------------ */

private val PT: Locale = Locale.forLanguageTag("pt-PT")
private val mesCurto = DateTimeFormatter.ofPattern("MMM", PT)
private val diaCompleto = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", PT)

/**
 * Um quadrado por dia, semanas em colunas (segunda em cima), como o calendário
 * de contribuições do GitHub. A intensidade usa a rampa sequencial de um só tom;
 * dias sem nada ficam na cor da grelha. Rola na horizontal e abre no fim (hoje).
 * Tocar num dia mostra o que lá aconteceu, em texto.
 */
@Composable
fun MapaCalor(
    inicio: LocalDate,
    fim: LocalDate,
    contagem: (LocalDate) -> Int,
    descricao: (LocalDate) -> String,
    modifier: Modifier = Modifier,
) {
    val viz = LocalViz.current
    val medidor = rememberTextMeasurer()
    val estEixo = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = viz.esbatido)
    val densidade = LocalDensity.current
    val corSelecao = MaterialTheme.colorScheme.onSurface

    val primeiraSegunda = inicio.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val semanas = (ChronoUnit.WEEKS.between(primeiraSegunda, fim) + 1).toInt()
    val lado = 14.dp
    val folga = 3.dp
    val goteira = 28.dp
    val topoMeses = 16.dp
    val larguraTotal = goteira + (lado + folga) * semanas

    var selecionado by remember(inicio, fim) { mutableStateOf<LocalDate?>(null) }
    // Níveis: 1, 2, 3, 4+ acontecimentos no dia.
    val niveis = listOf(viz.sequencial[1], viz.sequencial[2], viz.sequencial[3], viz.sequencial[5])
    fun cor(n: Int) = if (n <= 0) viz.grade else niveis[(n - 1).coerceAtMost(3)]

    val rolagem = rememberScrollState()
    LaunchedEffect(rolagem.maxValue) { rolagem.scrollTo(rolagem.maxValue) }

    Column(modifier) {
        Text(
            selecionado?.let { "${it.format(diaCompleto).replaceFirstChar { c -> c.uppercase() }} — ${descricao(it)}" }
                ?: "Toca num dia para ver o que aconteceu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().horizontalScroll(rolagem)) {
            Canvas(
                Modifier
                    .width(larguraTotal)
                    .height(topoMeses + (lado + folga) * 7)
                    .pointerInput(inicio, fim) {
                        detectTapGestures { pos ->
                            val passo = (lado + folga).toPx()
                            val col = ((pos.x - goteira.toPx()) / passo).toInt()
                            val lin = ((pos.y - topoMeses.toPx()) / passo).toInt()
                            if (col in 0 until semanas && lin in 0..6) {
                                val d = primeiraSegunda.plusDays(col * 7L + lin)
                                selecionado = if (d.isBefore(inicio) || d.isAfter(fim)) null else d
                            }
                        }
                    },
            ) {
                val passo = (lado + folga).toPx()
                val l = lado.toPx()
                val x0 = goteira.toPx()
                val y0 = topoMeses.toPx()
                val r = 3.dp.toPx()

                // dias da semana na goteira (só alguns, para não encher)
                listOf(0 to "Seg", 2 to "Qua", 4 to "Sex").forEach { (lin, t) ->
                    val txt = medidor.measure(t, estEixo)
                    drawText(txt, topLeft = Offset(0f, y0 + passo * lin + (l - txt.size.height) / 2f))
                }

                var mesAnterior = -1
                for (col in 0 until semanas) {
                    val segunda = primeiraSegunda.plusWeeks(col.toLong())
                    // nome do mês por cima da primeira semana em que ele aparece
                    if (segunda.monthValue != mesAnterior) {
                        mesAnterior = segunda.monthValue
                        val txt = medidor.measure(segunda.format(mesCurto), estEixo)
                        if (x0 + passo * col + txt.size.width <= size.width) {
                            drawText(txt, topLeft = Offset(x0 + passo * col, 0f))
                        }
                    }
                    for (lin in 0..6) {
                        val d = segunda.plusDays(lin.toLong())
                        if (d.isBefore(inicio) || d.isAfter(fim)) continue
                        val topLeft = Offset(x0 + passo * col, y0 + passo * lin)
                        drawRoundRect(cor(contagem(d)), topLeft, Size(l, l), CornerRadius(r, r))
                        if (d == selecionado) {
                            drawRoundRect(
                                corSelecao, topLeft, Size(l, l), CornerRadius(r, r),
                                style = Stroke(width = with(densidade) { 2.dp.toPx() }),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Legenda da rampa: menos → mais.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Menos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            (0..4).forEach { n ->
                Box(Modifier.padding(horizontal = 1.5.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(cor(n)))
            }
            Spacer(Modifier.width(6.dp))
            Text("Mais", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
