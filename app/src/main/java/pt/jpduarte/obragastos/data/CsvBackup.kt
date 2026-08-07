package pt.jpduarte.obragastos.data

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Exportação e importação de tudo o que a app guarda, num único ficheiro CSV.
 *
 * O formato é feito para servir dois donos: o Excel (que é onde o ficheiro vai
 * acabar por ser aberto) e a própria app (que tem de o conseguir reler sem
 * perder nada). Daí as escolhas: separador `;` e decimais com vírgula, que é o
 * que o Excel português espera, e datas em ISO, que não são ambíguas.
 *
 * As quatro tabelas vivem no mesmo ficheiro, em secções marcadas por uma linha
 * `#NOME`. Os pagamentos referem a categoria **pelo nome** e não pelo id, para
 * que o ficheiro continue a fazer sentido a olho e para que a importação não
 * dependa de ids que já não existem.
 *
 * Secções desconhecidas são ignoradas em silêncio e secções em falta ficam
 * vazias — é o que permite ler ficheiros de outras versões sem rebentar.
 */
object CsvBackup {

    private const val SEP = ';'
    private const val CABECALHO = "#OBRAGASTOS"
    private const val VERSAO = 1

    private const val SEC_ORCAMENTO = "#ORCAMENTO"
    private const val SEC_CATEGORIAS = "#CATEGORIAS"
    private const val SEC_PAGAMENTOS = "#PAGAMENTOS"
    private const val SEC_EVENTOS = "#EVENTOS"

    private val PT: Locale = Locale.forLanguageTag("pt-PT")

    /** Tudo o que um ficheiro de backup transporta. */
    data class Conteudo(
        val orcamentoTotal: Double,
        val categorias: List<Categoria>,
        val pagamentos: List<PagamentoImportado>,
        val eventos: List<EventoObra>,
    )

    /**
     * Um pagamento lido do ficheiro. A categoria vem por nome porque os ids do
     * ficheiro não valem nada na base de dados de destino — são reatribuídos na
     * importação.
     */
    data class PagamentoImportado(val categoriaNome: String, val pagamento: Pagamento)

    /* ------------------------------ exportar ------------------------------ */

    fun exportar(
        orcamentoTotal: Double,
        categorias: List<Categoria>,
        pagamentos: List<Pagamento>,
        eventos: List<EventoObra>,
    ): String {
        val nomePorId = categorias.associate { it.id to it.nome }
        val sb = StringBuilder()

        sb.linha(CABECALHO, "versao", VERSAO.toString())
        sb.append("\r\n")

        sb.linha(SEC_ORCAMENTO)
        sb.linha("orcamentoTotal")
        sb.linha(dinheiro(orcamentoTotal))
        sb.append("\r\n")

        sb.linha(SEC_CATEGORIAS)
        sb.linha("nome", "corIndex", "orcamento")
        categorias.forEach { c ->
            sb.linha(c.nome, c.corIndex.toString(), dinheiro(c.orcamento))
        }
        sb.append("\r\n")

        sb.linha(SEC_PAGAMENTOS)
        sb.linha(
            "data", "categoria", "descricao", "valorBase", "baseTributavel",
            "taxaIva", "ivaPago", "contaParaObra", "notas",
        )
        pagamentos.forEach { p ->
            sb.linha(
                LocalDate.ofEpochDay(p.dataEpochDay).toString(),
                nomePorId[p.categoriaId] ?: "",
                p.descricao,
                dinheiro(p.valorBase),
                dinheiro(p.baseTributavel),
                dinheiro(p.taxaIva),
                booleano(p.ivaPago),
                booleano(p.contaParaObra),
                p.notas,
            )
        }
        sb.append("\r\n")

        sb.linha(SEC_EVENTOS)
        sb.linha("data", "titulo", "descricao")
        eventos.forEach { e ->
            sb.linha(LocalDate.ofEpochDay(e.dataEpochDay).toString(), e.titulo, e.descricao)
        }

        return sb.toString()
    }

