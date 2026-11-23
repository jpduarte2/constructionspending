package pt.jpduarte.obragastos.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.jpduarte.obragastos.ui.theme.LocalViz

/**
 * Abertura da aplicação: uma casa que se constrói sozinha.
 *
 * As paredes sobem do chão, o telhado desce e assenta por cima, e ao lado
 * crescem as barras de um gráfico — a app inteira numa imagem. Toca-se em
 * qualquer sítio para saltar.
 *
 * Quem tiver as animações desligadas nas definições do Android vê apenas o
 * resultado final: movimento inesperado no arranque é dos gatilhos mais comuns
 * de enjoo em quem tem sensibilidade vestibular.
 */
@Composable
fun EcraAbertura(onTerminar: () -> Unit) {
    val viz = LocalViz.current
    val terminar by rememberUpdatedState(onTerminar)

    val contexto = LocalContext.current
    val comMovimento = remember {
        Settings.Global.getFloat(
            contexto.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }

    val inicio = if (comMovimento) 0f else 1f
    val chao = remember { Animatable(inicio) }
    val paredes = remember { Animatable(inicio) }
    val telhado = remember { Animatable(inicio) }
    val barras = remember { Animatable(inicio) }
    val texto = remember { Animatable(inicio) }
    val assinatura = remember { Animatable(inicio) }

    LaunchedEffect(Unit) {
        if (!comMovimento) {
            delay(1200)
            terminar()
            return@LaunchedEffect
        }
        launch { chao.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        delay(160)
        launch { paredes.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        delay(380)
        launch { telhado.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        delay(240)
        launch { barras.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
        delay(200)
        launch { texto.animateTo(1f, tween(420, easing = LinearEasing)) }
        delay(240)
        launch { assinatura.animateTo(1f, tween(520, easing = LinearEasing)) }
        delay(1000)
        terminar()
    }

    // Cores lidas aqui fora: dentro do Canvas já não há acesso ao tema.
    val fundo = MaterialTheme.colorScheme.background
    val corChao = viz.eixo
    val corParedes = viz.series[0]
    val corTelhado = viz.series[1]
    val corBarras = viz.series[2]
    val corFio = viz.esbatido

    val interacao = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(fundo)
            .clickable(
                interactionSource = interacao,
                indication = null,
                onClickLabel = "Saltar a abertura",
            ) { terminar() }
            .semantics {
                contentDescription = "Obra — Construção Jovim 2026. Made by João Duarte."
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Canvas(Modifier.size(200.dp)) {
                val w = size.width
                val h = size.height
                val yChao = h * 0.80f
                val larguraCasa = w * 0.42f
                val alturaCasa = h * 0.34f
                val esq = w * 0.30f - larguraCasa / 2f
                val meio = esq + larguraCasa / 2f

                // chão, a abrir do centro para os lados
                drawLine(
                    color = corChao,
                    start = Offset(w * 0.5f - w * 0.46f * chao.value, yChao),
                    end = Offset(w * 0.5f + w * 0.46f * chao.value, yChao),
                    strokeWidth = h * 0.022f,
                    cap = StrokeCap.Round,
                )

                // paredes, a crescer do chão para cima
                val alturaAtual = alturaCasa * paredes.value
                if (alturaAtual > 0f) {
                    drawRect(
                        color = corParedes,
                        topLeft = Offset(esq, yChao - alturaAtual),
                        size = Size(larguraCasa, alturaAtual),
                    )
                }

                // porta, recortada na parede depois de ela estar de pé
                if (paredes.value > 0.75f) {
                    val alturaPorta = alturaCasa * 0.46f * paredes.value
                    val larguraPorta = larguraCasa * 0.26f
                    drawRect(
                        color = fundo,
                        topLeft = Offset(meio - larguraPorta / 2f, yChao - alturaPorta),
                        size = Size(larguraPorta, alturaPorta),
                    )
                }

                // telhado, a descer até assentar
                if (telhado.value > 0f) {
                    val topo = yChao - alturaCasa
                    val beira = w * 0.05f
                    val alto = h * 0.18f
                    val queda = (1f - telhado.value) * h * 0.30f
                    drawPath(
                        path = Path().apply {
                            moveTo(esq - beira, topo + queda)
                            lineTo(meio, topo - alto + queda)
                            lineTo(esq + larguraCasa + beira, topo + queda)
                            close()
                        },
                        color = corTelhado,
                        alpha = telhado.value.coerceIn(0f, 1f),
                    )

                    // o fio do guindaste que trouxe o telhado, a esbater-se
                    val fio = (1f - telhado.value).coerceIn(0f, 1f)
                    if (fio > 0.02f) {
                        drawLine(
                            color = corFio,
                            start = Offset(meio, 0f),
                            end = Offset(meio, topo - alto + queda),
                            strokeWidth = h * 0.006f,
                            alpha = fio * 0.7f,
                        )
                    }
                }

                // barras do gráfico, cada uma a arrancar depois da anterior
                val alturas = listOf(0.20f, 0.34f, 0.50f)
                val largura = w * 0.06f
                alturas.forEachIndexed { i, alt ->
                    val p = ((barras.value - i * 0.16f) / 0.68f).coerceIn(0f, 1f)
                    if (p <= 0f) return@forEachIndexed
                    val altura = h * alt * p
                    drawRect(
                        color = corBarras,
                        topLeft = Offset(w * 0.63f + i * (largura + w * 0.04f), yChao - altura),
                        size = Size(largura, altura),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Obra",
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer {
                    alpha = texto.value
                    translationY = (1f - texto.value) * 24f
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Construção 2026",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = texto.value },
            )
        }

        Text(
            "Made by João Duarte",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .graphicsLayer {
                    alpha = assinatura.value
                    translationY = (1f - assinatura.value) * 16f
                },
        )
    }
}
