package pt.jpduarte.obragastos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * As fotos do livro de obra.
 *
 * Uma foto de telemóvel tem 3 a 8 MB; guardada assim, um livro com cem fotos
 * passava o meio giga. Por isso cada foto é reduzida ao entrar — lado maior com
 * [LADO_MAX] px, JPEG a [QUALIDADE] — e fica com uns 200 a 300 KB, nítida no
 * ecrã do telemóvel. O original não é tocado: continua na galeria.
 *
 * Os ficheiros vivem em `files/fotos/`, dentro da app (desaparecem com ela) e
 * incluídos na cópia de segurança do Android. Na base de dados fica só o nome.
 */
object Fotos {

    private const val PASTA = "fotos"
    private const val LADO_MAX = 1600
    private const val QUALIDADE = 80

    fun pasta(context: Context): File = File(context.filesDir, PASTA).apply { mkdirs() }

    fun ficheiro(context: Context, nome: String): File = File(pasta(context), nome)

    /** Pasta temporária para a câmara escrever a foto antes de ser reduzida. */
    fun pastaCamara(context: Context): File = File(context.cacheDir, "camara").apply { mkdirs() }

    /**
     * Lê a imagem de [uri], reduz, endireita conforme o EXIF e grava-a na pasta
     * das fotos. Devolve o nome do ficheiro novo. Corre fora da thread principal.
     */
    fun importar(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        val maior = max(limites.outWidth, limites.outHeight)
        require(maior > 0) { "Não foi possível ler a imagem." }

        // Descodificar já reduzido: abrir uma foto de 48 MP inteira esgotava a memória.
        var amostra = 1
        while (maior / (amostra * 2) >= LADO_MAX) amostra *= 2
        val lida = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        } ?: error("Não foi possível ler a imagem.")

        val escala = min(1f, LADO_MAX.toFloat() / max(lida.width, lida.height))
        val matriz = Matrix().apply {
            postScale(escala, escala)
            postRotate(rotacaoExif(context, uri).toFloat())
        }
        val final = Bitmap.createBitmap(lida, 0, 0, lida.width, lida.height, matriz, true)

        val nome = "foto-${UUID.randomUUID()}.jpg"
        ficheiro(context, nome).outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, QUALIDADE, it) }
        if (final !== lida) final.recycle()
        lida.recycle()
        return nome
    }

    private fun rotacaoExif(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    fun apagar(context: Context, nomes: Collection<String>) {
        nomes.forEach { nome ->
            ficheiro(context, nome).delete()
            cache.snapshot().keys.filter { it.startsWith("$nome@") }.forEach { cache.remove(it) }
        }
    }

    /** Espaço ocupado por todas as fotos, em bytes. */
    fun espacoOcupado(context: Context): Long =
        pasta(context).listFiles()?.sumOf { it.length() } ?: 0L

    /* ------------------------------ leitura ------------------------------ */

    // Um oitavo da memória da app para miniaturas — chega para uma lista longa
    // sem voltar a descodificar ao rolar.
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8 / 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /**
     * Lê a foto [nome] com o lado menor perto de [ladoPx] (nunca abaixo, para
     * não ficar desfocada). Devolve null se o ficheiro já não existir.
     */
    fun carregar(context: Context, nome: String, ladoPx: Int): Bitmap? {
        val chave = "$nome@$ladoPx"
        cache.get(chave)?.let { return it }
        val f = ficheiro(context, nome)
        if (!f.exists()) return null
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, limites)
        val menor = min(limites.outWidth, limites.outHeight)
        var amostra = 1
        while (menor / (amostra * 2) >= ladoPx) amostra *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = amostra })
            ?: return null
        cache.put(chave, bmp)
        return bmp
    }

    /** "2,4 MB" / "830 KB". */
    fun tamanhoTexto(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.forLanguageTag("pt-PT"), "%.1f MB", bytes / 1024.0 / 1024.0)
        else -> "${(bytes / 1024.0).roundToInt()} KB"
    }
}
