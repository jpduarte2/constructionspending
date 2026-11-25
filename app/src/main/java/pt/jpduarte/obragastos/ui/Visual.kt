package pt.jpduarte.obragastos.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pt.jpduarte.obragastos.ui.theme.LocalViz

/*
 * Peças visuais partilhadas pelos ecrãs: o cartão em destaque, o quadrado de
 * cor das categorias, as etiquetas, a barra e o anel de progresso e o ecrã
 * vazio. Só aspeto — nenhuma conta mora aqui.
 */

/** O gradiente dos cartões em destaque (o total da obra, o resumo das despesas). */
@Composable
fun gradienteDestaque(): Brush = Brush.linearGradient(
    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
)

/**
 * O cartão grande no topo de um ecrã, em gradiente. O texto dentro deve usar
 * [corNoDestaque] (e [corNoDestaqueSuave] para os rótulos).
 */
@Composable
fun CartaoDestaque(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(gradienteDestaque())
            .padding(20.dp),
    ) {
        Column(content = conteudo)
    }
}

val corNoDestaque: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
val corNoDestaqueSuave: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)

/** Quadrado arredondado na cor da categoria, com a inicial do nome. */
@Composable
fun IconeCategoria(nome: String, cor: Color, tamanho: Dp = 40.dp) {
    Box(
        Modifier.size(tamanho).clip(RoundedCornerShape(tamanho / 3.3f)).background(cor.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            nome.trim().firstOrNull()?.uppercase() ?: "•",
            style = if (tamanho >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = cor,
        )
    }
}

/** Uma etiqueta pequena ("bancário", "fora da obra", "poupou 230 €"). */
@Composable
fun Selo(texto: String, cor: Color) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = cor,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(cor.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * A cor de um gasto face ao orçamento: a tinta positiva até 85 %, o aviso até
 * 100 % e o crítico acima.
 */
@Composable
fun corOrcamento(fracao: Double): Color {
    val viz = LocalViz.current
    return when {
        fracao > 1.0 -> viz.critico
        fracao > 0.85 -> viz.aviso
        else -> viz.tintaPositiva
    }
}

/** Barra de progresso arredondada (a fração é cortada a 0..1 no desenho). */
@Composable
fun BarraProgresso(fracao: Double, cor: Color, fundo: Color, modifier: Modifier = Modifier, altura: Dp = 8.dp) {
    val f by animateFloatAsState(fracao.toFloat().coerceIn(0f, 1f), label = "barra")
    Box(modifier.fillMaxWidth().height(altura).clip(CircleShape).background(fundo)) {
        Box(Modifier.fillMaxWidth(f).height(altura).clip(CircleShape).background(cor))
    }
}

/** Um anel de progresso com o conteúdo ao centro. */
@Composable
fun AnelProgresso(fracao: Double, cor: Color, tamanho: Dp = 56.dp, traco: Dp = 6.dp, centro: @Composable () -> Unit = {}) {
    val pista = LocalViz.current.grade
    val f by animateFloatAsState(fracao.toFloat().coerceIn(0f, 1f), label = "anel")
    Box(Modifier.size(tamanho), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = traco.toPx()
            val area = Size(size.width - t, size.height - t)
            val canto = Offset(t / 2, t / 2)
            drawArc(pista, 0f, 360f, false, canto, area, style = Stroke(t))
            drawArc(cor, -90f, 360f * f, false, canto, area, style = Stroke(t, cap = StrokeCap.Round))
        }
        centro()
    }
}

/** Ecrã vazio com uma ilustração em vez de só texto. */
@Composable
fun EstadoVazioIlustrado(
    icone: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    botao: String? = null,
    onBotao: () -> Unit = {},
) {
    val cores = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(gradienteDestaque()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icone, contentDescription = null, tint = cores.primary, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = cores.onSurfaceVariant, textAlign = TextAlign.Center)
        if (botao != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onBotao) { Text(botao) }
        }
    }
}

/** Espaço horizontal pequeno entre etiquetas. */
@Composable
fun EspacoSelo() = Spacer(Modifier.width(4.dp))
