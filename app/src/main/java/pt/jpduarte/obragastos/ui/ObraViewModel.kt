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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pt.jpduarte.obragastos.data.Categoria
import pt.jpduarte.obragastos.data.CsvBackup
import pt.jpduarte.obragastos.data.Definicoes
import pt.jpduarte.obragastos.data.Despesa
import pt.jpduarte.obragastos.data.DespesaFixa
import pt.jpduarte.obragastos.data.Regra
import pt.jpduarte.obragastos.data.lancamentosEmFalta
import pt.jpduarte.obragastos.data.ocorrenciaAoRetomar
import pt.jpduarte.obragastos.data.EventoObra
import pt.jpduarte.obragastos.data.Fotos
import pt.jpduarte.obragastos.data.ObraDatabase
import pt.jpduarte.obragastos.data.Pagamento
import pt.jpduarte.obragastos.data.data
import pt.jpduarte.obragastos.data.listaFotos
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class ObraViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = ObraDatabase.get(app).dao()

    val categorias: StateFlow<List<Categoria>> =
        dao.categorias().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pagamentos: StateFlow<List<Pagamento>> =
        dao.pagamentos().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val despesas: StateFlow<List<Despesa>> =
        dao.despesas().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val despesasFixas: StateFlow<List<DespesaFixa>> =
        dao.fixas().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val definicoes: StateFlow<Definicoes?> =
        dao.definicoes().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Só o que é obra: alimenta a figura principal, os gráficos e os orçamentos. */
    val resumo: StateFlow<ResumoObra> = pagamentos
        .map { lista -> resumoDe(lista.filter { it.contaParaObra }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ResumoObra())

    /** Custos da casa que não são obra — terreno, licenças antigas, escrituras. */
    val resumoExtra: StateFlow<ResumoObra> = pagamentos
        .map { lista -> resumoDe(lista.filterNot { it.contaParaObra }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ResumoObra())

    val porCategoria: StateFlow<List<GastoCategoria>> =
        combine(categorias, pagamentos, ::gastosPorCategoria)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Tudo o que os gráficos mostram, recalculado quando muda qualquer dado. */
    val dadosGraficos: StateFlow<DadosGraficos> =
        combine(categorias, pagamentos, dao.eventos(), definicoes) { cats, pags, evs, defs ->
            calcularDadosGraficos(
                categorias = cats,
                pagamentos = pags,
                eventos = evs,
                orcamentoTotal = defs?.orcamentoTotal ?: 0.0,
                areaConstrucao = defs?.areaConstrucao ?: 0.0,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DadosGraficos())

    /* ------------------------- orçamento global ------------------------- */

    /** Orçamento estimado para a obra toda. 0 = ainda não definido. */
    val orcamentoTotal: StateFlow<Double> = definicoes
        .map { it?.orcamentoTotal ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    fun definirOrcamentoTotal(valor: Double) = viewModelScope.launch {
        dao.definirOrcamentoTotal(valor.coerceAtLeast(0.0))
    }

    fun definirAreaConstrucao(area: Double) = viewModelScope.launch {
        dao.definirAreaConstrucao(area.coerceAtLeast(0.0))
    }

    /* ---------------------- organização dos gráficos ---------------------- */

    /**
     * Ordem escolhida para os blocos do ecrã de gráficos. Vazia enquanto o
     * utilizador não mexer em nada — quem decide o que fazer com isso é o ecrã.
     */
    val ordemGraficos: StateFlow<List<String>> = definicoes
        .map { d -> lista(d?.ordemGraficos) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Blocos que o utilizador escondeu no ecrã de escolha. */
    val graficosEscondidos: StateFlow<Set<String>> = definicoes
        .map { d -> lista(d?.graficosEscondidos).toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private fun lista(texto: String?): List<String> =
        texto.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun definirGraficos(ordem: List<String>, escondidos: Set<String>) = viewModelScope.launch {
        dao.definirGraficos(ordem.joinToString(","), escondidos.sorted().joinToString(","))
    }

    /* ------------------------------- tema ------------------------------- */

    /** `sistema`, `claro` ou `escuro`. */
    val tema: StateFlow<String> = definicoes
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
    val ordemLivro: StateFlow<String> = definicoes
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

    /**
     * Grava o evento e só depois apaga os ficheiros das fotos que lhe foram
     * tiradas — se a gravação falhar, as fotos continuam lá.
     */
    fun guardar(evento: EventoObra, fotosRetiradas: Collection<String> = emptyList()) = viewModelScope.launch {
        if (evento.id == 0L) {
            // Entra no fim da lista; a partir daí é o utilizador que manda na ordem.
            dao.inserir(evento.copy(ordem = dao.maiorOrdemEvento() + 1))
        } else {
            dao.atualizar(evento)
        }
        if (fotosRetiradas.isNotEmpty()) apagarFicheirosFotos(fotosRetiradas)
    }

    fun apagar(evento: EventoObra) = viewModelScope.launch {
        dao.apagar(evento)
        apagarFicheirosFotos(evento.listaFotos)
    }

    /** Reduz e guarda uma foto escolhida; devolve o nome, ou null se não deu. */
    suspend fun importarFoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            Fotos.importar(getApplication(), uri)
        } catch (e: Exception) {
            null
        }
    }

    /** Fotos acrescentadas num diálogo que acabou cancelado. */
    fun descartarFotos(nomes: Collection<String>) {
        if (nomes.isNotEmpty()) viewModelScope.launch { apagarFicheirosFotos(nomes) }
    }

    private suspend fun apagarFicheirosFotos(nomes: Collection<String>) = withContext(Dispatchers.IO) {
        Fotos.apagar(getApplication(), nomes)
    }

    suspend fun espacoFotos(): Long = withContext(Dispatchers.IO) { Fotos.espacoOcupado(getApplication()) }

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

    /* ------------------------ pagamentos e despesas ------------------------ */

    fun categoriaDe(id: Long): Categoria? = categorias.value.firstOrNull { it.id == id }
    fun pagamentoDe(id: Long): Pagamento? = pagamentos.value.firstOrNull { it.id == id }
    fun despesaDe(id: Long): Despesa? = despesas.value.firstOrNull { it.id == id }

    fun guardar(pagamento: Pagamento) = viewModelScope.launch {
        if (pagamento.id == 0L) dao.inserir(pagamento) else dao.atualizar(pagamento)
    }


    /**
     * Grava a despesa. Com [repeticao] passa também a ser uma despesa fixa — ela
     * própria é o primeiro mês, e os meses entretanto passados lançam-se logo.
     *
     * Ao gravar uma despesa lançada por uma fixa de valor variável, o valor
     * passa a ser a referência dos meses seguintes ("entra com o valor do mês
     * anterior"). Gravar é também confirmar: deixa de estar "a confirmar".
     */
    fun guardar(despesa: Despesa, repeticao: Regra? = null, valorVariavel: Boolean = false) = viewModelScope.launch {
        val d = despesa.copy(aConfirmar = false)
        if (repeticao != null && d.fixaId == null) {
            dao.guardarComFixa(
                d,
                DespesaFixa(
                    rubrica = d.rubrica,
                    descricao = d.descricao,
                    valorCent = d.valorCent,
                    capitalCent = d.capitalCent,
                    valorVariavel = valorVariavel,
                    unidade = repeticao.unidade,
                    intervalo = repeticao.intervalo,
                    dia = repeticao.dia,
                    // A despesa gravada é a ocorrência 0; a seguinte é a 1.
                    inicioEpochDay = d.dataEpochDay,
                    proximo = 1,
                    lancadas = 1,
                    fimEpochDay = repeticao.fim?.toEpochDay(),
                    maxOcorrencias = repeticao.maxVezes,
                ),
            )
            lancarFixas()
            return@launch
        }
        if (d.id == 0L) dao.inserir(d) else dao.atualizar(d)
        val fixa = d.fixaId?.let { id -> despesasFixas.value.firstOrNull { it.id == id } }
        if (fixa != null && fixa.valorVariavel) {
            dao.atualizar(fixa.copy(valorCent = d.valorCent, capitalCent = d.capitalCent))
        }
    }

    /* ---------------------------- despesas fixas ---------------------------- */

    private val lancamento = Mutex()

    private val _lancadas = MutableStateFlow(0)

    /** Quantas despesas fixas se lançaram da última vez, para o ecrã avisar. */
    val lancadas: StateFlow<Int> = _lancadas

    fun esquecerLancadas() { _lancadas.value = 0 }

    /**
     * Lança as despesas fixas que já venceram. Corre ao abrir a app e ao abrir
     * o separador Despesas; o [Mutex] e o último mês guardado em cada fixa
     * garantem que duas chamadas seguidas não lançam o mesmo mês duas vezes.
     */
    fun lancarDespesasFixas() = viewModelScope.launch { lancarFixas() }

    private suspend fun lancarFixas() = lancamento.withLock {
        val hoje = LocalDate.now()
        var n = 0
        dao.fixasAtivas().forEach { f ->
            val l = lancamentosEmFalta(f, hoje)
            if (l.despesas.isNotEmpty()) {
                dao.lancar(f, l.despesas, l.proximo, l.lancadas)
                n += l.despesas.size
            }
        }
        if (n > 0) _lancadas.value = n
    }

    fun guardar(fixa: DespesaFixa) = viewModelScope.launch {
        dao.atualizar(fixa)
        lancarFixas()
    }

    /** Pausar não lança nada; retomar recomeça no próximo débito, sem recuperar os que passaram em pausa. */
    fun pausar(fixa: DespesaFixa, pausada: Boolean) = viewModelScope.launch {
        if (pausada) {
            dao.atualizar(fixa.copy(ativa = false))
        } else {
            dao.atualizar(fixa.copy(ativa = true, proximo = ocorrenciaAoRetomar(fixa, LocalDate.now())))
            lancarFixas()
        }
    }

    /** Deixa de repetir; as despesas já lançadas ficam. */
    fun apagar(fixa: DespesaFixa) = viewModelScope.launch { dao.apagarFixa(fixa) }

    init {
        lancarDespesasFixas()
    }

    /**
     * O último pagamento ou despesa apagado, para o ecrã oferecer "Desfazer".
     * Apaga-se logo — o desfazer volta a inserir o registo com o mesmo id.
     */
    private val _apagado = MutableStateFlow<Any?>(null)
    val apagado: StateFlow<Any?> = _apagado

    fun apagar(pagamento: Pagamento) = viewModelScope.launch {
        dao.apagar(pagamento)
        _apagado.value = pagamento
    }

    fun apagar(despesa: Despesa) = viewModelScope.launch {
        dao.apagar(despesa)
        _apagado.value = despesa
    }

    fun desfazerApagar() = viewModelScope.launch {
        when (val r = _apagado.value) {
            // Se a categoria do pagamento entretanto desapareceu, a chave
            // estrangeira recusa-o; nesse caso não há nada a repor.
            is Pagamento -> runCatching { dao.inserir(r) }
            is Despesa -> dao.inserir(r)
        }
        _apagado.value = null
    }

    fun esquecerApagado() { _apagado.value = null }

    fun guardar(categoria: Categoria) = viewModelScope.launch {
        if (categoria.id == 0L) dao.inserir(categoria) else dao.atualizar(categoria)
    }

    /**
     * Apagar uma categoria arrasta os pagamentos dela (ON DELETE CASCADE), por
     * isso devolvemos quantos são para o ecrã poder avisar antes de confirmar.
     */
    suspend fun pagamentosEm(categoriaId: Long): Int = dao.contarPagamentosDe(categoriaId)

    /**
     * Apaga a categoria. Com [destino], os pagamentos passam primeiro para essa
     * categoria; sem ele, vão com ela.
     */
    fun apagar(categoria: Categoria, destino: Long? = null) = viewModelScope.launch {
        if (destino == null) dao.apagar(categoria) else dao.apagarMovendoPagamentos(categoria, destino)
    }

    /* --------------------------- exportar / importar --------------------------- */

    /** Resultado de uma exportação ou importação, para o ecrã dar notícias. */
    sealed interface ResultadoFicheiro {
        /** [copia]: a cópia do que havia antes de importar, para se poder desfazer. */
        data class Sucesso(val mensagem: String, val copia: File? = null) : ResultadoFicheiro
        data class Falha(val mensagem: String) : ResultadoFicheiro
    }

    private val _resultadoFicheiro = MutableStateFlow<ResultadoFicheiro?>(null)
    val resultadoFicheiro: StateFlow<ResultadoFicheiro?> = _resultadoFicheiro

    fun limparResultadoFicheiro() { _resultadoFicheiro.value = null }

    /** Nome sugerido para o ficheiro, com a data de hoje. */
    fun nomeFicheiroSugerido(): String = "obragastos-${LocalDate.now()}.csv"

    private fun csvAtual(): String = CsvBackup.exportar(
        orcamentoTotal = orcamentoTotal.value,
        categorias = categorias.value,
        pagamentos = pagamentos.value,
        eventos = eventos.value,
        despesas = despesas.value,
        areaConstrucao = definicoes.value?.areaConstrucao ?: 0.0,
        fixas = despesasFixas.value,
    )

    fun exportarPara(uri: Uri) = viewModelScope.launch {
        _resultadoFicheiro.value = withContext(Dispatchers.IO) {
            try {
                val csv = csvAtual()
                val resolver = getApplication<Application>().contentResolver
                // "wt" trunca: sem isto, gravar por cima de um ficheiro maior
                // deixaria lixo do anterior no fim. O BOM é o que faz o Excel
                // reconhecer o UTF-8 — sem ele os acentos saem trocados.
                resolver.openOutputStream(uri, "wt")?.use { saida ->
                    saida.write("﻿$csv".toByteArray(Charsets.UTF_8))
                } ?: error("Não foi possível escrever no ficheiro.")

                ResultadoFicheiro.Sucesso(
                    "Exportados ${pagamentos.value.size} pagamentos, ${despesas.value.size} despesas " +
                        "e ${eventos.value.size} eventos.",
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
                importarTexto(texto, guardarCopia = true)
            } catch (e: Exception) {
                ResultadoFicheiro.Falha(e.message ?: "Falhou a importação.")
            }
        }
    }

    /** Desfaz uma importação, repondo a cópia feita antes dela. */
    fun reporCopia(copia: File) = viewModelScope.launch {
        _resultadoFicheiro.value = withContext(Dispatchers.IO) {
            try {
                importarTexto(copia.readText(Charsets.UTF_8), guardarCopia = false)
                ResultadoFicheiro.Sucesso("Dados repostos como estavam antes da importação.")
            } catch (e: Exception) {
                ResultadoFicheiro.Falha(e.message ?: "Não foi possível repor a cópia.")
            }
        }
    }

    /**
     * Valida o ficheiro e só depois de o ter lido todo guarda uma cópia do
     * estado atual (em `files/copias/`) e substitui os dados.
     */
    private suspend fun importarTexto(texto: String, guardarCopia: Boolean): ResultadoFicheiro {
        val conteudo = CsvBackup.importar(texto.removePrefix("﻿"))
        val copia = if (guardarCopia) guardarCopiaAntesDeImportar() else null
        dao.restaurar(conteudo)
        return ResultadoFicheiro.Sucesso(
            "Importados ${conteudo.pagamentos.size} pagamentos, ${conteudo.despesas.size} despesas " +
                "e ${conteudo.eventos.size} eventos.",
            copia,
        )
    }

    private fun guardarCopiaAntesDeImportar(): File {
        val pasta = File(getApplication<Application>().filesDir, "copias").apply { mkdirs() }
        val carimbo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val ficheiro = File(pasta, "antes-de-importar-$carimbo.csv")
        ficheiro.writeText(csvAtual(), Charsets.UTF_8)
        // Ficam as cinco mais recentes; são pequenas, mas não há razão para
        // as deixar acumular para sempre.
        pasta.listFiles { f -> f.name.startsWith("antes-de-importar-") }
            ?.sortedByDescending { it.name }
            ?.drop(COPIAS_GUARDADAS)
            ?.forEach { it.delete() }
        return ficheiro
    }

    private companion object {
        const val COPIAS_GUARDADAS = 5
    }
}
