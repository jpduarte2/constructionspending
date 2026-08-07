package pt.jpduarte.obragastos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Cores dedicadas a visualização de dados.
 *
 * A ordem de [series] é a ordem validada da paleta categórica — os slots são
 * atribuídos por ordem fixa e nunca reciclados nem gerados. [sequencial] é uma
 * rampa de um só tom (claro -> escuro) para codificar magnitude.
 *
 * [series] tem [N_CORES_CATEGORIA] entradas, e o mesmo índice dá a mesma cor no
 * tema claro e no escuro — é isso que deixa o corIndex da categoria ser um número.
 */
@Immutable
data class VizPalette(
    val series: List<Color>,
    val sequencial: List<Color>,
    /** Tom uniforme das barras de magnitude — o comprimento é que codifica o valor. */
    val magnitude: Color,
    val grade: Color,
    val eixo: Color,
    val esbatido: Color,
    val superficie: Color,
    val tintaPositiva: Color,
    val aviso: Color,
    val critico: Color,
)

/**
 * Nº de slots da paleta categórica.
 *
 * Os 8 primeiros slots nunca podem ser reordenados nem substituídos: o corIndex da
 * categoria guarda o índice, não a cor, e o CSV de backup guarda esse mesmo número.
 * Trocar a ordem mudava a cor de todas as categorias já criadas e de todos os backups
 * antigos. Cores novas acrescentam-se sempre no fim, e [SeriesClaro] e [SeriesEscuro]
 * têm de ter este tamanho.
 */
const val N_CORES_CATEGORIA = 24

private val SeriesClaro = listOf(
    Color(0xFF2A78D6), //  1 azul
    Color(0xFFEB6834), //  2 laranja
    Color(0xFF1BAF7A), //  3 água
    Color(0xFFEDA100), //  4 amarelo
    Color(0xFFE87BA4), //  5 magenta
    Color(0xFF008300), //  6 verde
    Color(0xFF4A3AA7), //  7 violeta
    Color(0xFFE34948), //  8 vermelho
    Color(0xFF0E4C92), //  9 azul-marinho
    Color(0xFF7FB2E8), // 10 azul-céu
    Color(0xFF00A3B5), // 11 ciano
    Color(0xFF0B6B63), // 12 petróleo
    Color(0xFF57C99A), // 13 menta
    Color(0xFF7BC043), // 14 lima
    Color(0xFF4F7A21), // 15 oliva
    Color(0xFF8A5A2B), // 16 castanho
    Color(0xFFC98A5E), // 17 areia
    Color(0xFF4E342E), // 18 chocolate
    Color(0xFFB0447A), // 19 framboesa
    Color(0xFF8E44AD), // 20 púrpura
    Color(0xFFC77DE8), // 21 lilás
    Color(0xFF8B1A1A), // 22 bordô
    Color(0xFFF09A8A), // 23 salmão
    Color(0xFF5A6472), // 24 ardósia
)

private val SeriesEscuro = listOf(
    Color(0xFF3987E5),
    Color(0xFFD95926),
    Color(0xFF199E70),
    Color(0xFFC98500),
    Color(0xFFD55181),
    Color(0xFF008300),
    Color(0xFF9085E9),
    Color(0xFFE66767),
    Color(0xFF5C8FCF),
    Color(0xFFA8CBF0),
    Color(0xFF16B3C4),
    Color(0xFF2A8E85),
    Color(0xFF6FD3AA),
    Color(0xFF8FD055),
    Color(0xFF6E9A3A),
    Color(0xFFA97445),
    Color(0xFFD8A87E),
    Color(0xFFA1887F),
    Color(0xFFC55E90),
    Color(0xFFA65FC4),
    Color(0xFFD49BEE),
    Color(0xFFB24242),
    Color(0xFFF3AC9D),
    Color(0xFF8792A3),
)

private val SequencialClaro = listOf(
    Color(0xFFCDE2FB), Color(0xFF9EC5F4), Color(0xFF6DA7EC),
    Color(0xFF3987E5), Color(0xFF256ABF), Color(0xFF184F95),
)