    /* ------------------------------ importar ------------------------------ */

    /**
     * Lê um ficheiro produzido por [exportar].
     *
     * @throws IllegalArgumentException com uma mensagem que se possa mostrar ao
     *   utilizador — o ficheiro vem de fora e pode estar tudo menos correto.
     */
    fun importar(texto: String): Conteudo {
        val linhas = tokenizar(texto.removePrefix("\uFEFF"))
            .filterNot { it.size == 1 && it[0].isBlank() }

        require(linhas.isNotEmpty()) { "O ficheiro está vazio." }
        require(linhas.first().firstOrNull()?.trim() == CABECALHO) {
            "Isto não parece um ficheiro do ObraGastos (falta a linha $CABECALHO)."
        }

        var orcamento = 0.0
        val categorias = mutableListOf<Categoria>()
        val pagamentos = mutableListOf<PagamentoImportado>()
        val eventos = mutableListOf<EventoObra>()

        var seccao: String? = null
        var primeiraDaSeccao = false

        // A primeira linha de cada secção é o cabeçalho das colunas e salta-se:
        // as colunas são lidas por posição, não por nome.
        linhas.drop(1).forEach { campos ->
            val marcador = campos.firstOrNull()?.trim().orEmpty()
            if (marcador.startsWith("#")) {
                seccao = marcador
                primeiraDaSeccao = true
                return@forEach
            }
            if (primeiraDaSeccao) {
                primeiraDaSeccao = false
                return@forEach
            }
            when (seccao) {
                SEC_ORCAMENTO -> orcamento = numero(campos.getOrNull(0)) ?: 0.0

                SEC_CATEGORIAS -> {
                    val nome = campos.getOrNull(0)?.trim().orEmpty()
                    if (nome.isNotEmpty()) {
                        categorias += Categoria(
                            nome = nome,
                            corIndex = campos.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
                            orcamento = numero(campos.getOrNull(2)) ?: 0.0,
                        )
                    }
                }

                SEC_PAGAMENTOS -> {
                    val dia = data(campos.getOrNull(0))
                        ?: erro("Data de pagamento inválida: “${campos.getOrNull(0)}”.")
                    val valorBase = numero(campos.getOrNull(3))
                        ?: erro("Valor inválido no pagamento de ${campos.getOrNull(0)}.")
                    val taxa = numero(campos.getOrNull(5)) ?: 0.0
                    // Um ficheiro editado à mão pode trazer a base tributável em
                    // branco; nesse caso o IVA incide sobre tudo, que é o normal.
                    val base = numero(campos.getOrNull(4)) ?: valorBase
                    // Taxa zero significa IVA evitado, não isenção: guarda-se à
                    // taxa normal com o imposto por pagar, ou seja, poupado.
                    val semIva = taxa <= 0.0
                    pagamentos += PagamentoImportado(
                        categoriaNome = campos.getOrNull(1)?.trim().orEmpty(),
                        pagamento = Pagamento(
                            categoriaId = 0L,
                            descricao = campos.getOrNull(2)?.trim().orEmpty(),
                            dataEpochDay = dia.toEpochDay(),
                            valorBase = valorBase,
                            baseTributavel = if (semIva) valorBase else base.coerceIn(0.0, valorBase),
                            taxaIva = if (semIva) TAXA_IVA_NORMAL else taxa,
                            ivaPago = if (semIva) false else booleano(campos.getOrNull(6), predefinido = true),
                            contaParaObra = booleano(campos.getOrNull(7), predefinido = true),
                            notas = campos.getOrNull(8)?.trim().orEmpty(),
                        ),
                    )
                }

                SEC_EVENTOS -> {
                    val dia = data(campos.getOrNull(0))
                        ?: erro("Data de evento inválida: “${campos.getOrNull(0)}”.")
                    val titulo = campos.getOrNull(1)?.trim().orEmpty()
                    if (titulo.isNotEmpty()) {
                        eventos += EventoObra(
                            dataEpochDay = dia.toEpochDay(),
                            titulo = titulo,
                            descricao = campos.getOrNull(2)?.trim().orEmpty(),
                            ordem = eventos.size,
                        )
                    }
                }
            }
        }

        require(categorias.isNotEmpty() || pagamentos.isNotEmpty() || eventos.isNotEmpty()) {
            "O ficheiro não tem dados para importar."
        }
        return Conteudo(orcamento, categorias, pagamentos, eventos)
    }

