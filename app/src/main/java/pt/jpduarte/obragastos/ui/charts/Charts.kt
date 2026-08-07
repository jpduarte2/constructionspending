package pt.jpduarte.obragastos.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import pt.jpduarte.obragastos.ui.eurCurto
import pt.jpduarte.obragastos.ui.eurEixo
import pt.jpduarte.obragastos.ui.theme.LocalViz

/** Uma fatia de dados nomeada. */
data class Fatia(val rotulo: String, val valor: Double, val cor: Color? = null)

/* ------------------------------------------------------------------ */
/*  Primitivas de desenho                                             */
/* ------------------------------------------------------------------ */

/**
 * Barra horizontal com a extremidade dos dados arredondada (4px) e a base
 * quadrada, ancorada à linha de base. Desenhamos o retângulo arredondado e
 * voltamos a quadrar o lado da base — sem contornos: só a forma.
 */
private fun DrawScope.barraH(x: Float, y: Float, largura: Float, altura: Float, cor: Color) {
    if (largura <= 0f) return
    val r = 4.dp.toPx()
    if (largura <= r) {
        drawRect(cor, Offset(x, y), Size(largura, altura))
        return
    }
    drawRoundRect(
        color = cor,
        topLeft = Offset(x, y),
        size = Size(largura, altura),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
    )
    drawRect(cor, Offset(x, y), Size(r, altura))
}

/** Coluna vertical: topo arredondado, base quadrada. */
private fun DrawScope.colunaV(x: Float, base: Float, largura: Float, altura: Float, cor: Color) {
    if (altura <= 0f) return
    val r = 4.dp.toPx()
    val topo = base - altura
    if (altura <= r) {
        drawRect(cor, Offset(x, topo), Size(largura, altura))
        return
    }
    drawRoundRect(
        color = cor,
        topLeft = Offset(x, topo),
        size = Size(largura, altura),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
    )
    drawRect(cor, Offset(x, topo + r), Size(largura, altura - r))
}

/**
 * Arredonda o máximo do eixo para um número limpo, mas **justo**: passos finos
 * o suficiente para que a coluna mais alta encha o gráfico. Com passos só de
 * 1/2/5, um máximo de 11,8 mil saltava para 20 mil e metade da altura ficava
 * vazia.
 */
internal fun escalaLimpa(max: Double): Double {
    if (max <= 0.0) return 1.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(max)))
    val norm = max / magnitude
    val passo = listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)
        .first { norm <= it + 1e-9 }
    return passo * magnitude
}

/* ------------------------------------------------------------------ */
/*  Legenda                                                           */
/* ------------------------------------------------------------------ */

/**
 * Legenda — sempre presente a partir de duas séries, para que a identidade nunca
 * dependa só da cor. O texto usa tintas de texto; a cor vive no ponto ao lado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legenda(entradas: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        entradas.forEach { (rotulo, cor) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(cor))
                Spacer(Modifier.width(6.dp))
                Text(
                    rotulo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Barras horizontais — magnitude por especialidade                  */
/* ------------------------------------------------------------------ */

/**
 * Ranking de magnitude: barras horizontais, ordenadas da maior para a menor. O
 * **comprimento** é que codifica o valor — a cor serve só de identidade, e por
 * isso cada [Fatia] traz a cor da sua categoria, a mesma que ela tem em toda a
 * aplicação. Sem cor definida cai num tom único, para não inventar identidades
 * que mudariam quando a ordem muda.
 */
