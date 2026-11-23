package pt.jpduarte.obragastos.data

import java.time.LocalDate
import java.time.YearMonth
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
 * As tabelas vivem todas no mesmo ficheiro, em secções marcadas por uma linha
 * `#NOME`. Os pagamentos referem a categoria **pelo nome** e não pelo id, para
 * que o ficheiro continue a fazer sentido a olho e para que a importação não
 * dependa de ids que já não existem.
 *
 * Secções desconhecidas são ignoradas em silêncio e secções em falta ficam
 * vazias — é o que permite ler ficheiros de outras versões sem rebentar.
 * Versão 5: a origem dos fundos de cada pagamento (`Pessoal`/`Bancário`), no
 * fim de #PAGAMENTOS; sem ela o pagamento entra como pessoal.
 * Versão 4: recorrentes com qualquer intervalo (unidade, intervalo, início,
 * contagem, número máximo de vezes), em colunas novas no fim.
 * Versão 3: despesas fixas (#DESPESAS_FIXAS) e, nas despesas, a chave da fixa
 * que as lançou e a marca "a confirmar".
 * Colunas novas acrescentam-se sempre no fim, e a falta delas tem um valor por
 * omissão: um ficheiro da versão 1 continua a importar-se.
 *
 * As fotos do livro de obra não vão no CSV (é texto); só os nomes dos ficheiros,
 * para voltarem a ligar-se a um evento quando se importa no mesmo telemóvel.
 */
object CsvBackup {

    private const val SEP = ';'
    private const val CABECALHO = "#OBRAGASTOS"
    private const val VERSAO = 5

    private const val SEC_ORCAMENTO = "#ORCAMENTO"
    private const val SEC_CATEGORIAS = "#CATEGORIAS"
    private const val SEC_PAGAMENTOS = "#PAGAMENTOS"
    private const val SEC_EVENTOS = "#EVENTOS"
    private const val SEC_DESPESAS = "#DESPESAS"
    private const val SEC_FIXAS = "#DESPESAS_FIXAS"

    private val PT: Locale = Locale.forLanguageTag("pt-PT")

    /** Tudo o que um ficheiro de backup transporta. */
    data class Conteudo(
        val orcamentoTotal: Double,
        val categorias: List<Categoria>,
        val pagamentos: List<PagamentoImportado>,
        val eventos: List<EventoObra>,
        val despesas: List<DespesaImportada> = emptyList(),
        val areaConstrucao: Double = 0.0,
        /** As despesas fixas, cada uma com a chave por que as despesas a referem. */
        val fixas: List<Pair<String, DespesaFixa>> = emptyList(),
    )

    /**
     * Uma despesa lida do ficheiro, com a chave da fixa que a lançou (vazia se
     * nenhuma) — os ids da fixa são reatribuídos na importação.
     */
    data class DespesaImportada(val fixa: String, val despesa: Despesa)

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
        despesas: List<Despesa> = emptyList(),
        areaConstrucao: Double = 0.0,
        fixas: List<DespesaFixa> = emptyList(),
    ): String {
        val nomePorId = categorias.associate { it.id to it.nome }
        val sb = StringBuilder()

        sb.linha(CABECALHO, "versao", VERSAO.toString())
        sb.append("\r\n")

        sb.linha(SEC_ORCAMENTO)
        sb.linha("orcamentoTotal", "areaConstrucao")
        sb.linha(dinheiro(orcamentoTotal), dinheiro(areaConstrucao))
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
            "taxaIva", "ivaPago", "contaParaObra", "notas", "fornecedor", "origemFundos",
        )
        pagamentos.forEach { p ->
            sb.linha(
                LocalDate.ofEpochDay(p.dataEpochDay).toString(),
                nomePorId[p.categoriaId] ?: "",
                p.descricao,
                dinheiro(p.valorBaseCent),
                dinheiro(p.baseTributavelCent),
                dinheiro(p.taxaIva),
                booleano(p.ivaPago),
                booleano(p.contaParaObra),
                p.notas,
                p.fornecedor,
                if (p.bancario) "Bancário" else "Pessoal",
            )
        }
        sb.append("\r\n")

        sb.linha(SEC_EVENTOS)
        sb.linha("data", "titulo", "descricao", "fotos")
        eventos.forEach { e ->
            sb.linha(LocalDate.ofEpochDay(e.dataEpochDay).toString(), e.titulo, e.descricao, e.fotos)
        }
        sb.append("\r\n")

        // As recorrentes: as despesas referem-nas pela chave (o id). A coluna
        // ultimoMes só existe para ficheiros da versão 3 e vai vazia.
        sb.linha(SEC_FIXAS)
        sb.linha(
            "chave", "rubrica", "descricao", "valor", "capital", "valorVariavel", "dia", "ultimoMes", "fim", "ativa",
            "unidade", "intervalo", "inicio", "proximo", "lancadas", "maxVezes",
        )
        fixas.forEach { f ->
            sb.linha(
                f.id.toString(),
                f.rubrica,
                f.descricao,
                dinheiro(f.valorCent),
                dinheiro(f.capitalCent),
                booleano(f.valorVariavel),
                f.dia.toString(),
                "",
                f.fimEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "",
                booleano(f.ativa),
                f.unidade,
                f.intervalo.toString(),
                LocalDate.ofEpochDay(f.inicioEpochDay).toString(),
                f.proximo.toString(),
                f.lancadas.toString(),
                f.maxOcorrencias?.toString() ?: "",
            )
        }
        sb.append("\r\n")

        sb.linha(SEC_DESPESAS)
        sb.linha("data", "rubrica", "descricao", "valor", "capital", "notas", "fixa", "aConfirmar")
        despesas.forEach { d ->
            sb.linha(
                LocalDate.ofEpochDay(d.dataEpochDay).toString(),
                d.rubrica,
                d.descricao,
                dinheiro(d.valorCent),
                dinheiro(d.capitalCent),
                d.notas,
                d.fixaId?.toString() ?: "",
                booleano(d.aConfirmar),
            )
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
        var area = 0.0
        val despesas = mutableListOf<DespesaImportada>()
        val fixas = mutableListOf<Pair<String, DespesaFixa>>()
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
                SEC_ORCAMENTO -> {
                    orcamento = numero(campos.getOrNull(0)) ?: 0.0
                    area = numero(campos.getOrNull(1)) ?: 0.0
                }

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
                            valorBaseCent = centimos(valorBase),
                            baseTributavelCent = centimos(
                                if (semIva) valorBase else base.coerceIn(0.0, valorBase),
                            ),
                            taxaIva = if (semIva) TAXA_IVA_NORMAL else taxa,
                            ivaPago = if (semIva) false else booleano(campos.getOrNull(6), predefinido = true),
                            contaParaObra = booleano(campos.getOrNull(7), predefinido = true),
                            notas = campos.getOrNull(8)?.trim().orEmpty(),
                            fornecedor = campos.getOrNull(9)?.trim().orEmpty(),
                            origemFundos = origem(campos.getOrNull(10)),
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
                            fotos = campos.getOrNull(3)?.trim().orEmpty(),
                        )
                    }
                }

                SEC_DESPESAS -> {
                    val dia = data(campos.getOrNull(0))
                        ?: erro("Data de despesa inválida: “${campos.getOrNull(0)}”.")
                    val valor = numero(campos.getOrNull(3))
                        ?: erro("Valor inválido na despesa de ${campos.getOrNull(0)}.")
                    val capital = (numero(campos.getOrNull(4)) ?: 0.0).coerceIn(0.0, valor)
                    despesas += DespesaImportada(
                        fixa = campos.getOrNull(6)?.trim().orEmpty(),
                        despesa = Despesa(
                            rubrica = campos.getOrNull(1)?.trim().orEmpty().ifBlank { "Outras" },
                            descricao = campos.getOrNull(2)?.trim().orEmpty(),
                            dataEpochDay = dia.toEpochDay(),
                            valorCent = centimos(valor),
                            capitalCent = centimos(capital),
                            notas = campos.getOrNull(5)?.trim().orEmpty(),
                            aConfirmar = booleano(campos.getOrNull(7), predefinido = false),
                        ),
                    )
                }

                SEC_FIXAS -> {
                    val chave = campos.getOrNull(0)?.trim().orEmpty()
                    val valor = numero(campos.getOrNull(3))
                        ?: erro("Valor inválido na despesa recorrente “${campos.getOrNull(1)}”.")
                    val dia = campos.getOrNull(6)?.trim()?.toIntOrNull()?.coerceIn(1, 31) ?: 1
                    val inicio = data(campos.getOrNull(12))
                    // Versão 3: só mensais, com o último mês lançado. Passa a
                    // começar nesse mês, com a ocorrência seguinte por lançar.
                    val inicioV3 = if (inicio != null) null else campos.getOrNull(7)?.trim()?.let {
                        try { YearMonth.parse(it).atDay(1) } catch (e: DateTimeParseException) { null }
                    } ?: erro("Despesa recorrente “${campos.getOrNull(1)}” sem data de início.")
                    val unidade = campos.getOrNull(10)?.trim()
                        ?.takeIf { it in listOf(UNIDADE_DIA, UNIDADE_SEMANA, UNIDADE_MES, UNIDADE_ANO) } ?: UNIDADE_MES
                    fixas += chave to DespesaFixa(
                        rubrica = campos.getOrNull(1)?.trim().orEmpty().ifBlank { "Outras" },
                        descricao = campos.getOrNull(2)?.trim().orEmpty(),
                        valorCent = centimos(valor),
                        capitalCent = centimos((numero(campos.getOrNull(4)) ?: 0.0).coerceIn(0.0, valor)),
                        valorVariavel = booleano(campos.getOrNull(5), predefinido = false),
                        dia = dia,
                        unidade = unidade,
                        intervalo = campos.getOrNull(11)?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        inicioEpochDay = (inicio ?: inicioV3!!).toEpochDay(),
                        proximo = campos.getOrNull(13)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 1,
                        lancadas = campos.getOrNull(14)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 1,
                        fimEpochDay = data(campos.getOrNull(8))?.toEpochDay(),
                        maxOcorrencias = campos.getOrNull(15)?.trim()?.toIntOrNull()?.takeIf { it > 0 },
                        ativa = booleano(campos.getOrNull(9), predefinido = true),
                    )
                }
            }
        }

        require(
            categorias.isNotEmpty() || pagamentos.isNotEmpty() || eventos.isNotEmpty() ||
                despesas.isNotEmpty() || fixas.isNotEmpty(),
        ) { "O ficheiro não tem dados para importar." }
        return Conteudo(orcamento, categorias, pagamentos, eventos, despesas, area, fixas)
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

    /** Cêntimos escritos como euros: 123456 -> "1234,56", sem passar por Double. */
    private fun dinheiro(centimos: Long): String {
        val sinal = if (centimos < 0) "-" else ""
        val abs = Math.abs(centimos)
        return "$sinal${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
    }

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

    /** "Bancário", "bancario", "banco", "b" → bancário; tudo o resto (e vazio) → pessoal. */
    private fun origem(texto: String?): String =
        if (texto?.trim()?.lowercase()?.startsWith("b") == true) ORIGEM_BANCARIO else ORIGEM_PESSOAL

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
