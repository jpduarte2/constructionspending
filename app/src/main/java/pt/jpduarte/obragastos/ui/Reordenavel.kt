package pt.jpduarte.obragastos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Arrastar para reordenar numa `LazyColumn`.
 *
 * A conta é toda feita sobre o que a lista já sabe desenhar: enquanto se
 * arrasta, o bloco escolhido anda com o dedo (só visualmente) e, mal o seu
 * centro entra no espaço de outro bloco, os dois trocam de lugar na lista de
 * verdade. Assim funciona com blocos de alturas diferentes — que é o caso dos
 * gráficos — sem ter de medir nada à mão.
 *
 * Requisito: os `item`/`items` têm de ter `key` estável, senão o Compose perde
 * o rasto do bloco a meio do arrasto.
 */
class EstadoReordenacao(
    private val lista: LazyListState,
    private val aoMover: (de: Int, para: Int) -> Unit,
) {
    /** Índice do bloco que está agora na mão. `null` = ninguém a arrastar. */
    var indiceArrastado by mutableStateOf<Int?>(null)
        private set

    private var offsetInicial by mutableIntStateOf(0)
    private var acumulado by mutableFloatStateOf(0f)

    /** Píxeis por fotograma a rolar sozinho quando se arrasta junto às bordas. */
    var rolagem by mutableFloatStateOf(0f)
        private set

    private val info: LazyListItemInfo?
        get() = indiceArrastado?.let { i ->
            lista.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
        }

    /**
     * Quanto o bloco arrastado tem de ser desenhado fora do seu lugar. Depois de
     * uma troca o lugar já é outro, por isso desconta-se sempre a posição atual.
     */
    val deslocamento: Float
        get() = info?.let { offsetInicial + acumulado - it.offset } ?: 0f

    fun iniciar(indice: Int) {
        val alvo = lista.layoutInfo.visibleItemsInfo.firstOrNull { it.index == indice } ?: return
        indiceArrastado = indice
        offsetInicial = alvo.offset
        acumulado = 0f
        rolagem = 0f
    }

    fun terminar() {
        indiceArrastado = null
        acumulado = 0f
        rolagem = 0f
    }

    fun arrastar(dy: Float) {
        acumulado += dy
        val atual = info ?: return

        val inicio = atual.offset + deslocamento
        val fim = inicio + atual.size
        val centro = inicio + atual.size / 2f

        val vizinho = lista.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != atual.index && centro.toInt() in it.offset..(it.offset + it.size)
        }
        if (vizinho != null) {
            aoMover(atual.index, vizinho.index)
            indiceArrastado = vizinho.index
        }

        val topo = lista.layoutInfo.viewportStartOffset.toFloat()
        val base = lista.layoutInfo.viewportEndOffset.toFloat()
        rolagem = when {
            fim > base - MARGEM -> (fim - (base - MARGEM)).coerceAtMost(VELOCIDADE)
            inicio < topo + MARGEM -> (inicio - (topo + MARGEM)).coerceAtLeast(-VELOCIDADE)
            else -> 0f
        }
    }

    private companion object {
        /** Faixa junto às bordas onde a lista começa a rolar sozinha. */
        const val MARGEM = 140f
        const val VELOCIDADE = 28f
    }
}

@Composable
fun rememberEstadoReordenacao(
    lista: LazyListState,
    aoMover: (de: Int, para: Int) -> Unit,
): EstadoReordenacao {
    val mover by rememberUpdatedState(aoMover)
    val estado = remember(lista) { EstadoReordenacao(lista) { de, para -> mover(de, para) } }

    // Chegar ao fundo do ecrã com o dedo tem de continuar a lista a andar,
    // senão não há como mover um bloco para além do que está visível.
    val aArrastar = estado.indiceArrastado != null
    LaunchedEffect(aArrastar) {
        if (!aArrastar) return@LaunchedEffect
        while (true) {
            val passo = estado.rolagem
            if (passo != 0f) lista.scrollBy(passo)
            androidx.compose.runtime.withFrameNanos { }
        }
    }
    return estado
}

/** Levanta o bloco arrastado acima dos outros e cola-o ao dedo. */
@Composable
fun Modifier.blocoReordenavel(estado: EstadoReordenacao, indice: Int): Modifier {
    val arrastado = estado.indiceArrastado == indice
    return this
        .zIndex(if (arrastado) 1f else 0f)
        .graphicsLayer {
            if (estado.indiceArrastado == indice) {
                translationY = estado.deslocamento
                scaleX = 1.02f
                scaleY = 1.02f
                shadowElevation = 12f
            }
        }
}

/**
 * A pega. Arrasta-se para cima e para baixo; um toque abre um menu com as
 * mesmas ações em palavras, para quem não consegue (ou não quer) arrastar.
 */
@Composable
fun PegaArrasto(
    estado: EstadoReordenacao,
    indice: Int,
    total: Int,
    chave: Any,
    aoMover: (de: Int, para: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // O índice muda a meio do arrasto, quando os blocos trocam de lugar; a pega
    // tem de continuar a ser a mesma, por isso o gesto não pode depender dele.
    val indiceAtual by rememberUpdatedState(indice)
    val haptica = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }

    Box(modifier) {
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = "Mover este bloco",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { menu = true }
                .pointerInput(chave) {
                    detectDragGestures(
                        onDragStart = {
                            haptica.performHapticFeedback(HapticFeedbackType.LongPress)
                            estado.iniciar(indiceAtual)
                        },
                        onDragEnd = { estado.terminar() },
                        onDragCancel = { estado.terminar() },
                        onDrag = { evento, delta ->
                            evento.consume()
                            estado.arrastar(delta.y)
                        },
                    )
                }
                .padding(8.dp),
        )

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (indice > 0) {
                DropdownMenuItem(
                    text = { Text("Mover para cima") },
                    onClick = { menu = false; aoMover(indice, indice - 1) },
                )
                DropdownMenuItem(
                    text = { Text("Mover para o topo") },
                    onClick = { menu = false; aoMover(indice, 0) },
                )
            }
            if (indice < total - 1) {
                DropdownMenuItem(
                    text = { Text("Mover para baixo") },
                    onClick = { menu = false; aoMover(indice, indice + 1) },
                )
                DropdownMenuItem(
                    text = { Text("Mover para o fim") },
                    onClick = { menu = false; aoMover(indice, total - 1) },
                )
            }
        }
    }
}