@Composable
fun BarrasHorizontais(
    dados: List<Fatia>,
    modifier: Modifier = Modifier,
    alturaBarra: Int = 22,
) {
    if (dados.isEmpty()) return
    val viz = LocalViz.current
    val medidor = rememberTextMeasurer()
    val corTextoPrim = MaterialTheme.colorScheme.onSurface
    val corTextoSec = MaterialTheme.colorScheme.onSurfaceVariant

    val estRotulo = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
    val estValor = MaterialTheme.typography.labelLarge.copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )

    val ordenados = remember(dados) { dados.sortedByDescending { it.valor } }
    val max = remember(ordenados) { ordenados.maxOf { it.valor }.coerceAtLeast(0.01) }

    // Cada linha: rótulo em cima, barra + valor em baixo. Evita cortar nomes
    // longos ("Projetos e licenças") numa goteira estreita à esquerda.
    val alturaLinha = alturaBarra + 26
    Canvas(
        modifier
            .fillMaxWidth()
            .height((alturaLinha * ordenados.size).dp),
    ) {
        val alturaBarraPx = alturaBarra.dp.toPx()
        val alturaLinhaPx = alturaLinha.dp.toPx()

        ordenados.forEachIndexed { i, f ->
            val topoLinha = i * alturaLinhaPx

            // rótulo
            val rot = medidor.measure(f.rotulo, estRotulo.copy(color = corTextoPrim))
            drawText(rot, topLeft = Offset(0f, topoLinha))

            // valor à direita, na mesma linha do rótulo
            val val_ = medidor.measure(f.valor.eurCurto(), estValor.copy(color = corTextoSec))
            drawText(val_, topLeft = Offset(size.width - val_.size.width, topoLinha))

            // Sem pista de fundo: aqui não há limite nenhum a atingir. Uma
            // pista sugeriria um teto inexistente e confundir-se-ia com o
            // medidor de orçamento, onde a pista É o orçamento.
            val yBarra = topoLinha + rot.size.height + 4.dp.toPx()
            barraH(
                x = 0f,
                y = yBarra,
                largura = (size.width * (f.valor / max)).toFloat(),
                altura = alturaBarraPx,
                cor = f.cor ?: viz.magnitude,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Colunas — evolução mensal                                         */
/* ------------------------------------------------------------------ */

/**
 * Evolução mensal. Série única, um só tom, grelha discreta. Tocar numa coluna
 * seleciona-a e mostra o valor — o equivalente táctil do hover.
 */
@Composable
fun ColunasMensais(
    dados: List<Fatia>,
    modifier: Modifier = Modifier,
) {
    if (dados.isEmpty()) return
    val viz = LocalViz.current
    val medidor = rememberTextMeasurer()
    val corTextoPrim = MaterialTheme.colorScheme.onSurface

    var selecionado by remember(dados) { mutableStateOf(dados.lastIndex) }

    val estEixo = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = viz.esbatido)
    val estValor = MaterialTheme.typography.labelLarge.copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = corTextoPrim,
    )

    val maxEscala = remember(dados) { escalaLimpa(dados.maxOf { it.valor }) }

    Column(modifier) {
        // Leitura do mês selecionado — o rótulo direto, em vez de um número
        // em cima de cada coluna.
        val sel = dados.getOrNull(selecionado)
        if (sel != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    sel.valor.eurCurto(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "em ${sel.rotulo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        val larguraEixoY = 56.dp
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(dados) {
                    detectTapGestures { pos ->
                        val esq = larguraEixoY.toPx()
                        val larguraPlot = size.width - esq
                        if (larguraPlot > 0 && pos.x >= esq) {
                            val faixa = larguraPlot / dados.size
                            val i = ((pos.x - esq) / faixa).toInt().coerceIn(0, dados.lastIndex)
                            selecionado = i
                        }
                    }
                },
        ) {
            val esq = larguraEixoY.toPx()
            val alturaEixoX = 18.dp.toPx()
            val base = size.height - alturaEixoX
            val larguraPlot = size.width - esq

            // grelha + escala Y (3 linhas: 0, meio, máximo)
            listOf(0.0, 0.5, 1.0).forEach { frac ->
                val y = base - (base * frac).toFloat()
                drawLine(
                    color = if (frac == 0.0) viz.eixo else viz.grade,
                    start = Offset(esq, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val txt = medidor.measure((maxEscala * frac).eurEixo(), estEixo)
                drawText(
                    txt,
                    topLeft = Offset(
                        esq - 8.dp.toPx() - txt.size.width,
                        y - txt.size.height / 2f,
                    ),
                )
            }

            val faixa = larguraPlot / dados.size
            // barra <= 24px de espessura; a folga da faixa fica a ar
            val larguraCol = minOf(faixa * 0.6f, 24.dp.toPx())

            dados.forEachIndexed { i, f ->
                val centro = esq + faixa * i + faixa / 2f
                val altura = (base * (f.valor / maxEscala)).toFloat()
                val ativo = i == selecionado
                colunaV(
                    x = centro - larguraCol / 2f,
                    base = base,
                    largura = larguraCol,
                    altura = altura,
                    cor = if (ativo) viz.magnitude else viz.magnitude.copy(alpha = 0.38f),
                )

                // rótulo do mês; só desenha se couber na faixa
                val txt = medidor.measure(f.rotulo, estEixo)
                if (txt.size.width <= faixa - 2.dp.toPx()) {
                    drawText(
                        txt,
                        topLeft = Offset(centro - txt.size.width / 2f, base + 4.dp.toPx()),
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Barra empilhada — parte/todo (IVA pago vs poupado)                */
/* ------------------------------------------------------------------ */

/**
 * Parte-todo de duas ou três parcelas numa única barra horizontal empilhada.
 * Escolhida em vez de um donut: comparar ângulos é pior que comparar
 * comprimentos, e um donut de duas fatias não diz nada que a barra não diga.
 *
 * Cada segmento é separado por uma folga de 2px na cor da superfície — a
 * separação é feita pelo espaço, não por contornos.
 */
@Composable
fun BarraEmpilhada(
    partes: List<Fatia>,
    modifier: Modifier = Modifier,
    altura: Int = 28,
) {
    val viz = LocalViz.current
    val medidor = rememberTextMeasurer()
    val total = partes.sumOf { it.valor }
    if (total <= 0.0) return

    val estInterno = MaterialTheme.typography.labelMedium.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(altura.dp),
    ) {
        val folga = 2.dp.toPx()
        val visiveis = partes.filter { it.valor > 0.0 }
        val totalFolgas = folga * (visiveis.size - 1).coerceAtLeast(0)
        val larguraUtil = size.width - totalFolgas
        val r = 4.dp.toPx()

        var x = 0f
        visiveis.forEachIndexed { i, p ->
            val w = (larguraUtil * (p.valor / total)).toFloat()
            val cor = p.cor ?: viz.series[i % viz.series.size]

            // extremidades exteriores arredondadas, interiores retas
            val primeiro = i == 0
            val ultimo = i == visiveis.lastIndex
            if (primeiro || ultimo) {
                drawRoundRect(
                    color = cor,
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
                // quadra o lado que encosta ao vizinho
                if (!ultimo) drawRect(cor, Offset(x + w - r, 0f), Size(r, size.height))
                if (!primeiro) drawRect(cor, Offset(x, 0f), Size(r, size.height))
            } else {
                drawRect(cor, Offset(x, 0f), Size(w, size.height))
            }

            // Percentagem dentro do segmento — só se couber com folga
            // confortável nos dois lados. Nunca cortamos texto.
            val pctTxt = "${Math.round(p.valor / total * 100)}%"
            val txt = medidor.measure(pctTxt, estInterno.copy(color = Color.White))
            if (txt.size.width + 16.dp.toPx() <= w) {
                drawText(
                    txt,
                    topLeft = Offset(
                        x + (w - txt.size.width) / 2f,
                        (size.height - txt.size.height) / 2f,
                    ),
                )
            }
            x += w + folga
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Donut — peso relativo de cada parte no todo                       */
/* ------------------------------------------------------------------ */

/**
 * Peso de cada parte no total. O donut lê-se mal ao pormenor — comparar ângulos
 * é pior que comparar comprimentos — por isso serve só para a **impressão de
 * conjunto** ("uma rubrica domina?"); os valores exatos e a ordem vivem na
 * legenda ao lado, que nunca depende da cor nem do ângulo.
 *
 * O total vai ao centro, onde é lido sem competir com as fatias. Fatias abaixo
 * de [minimoPct] juntam-se num "Outros", senão o anel enche-se de lascas
 * indistinguíveis.
 */
@Composable
fun Donut(
    dados: List<Fatia>,
    modifier: Modifier = Modifier,
    diametro: Int = 180,
    espessura: Int = 28,
    minimoPct: Double = 3.0,
    textoCentro: String? = null,
) {
    val viz = LocalViz.current
    val corTextoPrim = MaterialTheme.colorScheme.onSurface
    val corTextoSec = MaterialTheme.colorScheme.onSurfaceVariant

    val total = dados.sumOf { it.valor }
    if (total <= 0.0) return

    // Agrupar o pó em "Outros" — e só se houver mais que um, senão trocaríamos
    // um nome verdadeiro por um genérico sem ganho nenhum.
    val corOutros = viz.esbatido
    val fatias = remember(dados, total, minimoPct, corOutros) {
        val ordenados = dados.filter { it.valor > 0.0 }.sortedByDescending { it.valor }
        val (grandes, pequenas) = ordenados.partition { it.valor / total * 100.0 >= minimoPct }
        // Cor própria: sem ela "Outros" caía no fallback por posição e roubava a cor a uma rubrica do anel.
        if (pequenas.size > 1) grandes + Fatia("Outros", pequenas.sumOf { it.valor }, corOutros)
        else ordenados
    }

    val cores = fatias.mapIndexed { i, f -> f.cor ?: viz.series[i % viz.series.size] }

    val estCentroVal = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
    )
    val estCentroRot = MaterialTheme.typography.labelSmall

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(diametro.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(diametro.dp)) {
                val traco = espessura.dp.toPx()
                val folgaGraus = if (fatias.size > 1) 1.5f else 0f
                val margem = traco / 2f
                val cantoTL = Offset(margem, margem)
                val tamanho = Size(size.width - traco, size.height - traco)

                var angulo = -90f
                fatias.forEachIndexed { i, f ->
                    val varrimento = (f.valor / total * 360.0).toFloat()
                    drawArc(
                        color = cores[i],
                        startAngle = angulo + folgaGraus / 2f,
                        sweepAngle = (varrimento - folgaGraus).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = cantoTL,
                        size = tamanho,
                        style = Stroke(width = traco, cap = StrokeCap.Butt),
                    )
                    angulo += varrimento
                }
            }

            // Centro: o total, para o anel não obrigar a somar de cabeça.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    total.eurCurto(),
                    style = estCentroVal,
                    color = corTextoPrim,
                )
                if (textoCentro != null) {
                    Text(textoCentro, style = estCentroRot, color = corTextoSec)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Legenda em lista: nome, valor e percentagem alinhados, por ordem
        // decrescente. É aqui que o gráfico se lê ao pormenor.
        fatias.forEachIndexed { i, f ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(cores[i]))
                Spacer(Modifier.width(8.dp))
                Text(
                    f.rotulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = corTextoPrim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    f.valor.eurCurto(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = corTextoPrim,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${Math.round(f.valor / total * 100)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = corTextoSec,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Medidor — um valor contra um limite (orçamento)                   */
/* ------------------------------------------------------------------ */

/**
 * Medidor de orçamento: uma razão contra um limite. A pista é um passo mais
 * claro da mesma rampa e o enchimento carrega a severidade (normal -> aviso ->
 * crítico), para que o estado se leia em toda a barra.
 */
@Composable
fun Medidor(
    gasto: Double,
    limite: Double,
    modifier: Modifier = Modifier,
    altura: Int = 10,
) {
    val viz = LocalViz.current
    if (limite <= 0.0) return
    val frac = (gasto / limite).coerceIn(0.0, 1.0)
    val cor = when {
        gasto > limite -> viz.critico
        gasto >= limite * 0.85 -> viz.aviso
        else -> viz.magnitude
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(altura.dp),
    ) {
        val r = altura.dp.toPx() / 2f
        drawRoundRect(
            color = viz.grade,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        val w = (size.width * frac).toFloat()
        if (w > 0f) {
            clipRect(right = w) {
                drawRoundRect(
                    color = cor,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
            }
        }
    }
}