    /* ------------------------------ formato ------------------------------ */

    private fun StringBuilder.linha(vararg campos: String) {
        append(campos.joinToString(SEP.toString()) { escapar(it) })
        append("\r\n")
    }

    /** Aspas só quando são precisas, e as que existam no texto duplicam-se. */
    private fun escapar(campo: String): String =
        if (campo.any { it == SEP || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + campo.replace("\"", "\"\"") + "\""
        } else {
            campo
        }

    private fun dinheiro(v: Double): String = String.format(PT, "%.2f", v)

    private fun booleano(b: Boolean): String = if (b) "Sim" else "Não"

    private fun booleano(texto: String?, predefinido: Boolean): Boolean {
        val t = texto?.trim()?.lowercase().orEmpty()
        return when {
            t.isEmpty() -> predefinido
            t.startsWith("s") || t == "true" || t == "1" || t == "sim" -> true
            t.startsWith("n") || t == "false" || t == "0" -> false
            else -> predefinido
        }
    }

    /** Aceita "1 234,56" e "1234.56" — o ficheiro pode ter passado pelo Excel. */
    private fun numero(texto: String?): Double? {
        val limpo = texto?.trim()?.replace("€", "")?.replace(" ", "")?.replace("\u00A0", "")
        if (limpo.isNullOrEmpty()) return null
        return limpo.replace(",", ".").toDoubleOrNull()
    }

    private fun data(texto: String?): LocalDate? {
        val t = texto?.trim().orEmpty()
        if (t.isEmpty()) return null
        return try {
            LocalDate.parse(t)
        } catch (e: DateTimeParseException) {
            // O Excel gosta de reescrever datas como dd/MM/yyyy.
            val p = t.split('/', '-')
            if (p.size != 3) return null
            val d = p[0].toIntOrNull() ?: return null
            val m = p[1].toIntOrNull() ?: return null
            val a = p[2].toIntOrNull() ?: return null
            try {
                LocalDate.of(a, m, d)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun erro(mensagem: String): Nothing = throw IllegalArgumentException(mensagem)

    /**
     * Parte o texto em linhas de campos, respeitando aspas — um campo entre
     * aspas pode conter `;` e mudanças de linha (as notas contêm ambos).
     */
    private fun tokenizar(texto: String): List<List<String>> {
        val linhas = mutableListOf<List<String>>()
        var campos = mutableListOf<String>()
        val atual = StringBuilder()
        var dentroDeAspas = false
        var i = 0

        fun fecharCampo() {
            campos.add(atual.toString())
            atual.setLength(0)
        }

        fun fecharLinha() {
            fecharCampo()
            linhas.add(campos)
            campos = mutableListOf()
        }

        while (i < texto.length) {
            val c = texto[i]
            when {
                dentroDeAspas && c == '"' && texto.getOrNull(i + 1) == '"' -> {
                    atual.append('"')
                    i++
                }
                c == '"' -> dentroDeAspas = !dentroDeAspas
                !dentroDeAspas && c == SEP -> fecharCampo()
                !dentroDeAspas && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && texto.getOrNull(i + 1) == '\n') i++
                    fecharLinha()
                }
                else -> atual.append(c)
            }
            i++
        }
        if (atual.isNotEmpty() || campos.isNotEmpty()) fecharLinha()

        return linhas
    }
}
