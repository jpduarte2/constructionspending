package pt.jpduarte.obragastos.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.jpduarte.obragastos.data.Categoria
import pt.jpduarte.obragastos.data.CsvBackup
import pt.jpduarte.obragastos.data.Definicoes
import pt.jpduarte.obragastos.data.EventoObra
import pt.jpduarte.obragastos.data.ObraDatabase
import pt.jpduarte.obragastos.data.Pagamento
import pt.jpduarte.obragastos.data.data
import pt.jpduarte.obragastos.data.ivaEfetivo
import pt.jpduarte.obragastos.data.ivaPoupado
import pt.jpduarte.obragastos.data.totalPago
import java.time.LocalDate
import java.time.YearMonth

/** Totais da obra até à data. */
data class ResumoObra(
    val totalPago: Double = 0.0,
    val totalBase: Double = 0.0,
    val ivaPago: Double = 0.0,
    val ivaPoupado: Double = 0.0,
    val nPagamentos: Int = 0,
) {
    /** Quanto custaria a obra se todo o IVA tivesse sido pago. */
    val totalComTodoIva: Double get() = totalBase + ivaPago + ivaPoupado

    /** A poupança em percentagem do que a obra teria custado. */
    val poupancaPct: Double
        get() = if (totalComTodoIva > 0) ivaPoupado / totalComTodoIva * 100.0 else 0.0
}

/**
 * Gasto acumulado numa categoria. [total] conta só o que é obra — é o que se
 * compara com os orçamentos; [totalExtra] são os custos marcados como fora da
 * obra (terreno, licenças antigas), contados à parte. [pagamentos] traz-nos
 * todos, para se poder abrir a categoria e ver de onde vem o valor.
 */
data class GastoCategoria(
    val categoria: Categoria,
    val total: Double,
    val nPagamentos: Int,
    val totalExtra: Double,
    val nExtra: Int,
    val pagamentos: List<Pagamento> = emptyList(),
) {
    /** Tudo o que se gastou nesta rubrica, seja obra ou não. */
    val totalGeral: Double get() = total + totalExtra

    val nTodos: Int get() = nPagamentos + nExtra
}

/** Um pagamento com o nome e a cor da categoria já resolvidos, para listagens. */
data class PagamentoDetalhe(
    val pagamento: Pagamento,
    val categoria: String,
    val corIndex: Int = 0,
)

class ObraViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = ObraDatabase.get(app).dao()

    val categorias: StateFlow<List<Categoria>> =
        dao.categorias().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pagamentos: StateFlow<List<Pagamento>> =
        dao.pagamentos().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Só o que é obra: alimenta a figura principal, os gráficos e os orçamentos. */
    val resumo: StateFlow<ResumoObra> = pagamentos
        .map { lista -> resumoDe(lista.filter { it.contaParaObra }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ResumoObra())

    /** Custos da casa que não são obra — terreno, licenças antigas, escrituras. */
    val resumoExtra: StateFlow<ResumoObra> = pagamentos
        .map { lista -> resumoDe(lista.filterNot { it.contaParaObra }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ResumoObra())

    private fun resumoDe(lista: List<Pagamento>) = ResumoObra(
        totalPago = lista.sumOf { it.totalPago },
        totalBase = lista.sumOf { it.valorBase },
        ivaPago = lista.sumOf { it.ivaEfetivo },
        ivaPoupado = lista.sumOf { it.ivaPoupado },
        nPagamentos = lista.size,
    )

    /**
     * Gasto por especialidade. Ordena pelo total geral (obra + fora da obra),
     * senão uma rubrica que só tenha custos fora da obra ia parar ao fim como
     * se não tivesse nada.
     */
    val porCategoria: StateFlow<List<GastoCategoria>> =
        combine(categorias, pagamentos) { cats, pags ->
            val agrupado = pags.groupBy { it.categoriaId }
            cats.map { c ->
                val doGrupo = agrupado[c.id].orEmpty()
                val (obra, extra) = doGrupo.partition { it.contaParaObra }
                GastoCategoria(
                    categoria = c,
                    total = obra.sumOf { it.totalPago },
                    nPagamentos = obra.size,
                    totalExtra = extra.sumOf { it.totalPago },
                    nExtra = extra.size,
                    pagamentos = doGrupo.sortedByDescending { it.dataEpochDay },
                )
            }.sortedByDescending { it.totalGeral }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Os pagamentos individuais que mais pesaram, com o nome da categoria já
     * resolvido. Conta obra **e** fora da obra: a pergunta aqui é "quais foram
     * as maiores faturas da casa", não "da construção".
     */
    val maioresPagamentos: StateFlow<List<PagamentoDetalhe>> =
        combine(categorias, pagamentos) { cats, pags ->
            val porId = cats.associateBy { it.id }
            pags.sortedByDescending { it.totalPago }
                .take(5)
                .map { p ->
                    val c = porId[p.categoriaId]
                    PagamentoDetalhe(p, c?.nome ?: "Sem categoria", c?.corIndex ?: 0)
                }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Gasto por mês, cronológico e **sem saltos** — os meses sem pagamentos
     * aparecem a zero, para que a evolução não minta sobre o ritmo da obra.
     */
    val porMes: StateFlow<List<Pair<YearMonth, Double>>> = pagamentos
        .map { todos ->
            // Evolução da obra: os custos fora da obra são de outra natureza
            // (e muitas vezes de outra época) e distorceriam o ritmo.
            val lista = todos.filter { it.contaParaObra }
            if (lista.isEmpty()) return@map emptyList()
            val porMes = lista.groupBy { YearMonth.from(it.data) }
                .mapValues { (_, v) -> v.sumOf { it.totalPago } }
            val primeiro = porMes.keys.min()
            val ultimo = porMes.keys.max()
            buildList {
                var m = primeiro
                while (!m.isAfter(ultimo)) {
                    add(m to (porMes[m] ?: 0.0))
                    m = m.plusMonths(1)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /* ------------------------- orçamento global ------------------------- */

    /** Orçamento estimado para a obra toda. 0 = ainda não definido. */
    val orcamentoTotal: StateFlow<Double> = dao.definicoes()
        .map { it?.orcamentoTotal ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    fun definirOrcamentoTotal(valor: Double) = viewModelScope.launch {
        dao.definirOrcamentoTotal(valor.coerceAtLeast(0.0))
    }

    /* ---------------------- organização dos gráficos ---------------------- */

    /**
     * Ordem escolhida para os blocos do ecrã de gráficos. Vazia enquanto o
     * utilizador não mexer em nada — quem decide o que fazer com isso é o ecrã.
     */
    val ordemGraficos: StateFlow<List<String>> = dao.definicoes()
        .map { d ->
            d?.ordemGraficos.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun definirOrdemGraficos(ordem: List<String>) = viewModelScope.launch {
        dao.definirOrdemGraficos(ordem.joinToString(","))
    }

    /* ------------------------------- tema ------------------------------- */

    /** `sistema`, `claro` ou `escuro`. */
    val tema: StateFlow<String> = dao.definicoes()
        .map { it?.tema ?: Definicoes.TEMA_SISTEMA }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Definicoes.TEMA_SISTEMA)

    /**
     * Liga/desliga o modo escuro. [escuroAgora] é o que está a ser mostrado neste
     * momento — enquanto o tema for `sistema` não sabemos, só o ecrã sabe, e é
     * por isso que a decisão vem de fora.
     */
    fun alternarTema(escuroAgora: Boolean) = viewModelScope.launch {
        dao.definirTema(
            if (escuroAgora) Definicoes.TEMA_CLARO else Definicoes.TEMA_ESCURO,
        )
    }

    fun seguirTemaDoSistema() = viewModelScope.launch {
        dao.definirTema(Definicoes.TEMA_SISTEMA)
    }

    /* --------------------------- livro de obra --------------------------- */

    /**
     * Como o livro está a ser apresentado: `recente`, `antigo` ou `manual`.
     */
    val ordemLivro: StateFlow<String> = dao.definicoes()
        .map { it?.ordemLivro ?: Definicoes.LIVRO_RECENTE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Definicoes.LIVRO_RECENTE)

    /**
     * Os eventos já pela ordem que o utilizador escolheu.
     *
     * A ordenação por data é feita aqui e não na consulta porque o modo é uma
     * definição que pode mudar a qualquer momento — assim alternar entre
     * "mais recente" e "mais antigo" não reescreve nada na base de dados e a
     * arrumação manual que lá esteja guardada não se perde.
     */
    val eventos: StateFlow<List<EventoObra>> =
        dao.eventos().combine(ordemLivro) { lista, modo ->
            when (modo) {
                // Desempate pelo id ao contrário: dois eventos no mesmo dia
                // aparecem com o mais recentemente registado primeiro.
                Definicoes.LIVRO_RECENTE ->
                    lista.sortedWith(compareByDescending<EventoObra> { it.dataEpochDay }.thenByDescending { it.id })
                Definicoes.LIVRO_ANTIGO ->
                    lista.sortedWith(compareBy<EventoObra> { it.dataEpochDay }.thenBy { it.id })
                // Manual: a consulta já vem ordenada por `ordem`.
                else -> lista
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun eventoDe(id: Long): EventoObra? = eventos.value.firstOrNull { it.id == id }

    fun guardar(evento: EventoObra) = viewModelScope.launch {
        if (evento.id == 0L) {
            // Entra no fim da lista; a partir daí é o utilizador que manda na ordem.
            dao.inserir(evento.copy(ordem = dao.maiorOrdemEvento() + 1))
        } else {
            dao.atualizar(evento)
        }
    }

    fun apagar(evento: EventoObra) = viewModelScope.launch { dao.apagar(evento) }

    /** Muda a forma de apresentar o livro. */
    fun definirOrdemLivro(modo: String) = viewModelScope.launch {
        if (modo == Definicoes.LIVRO_MANUAL) {
            // Passar a manual congela o que está no ecrã neste momento, senão o
            // livro saltava para uma arrumação antiga que já ninguém reconhece.
            dao.fixarOrdemManualDoLivro(eventos.value.mapIndexed { pos, e -> e.copy(ordem = pos) })
        } else {
            dao.definirOrdemLivro(modo)
        }
    }

    /**
     * Sobe ou desce um evento. Reescrevemos as ordens todas em vez de trocar os
     * campos `ordem` dos dois, para o caso de virem com valores repetidos, e
     * passamos o livro a manual: mexer à mão é dizer que já não se quer a ordem
     * automática.
     */
    fun mover(evento: EventoObra, delta: Int) = viewModelScope.launch {
        val lista = eventos.value
        val i = lista.indexOfFirst { it.id == evento.id }
        val j = i + delta
        if (i < 0 || j !in lista.indices) return@launch
        val trocada = lista.toMutableList().apply { add(j, removeAt(i)) }
        dao.fixarOrdemManualDoLivro(trocada.mapIndexed { pos, e -> e.copy(ordem = pos) })
    }

    fun categoriaDe(id: Long): Categoria? = categorias.value.firstOrNull { it.id == id }
    fun pagamentoDe(id: Long): Pagamento? = pagamentos.value.firstOrNull { it.id == id }

    fun guardar(pagamento: Pagamento) = viewModelScope.launch {
        if (pagamento.id == 0L) dao.inserir(pagamento) else dao.atualizar(pagamento)
    }

    fun apagar(pagamento: Pagamento) = viewModelScope.launch { dao.apagar(pagamento) }

    fun guardar(categoria: Categoria) = viewModelScope.launch {
        if (categoria.id == 0L) dao.inserir(categoria) else dao.atualizar(categoria)
    }

    /**
     * Apagar uma categoria arrasta os pagamentos dela (ON DELETE CASCADE), por
     * isso devolvemos quantos são para o ecrã poder avisar antes de confirmar.
     */
    suspend fun pagamentosEm(categoriaId: Long): Int = dao.contarPagamentosDe(categoriaId)

    fun apagar(categoria: Categoria) = viewModelScope.launch { dao.apagar(categoria) }

    /* --------------------------- exportar / importar --------------------------- */

    /** Resultado de uma exportação ou importação, para o ecrã dar notícias. */
    sealed interface ResultadoFicheiro {
        data class Sucesso(val mensagem: String) : ResultadoFicheiro
        data class Falha(val mensagem: String) : ResultadoFicheiro
    }

    private val _resultadoFicheiro = MutableStateFlow<ResultadoFicheiro?>(null)
    val resultadoFicheiro: StateFlow<ResultadoFicheiro?> = _resultadoFicheiro

    fun limparResultadoFicheiro() { _resultadoFicheiro.value = null }

    /** Nome sugerido para o ficheiro, com a data de hoje. */
    fun nomeFicheiroSugerido(): String = "obragastos-${LocalDate.now()}.csv"

    fun exportarPara(uri: Uri) = viewModelScope.launch {
        _resultadoFicheiro.value = withContext(Dispatchers.IO) {
            try {
                val csv = CsvBackup.exportar(
                    orcamentoTotal = orcamentoTotal.value,
                    categorias = categorias.value,
                    pagamentos = pagamentos.value,
                    eventos = eventos.value,
                )
                val resolver = getApplication<Application>().contentResolver
                // "wt" trunca: sem isto, gravar por cima de um ficheiro maior
                // deixaria lixo do anterior no fim. O BOM é o que faz o Excel
                // reconhecer o UTF-8 — sem ele os acentos saem trocados.
                resolver.openOutputStream(uri, "wt")?.use { saida ->
                    saida.write("\uFEFF$csv".toByteArray(Charsets.UTF_8))
                } ?: error("Não foi possível escrever no ficheiro.")

                ResultadoFicheiro.Sucesso(
                    "Exportados ${pagamentos.value.size} pagamentos e ${eventos.value.size} eventos.",
                )
            } catch (e: Exception) {
                ResultadoFicheiro.Falha(e.message ?: "Falhou a exportação.")
            }
        }
    }

    fun importarDe(uri: Uri) = viewModelScope.launch {
        _resultadoFicheiro.value = withContext(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val texto = resolver.openInputStream(uri)?.use { entrada ->
                    entrada.readBytes().toString(Charsets.UTF_8)
                } ?: error("Não foi possível ler o ficheiro.")

                val conteudo = CsvBackup.importar(texto)
                dao.restaurar(conteudo)

                ResultadoFicheiro.Sucesso(
                    "Importados ${conteudo.pagamentos.size} pagamentos e ${conteudo.eventos.size} eventos.",
                )
            } catch (e: Exception) {
                ResultadoFicheiro.Falha(e.message ?: "Falhou a importação.")
            }
        }
    }
}