private val SequencialEscuro = listOf(
    Color(0xFF184F95), Color(0xFF256ABF), Color(0xFF2A78D6),
    Color(0xFF3987E5), Color(0xFF5598E7), Color(0xFF86B6EF),
)

val VizClaro = VizPalette(
    series = SeriesClaro,
    sequencial = SequencialClaro,
    magnitude = Color(0xFF2A78D6),
    grade = Color(0xFFE1E0D9),
    eixo = Color(0xFFC3C2B7),
    esbatido = Color(0xFF898781),
    superficie = Color(0xFFFCFCFB),
    tintaPositiva = Color(0xFF006300),
    aviso = Color(0xFFFAB219),
    critico = Color(0xFFD03B3B),
)

val VizEscuro = VizPalette(
    series = SeriesEscuro,
    sequencial = SequencialEscuro,
    magnitude = Color(0xFF3987E5),
    grade = Color(0xFF2C2C2A),
    eixo = Color(0xFF383835),
    esbatido = Color(0xFF898781),
    superficie = Color(0xFF1A1A19),
    tintaPositiva = Color(0xFF0CA30C),
    aviso = Color(0xFFFAB219),
    critico = Color(0xFFD03B3B),
)

val LocalViz = staticCompositionLocalOf { VizClaro }

private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF2A78D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE2FB),
    onPrimaryContainer = Color(0xFF0D366B),
    secondary = Color(0xFF0F3D3E),
    onSecondary = Color.White,
    // Papéis de contentor definidos explicitamente: por omissão o Material 3
    // usa um esquema arroxeado que não pertence a esta paleta e aparecia na
    // barra de navegação e nos chips selecionados.
    secondaryContainer = Color(0xFFCDE2FB),
    onSecondaryContainer = Color(0xFF0D366B),
    tertiary = Color(0xFF0F3D3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E8E7),
    onTertiaryContainer = Color(0xFF0A2A2B),
    background = Color(0xFFF9F9F7),
    onBackground = Color(0xFF0B0B0B),
    surface = Color(0xFFFCFCFB),
    onSurface = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFFF0EFEC),
    onSurfaceVariant = Color(0xFF52514E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F5),
    surfaceContainer = Color(0xFFF0EFEC),
    surfaceContainerHigh = Color(0xFFEAE9E5),
    surfaceContainerHighest = Color(0xFFE1E0D9),
    outline = Color(0xFF898781),
    outlineVariant = Color(0xFFE1E0D9),
    error = Color(0xFFD03B3B),
    onError = Color.White,
)

private val EsquemaEscuro = darkColorScheme(
    primary = Color(0xFF3987E5),
    onPrimary = Color(0xFF06172E),
    primaryContainer = Color(0xFF184F95),
    onPrimaryContainer = Color(0xFFCDE2FB),
    secondary = Color(0xFF7FC8C4),
    onSecondary = Color(0xFF06231F),
    secondaryContainer = Color(0xFF184F95),
    onSecondaryContainer = Color(0xFFCDE2FB),
    tertiary = Color(0xFF7FC8C4),
    onTertiary = Color(0xFF06231F),
    tertiaryContainer = Color(0xFF17403F),
    onTertiaryContainer = Color(0xFFD6E8E7),
    background = Color(0xFF0D0D0D),
    onBackground = Color.White,
    surface = Color(0xFF1A1A19),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF262625),
    onSurfaceVariant = Color(0xFFC3C2B7),
    surfaceContainerLowest = Color(0xFF101010),
    surfaceContainerLow = Color(0xFF1F1F1E),
    surfaceContainer = Color(0xFF262625),
    surfaceContainerHigh = Color(0xFF2C2C2A),
    surfaceContainerHighest = Color(0xFF383835),
    outline = Color(0xFF898781),
    outlineVariant = Color(0xFF2C2C2A),
    error = Color(0xFFE66767),
    onError = Color(0xFF2A0A0A),
)

@Composable
fun ObraTheme(
    escuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalViz provides if (escuro) VizEscuro else VizClaro) {
        MaterialTheme(
            colorScheme = if (escuro) EsquemaEscuro else EsquemaClaro,
            content = content,
        )
    }
}
