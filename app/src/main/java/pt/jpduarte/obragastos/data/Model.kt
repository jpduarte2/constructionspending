package pt.jpduarte.obragastos.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import pt.jpduarte.obragastos.ui.theme.N_CORES_CATEGORIA
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate

/**
 * Uma especialidade / rubrica de obra (pedreiro, eletricista, materiais, ...).
 *
 * [orcamento] a zero significa "sem orçamento definido" — nesse caso a categoria
 * não aparece na secção de orçamentos dos gráficos. Os orçamentos ficam em
 * euros (`Double`): são estimativas, não se somam fatura a fatura.
 */
@Entity(tableName = "categorias")
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    /** Slot da paleta categórica validada (0 até N_CORES_CATEGORIA-1, ver VizPalette.series). */
    val corIndex: Int = 0,
    val orcamento: Double = 0.0,
)

/**
 * Um pagamento efetuado.
 *
 * O valor é sempre guardado **sem IVA** em [valorBaseCent]; [taxaIva] é a
 * percentagem aplicável e [ivaPago] diz se o IVA saiu efetivamente da conta.
 * Quando o IVA não foi pago, o montante correspondente conta como poupança —
 * ver [ivaPoupado].
 *
 * O IVA pode incidir só sobre **parte** do pagamento: é o que [baseTributavelCent]
 * regista. Numa fatura de 6000 € em que só 1500 € foram tributados a 23%, fica
 * `valorBase = 6000`, `baseTributavel = 1500`, e o IVA são 345 € — não 1380 €.
 * Quando o IVA incide sobre tudo, a base tributável é igual ao valor.
 *
 * Os montantes guardam-se em **cêntimos** (desde a versão 11): com `Double` as
 * somas de centenas de faturas acabavam a fugir um cêntimo ao extrato. O IVA de
 * cada fatura é arredondado ao cêntimo, como na própria fatura, e só depois somado.
 */
@Entity(
    tableName = "pagamentos",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("categoriaId"), Index("dataEpochDay")],
)
data class Pagamento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoriaId: Long,
    val descricao: String,
    val dataEpochDay: Long,
    /** Valor total do pagamento, sem IVA, em cêntimos. */
    val valorBaseCent: Long,
    /** Parte de [valorBaseCent] sobre a qual o IVA incide, em cêntimos. */
    val baseTributavelCent: Long,
    /** Taxa de IVA em percentagem (23.0, 13.0, 6.0, ou outra). */
    val taxaIva: Double,
    /** true = o IVA foi efetivamente pago; false = poupou-se o IVA. */
    val ivaPago: Boolean,
    /**
     * false para custos que são da casa mas não da obra — o terreno, licenças
     * antigas, escrituras. Ficam fora do "total gasto na obra" e fora do
     * orçamento, e aparecem somados à parte.
     */
    val contaParaObra: Boolean = true,
    val notas: String = "",
    /** A quem se pagou. Opcional; alimenta o gráfico dos fornecedores. */
    @ColumnInfo(defaultValue = "")
    val fornecedor: String = "",
    /**
     * De onde veio o dinheiro: [ORIGEM_PESSOAL] (poupanças, conta própria) ou
     * [ORIGEM_BANCARIO] (crédito do banco). Alimenta o gráfico da origem dos fundos.
     */
    @ColumnInfo(defaultValue = ORIGEM_PESSOAL)
    val origemFundos: String = ORIGEM_PESSOAL,
)

const val ORIGEM_PESSOAL = "pessoal"
const val ORIGEM_BANCARIO = "bancario"

val Pagamento.bancario: Boolean get() = origemFundos == ORIGEM_BANCARIO

/**
 * Taxa normal de IVA. Assume-se que tudo seria tributado a esta taxa, por isso
 * um pagamento "sem IVA" é registado com ela e com o imposto por pagar — a
 * diferença conta como poupança.
 */
const val TAXA_IVA_NORMAL = 23.0

/** Euros para cêntimos, arredondando ao cêntimo mais próximo. */
fun centimos(euros: Double): Long = Math.round(euros * 100.0)

/** Cêntimos para euros, para mostrar e para os gráficos. */
fun Long.emEuros(): Double = this / 100.0

/** Uma percentagem de um montante em cêntimos, arredondada ao cêntimo. */
private fun Long.percentagem(taxa: Double): Long = Math.round(this * taxa / 100.0)

/** IVA que a taxa implica sobre a base tributável, tenha sido pago ou não. */
val Pagamento.ivaCent: Long get() = baseTributavelCent.percentagem(taxaIva)

/**
 * O IVA que o pagamento teria se a taxa incidisse sobre a totalidade — a
 * referência contra a qual se mede a poupança.
 */
val Pagamento.ivaSeTudoTributadoCent: Long get() = valorBaseCent.percentagem(taxaIva)

val Pagamento.ivaEfetivoCent: Long get() = if (ivaPago) ivaCent else 0L

/** O que efetivamente saiu da conta. */
val Pagamento.totalPagoCent: Long get() = valorBaseCent + ivaEfetivoCent

/**
 * IVA que se evitou pagar — inclui as duas formas de o evitar: não pagar o IVA
 * devido, e o IVA não ter incidido sobre a totalidade. Numa fatura de 6000 € a
 * 23% em que só 1500 € foram tributados, pagaram-se 345 € e pouparam-se os
 * 1035 € que os restantes 4500 € teriam custado de imposto.
 */
val Pagamento.ivaPoupadoCent: Long
    get() = (ivaSeTudoTributadoCent - ivaEfetivoCent).coerceAtLeast(0L)

/** true quando o IVA incidiu só sobre parte do pagamento. */
val Pagamento.ivaParcial: Boolean get() = taxaIva > 0.0 && baseTributavelCent < valorBaseCent

// Os mesmos valores em euros, para o ecrã. As somas fazem-se sempre em
// cêntimos (ver ResumoObra) e só o resultado passa a euros.
val Pagamento.valorBase: Double get() = valorBaseCent.emEuros()
val Pagamento.baseTributavel: Double get() = baseTributavelCent.emEuros()
val Pagamento.valorIva: Double get() = ivaCent.emEuros()
val Pagamento.totalPago: Double get() = totalPagoCent.emEuros()
val Pagamento.ivaEfetivo: Double get() = ivaEfetivoCent.emEuros()
val Pagamento.ivaPoupado: Double get() = ivaPoupadoCent.emEuros()

val Pagamento.data: LocalDate get() = LocalDate.ofEpochDay(dataEpochDay)

/**
 * Definições da obra. Tabela de uma só linha ([ID] fixo) — mais simples de ler
 * reativamente com o resto dos dados do que um armazenamento à parte.
 */
@Entity(tableName = "definicoes")
data class Definicoes(
    @PrimaryKey val id: Int = ID,
    /** Orçamento estimado para a obra toda. 0 = ainda não definido. */
    val orcamentoTotal: Double = 0.0,
    /**
     * Ordem em que os blocos do ecrã de gráficos aparecem, por identificador e
     * separada por vírgulas. Vazio = ordem de fábrica. Guarda-se a lista toda,
     * incluindo blocos que agora estão escondidos, para a organização se manter
     * quando voltarem a ter dados.
     */
    val ordemGraficos: String = "",
    /**
     * Tema escolhido: `sistema`, `claro` ou `escuro`. Por omissão segue o
     * telemóvel — só passa a ser explícito quando o utilizador mexe.
     */
    val tema: String = TEMA_SISTEMA,
    /**
     * Como o livro de obra é apresentado: `recente`, `antigo` ou `manual`.
     * Por omissão mostra o mais recente em cima, que é o que interessa ver ao
     * abrir; a ordem manual só entra quando o utilizador arruma os eventos à
     * mão, e aí passa a mandar o campo [EventoObra.ordem].
     */
    val ordemLivro: String = LIVRO_RECENTE,
    /**
     * Blocos do ecrã de gráficos que o utilizador escondeu, por identificador e
     * separados por vírgulas. Fica à parte da ordem: esconder um bloco não o
     * tira do sítio onde estava.
     */
    @ColumnInfo(defaultValue = "")
    val graficosEscondidos: String = "",
    /** Área de construção em m², para o custo por metro quadrado. 0 = não definida. */
    @ColumnInfo(defaultValue = "0")
    val areaConstrucao: Double = 0.0,
) {
    companion object {
        const val ID = 1
        const val TEMA_SISTEMA = "sistema"
        const val TEMA_CLARO = "claro"
        const val TEMA_ESCURO = "escuro"
        const val LIVRO_RECENTE = "recente"
        const val LIVRO_ANTIGO = "antigo"
        const val LIVRO_MANUAL = "manual"
    }
}

/**
 * Uma entrada do livro de obra: uma data e o que aconteceu.
 *
 * [ordem] permite reordenar à mão, porque a ordem cronológica não é sempre a que
 * se quer ler (um evento pode ser registado com data estimada, ou dois no mesmo
 * dia têm uma sequência lógica que a data não capta).
 */
@Entity(tableName = "eventos")
data class EventoObra(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dataEpochDay: Long,
    val titulo: String,
    val descricao: String = "",
    val ordem: Int = 0,
    /**
     * Nomes dos ficheiros das fotos, separados por `|`, pela ordem em que se
     * mostram. Os ficheiros vivem em `files/fotos/` (ver `Fotos`).
     */
    @ColumnInfo(defaultValue = "")
    val fotos: String = "",
)

val EventoObra.data: LocalDate get() = LocalDate.ofEpochDay(dataEpochDay)

val EventoObra.listaFotos: List<String>
    get() = fotos.split('|').filter { it.isNotBlank() }

fun juntarFotos(nomes: List<String>): String = nomes.joinToString("|")

/**
 * Uma despesa da casa que não é construção: prestações e comissões do banco,
 * seguros, água, luz, impostos. Vive à parte dos pagamentos — não tem IVA
 * poupado nem entra no custo da obra.
 *
 * Numa prestação do crédito, [capitalCent] é a parte que amortiza capital. Essa
 * parte não é custo novo — é devolver o dinheiro que já pagou a obra — e por
 * isso fica de fora do [custoRealCent].
 */
@Entity(tableName = "despesas", indices = [Index("dataEpochDay")])
data class Despesa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Rubrica livre (ver [RUBRICAS_DESPESA] para as sugeridas). */
    val rubrica: String,
    val descricao: String = "",
    val dataEpochDay: Long,
    /** O que saiu da conta, em cêntimos. */
    val valorCent: Long,
    /** Parte de [valorCent] que amortizou capital do crédito, em cêntimos. */
    val capitalCent: Long = 0,
    val notas: String = "",
    /**
     * A despesa fixa que a lançou, se foi lançada por uma. Sem chave
     * estrangeira de propósito: deixar de repetir uma fixa não pode apagar as
     * despesas que ela já criou (ver [ObraDao.apagarFixa]).
     */
    val fixaId: Long? = null,
    /**
     * Lançada por uma fixa de valor variável, com o valor do mês anterior: é
     * uma estimativa até alguém a abrir e confirmar o valor verdadeiro.
     */
    @ColumnInfo(defaultValue = "0")
    val aConfirmar: Boolean = false,
)

/** O dinheiro que se perdeu de facto: juros, comissões, contas. */
val Despesa.custoRealCent: Long get() = (valorCent - capitalCent).coerceAtLeast(0L)

val Despesa.data: LocalDate get() = LocalDate.ofEpochDay(dataEpochDay)

/**
 * Uma despesa recorrente — a prestação, a comissão de manutenção, a internet,
 * o seguro anual. A app lança sozinha as que já venceram (ver `Recorrencia.kt`).
 * No ecrã chama-se "recorrente"; o nome da classe e da tabela ficou de quando
 * só havia repetição mensal.
 *
 * Repete-se de [intervalo] em [intervalo] [unidade]s a partir de
 * [inicioEpochDay]. [proximo] é o índice da próxima ocorrência por lançar e
 * [lancadas] quantas já se lançaram: é o que torna o lançamento seguro de
 * repetir — nunca lança a mesma ocorrência duas vezes.
 */
@Entity(tableName = "despesas_fixas")
data class DespesaFixa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rubrica: String,
    val descricao: String = "",
    /** Valor de cada vez; numa recorrente de valor variável, o último valor conhecido. */
    val valorCent: Long,
    val capitalCent: Long = 0,
    /** true = o valor muda de vez para vez; as despesas entram "a confirmar". */
    val valorVariavel: Boolean = false,
    /** `dia`, `semana`, `mes` ou `ano` (ver UNIDADE_*). */
    val unidade: String = UNIDADE_MES,
    val intervalo: Int = 1,
    /** Nas repetições mensais e anuais, o dia do mês (1–31; nos meses mais curtos, o último). */
    val dia: Int,
    /** A data da primeira ocorrência (a despesa que a criou). */
    val inicioEpochDay: Long,
    val proximo: Int = 1,
    val lancadas: Int = 1,
    /** Último dia em que ainda se lança (o fim do crédito, por exemplo). null = sem fim. */
    val fimEpochDay: Long? = null,
    /** Número total de vezes, contando a primeira. null = sem limite. */
    val maxOcorrencias: Int? = null,
    /** false = em pausa: não lança nada até ser retomada. */
    val ativa: Boolean = true,
)

const val RUBRICA_PRESTACAO = "Prestação do crédito"

/**
 * Rubricas sugeridas no formulário; a ordem dá a cor de cada uma, por isso as
 * novas acrescentam-se antes de "Outras" só enquanto ninguém tiver despesas
 * gravadas com as seguintes — depois disso, sempre no fim. As que o utilizador
 * cria ("Nova…") vivem só nas despesas que as usam e aparecem nos chips a seguir.
 */
val RUBRICAS_DESPESA = listOf(
    RUBRICA_PRESTACAO,
    "Comissões bancárias",
    "Seguros",
    "Água",
    "Luz",
    "Gás",
    "Internet e telefone",
    "Impostos e taxas",
    "Outras",
)

@Dao
interface ObraDao {

    @Query("SELECT * FROM categorias ORDER BY nome COLLATE NOCASE ASC")
    fun categorias(): Flow<List<Categoria>>

    @Query("SELECT * FROM pagamentos ORDER BY dataEpochDay DESC, id DESC")
    fun pagamentos(): Flow<List<Pagamento>>

    @Insert
    suspend fun inserir(categoria: Categoria): Long

    @Update
    suspend fun atualizar(categoria: Categoria)

    @Delete
    suspend fun apagar(categoria: Categoria)

    @Insert
    suspend fun inserir(pagamento: Pagamento): Long

    @Update
    suspend fun atualizar(pagamento: Pagamento)

    @Delete
    suspend fun apagar(pagamento: Pagamento)

    @Query("SELECT COUNT(*) FROM pagamentos WHERE categoriaId = :categoriaId")
    suspend fun contarPagamentosDe(categoriaId: Long): Int

    @Query("UPDATE pagamentos SET categoriaId = :destino WHERE categoriaId = :origem")
    suspend fun moverPagamentos(origem: Long, destino: Long)

    /**
     * Apaga a categoria passando antes os pagamentos dela para [destino] — sem
     * isto o ON DELETE CASCADE levava-os consigo.
     */
    @Transaction
    suspend fun apagarMovendoPagamentos(categoria: Categoria, destino: Long) {
        moverPagamentos(categoria.id, destino)
        apagar(categoria)
    }

    /* ------------------------------ despesas ------------------------------ */

    @Query("SELECT * FROM despesas ORDER BY dataEpochDay DESC, id DESC")
    fun despesas(): Flow<List<Despesa>>

    @Insert
    suspend fun inserir(despesa: Despesa): Long

    @Update
    suspend fun atualizar(despesa: Despesa)

    @Delete
    suspend fun apagar(despesa: Despesa)

    /* --------------------------- despesas fixas --------------------------- */

    @Query("SELECT * FROM despesas_fixas ORDER BY ativa DESC, rubrica ASC, descricao ASC")
    fun fixas(): Flow<List<DespesaFixa>>

    @Query("SELECT * FROM despesas_fixas WHERE ativa = 1")
    suspend fun fixasAtivas(): List<DespesaFixa>

    @Insert
    suspend fun inserir(fixa: DespesaFixa): Long

    @Update
    suspend fun atualizar(fixa: DespesaFixa)

    @Delete
    suspend fun apagar(fixa: DespesaFixa)

    @Query("UPDATE despesas SET fixaId = NULL WHERE fixaId = :fixaId")
    suspend fun desligarDaFixa(fixaId: Long)

    /** Deixa de repetir: as despesas já lançadas ficam, só perdem a ligação. */
    @Transaction
    suspend fun apagarFixa(fixa: DespesaFixa) {
        desligarDaFixa(fixa.id)
        apagar(fixa)
    }

    /** Lança as despesas em falta de uma recorrente e avança a sua contagem, tudo ou nada. */
    @Transaction
    suspend fun lancar(fixa: DespesaFixa, novas: List<Despesa>, proximo: Int, lancadas: Int) {
        novas.forEach { inserir(it) }
        atualizar(fixa.copy(proximo = proximo, lancadas = lancadas))
    }

    /** Cria a fixa a partir de uma despesa e liga-a a ela (a despesa é o 1.º mês). */
    @Transaction
    suspend fun guardarComFixa(despesa: Despesa, fixa: DespesaFixa) {
        val fixaId = inserir(fixa)
        val ligada = despesa.copy(fixaId = fixaId)
        if (despesa.id == 0L) inserir(ligada) else atualizar(ligada)
    }

    /* ------------------------------ definições ------------------------------ */

    @Query("SELECT * FROM definicoes WHERE id = ${Definicoes.ID}")
    fun definicoes(): Flow<Definicoes?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(definicoes: Definicoes)

    @Query(SQL_CRIAR_DEFINICOES)
    suspend fun criarDefinicoesSeFaltarem()

    @Query("UPDATE definicoes SET orcamentoTotal = :valor WHERE id = ${Definicoes.ID}")
    suspend fun gravarOrcamentoTotal(valor: Double)

    @Query("UPDATE definicoes SET ordemGraficos = :ordem WHERE id = ${Definicoes.ID}")
    suspend fun gravarOrdemGraficos(ordem: String)

    @Query("UPDATE definicoes SET tema = :tema WHERE id = ${Definicoes.ID}")
    suspend fun gravarTema(tema: String)

    @Query("UPDATE definicoes SET ordemLivro = :modo WHERE id = ${Definicoes.ID}")
    suspend fun gravarOrdemLivro(modo: String)

    @Query("UPDATE definicoes SET graficosEscondidos = :ids WHERE id = ${Definicoes.ID}")
    suspend fun gravarGraficosEscondidos(ids: String)

    @Query("UPDATE definicoes SET areaConstrucao = :area WHERE id = ${Definicoes.ID}")
    suspend fun gravarAreaConstrucao(area: Double)

    // Cada definição grava-se isoladamente: um REPLACE da linha inteira faria
    // com que gravar o orçamento apagasse a ordem dos gráficos, e vice-versa.
    @Transaction
    suspend fun definirOrcamentoTotal(valor: Double) {
        criarDefinicoesSeFaltarem()
        gravarOrcamentoTotal(valor)
    }

    @Transaction
    suspend fun definirOrdemGraficos(ordem: String) {
        criarDefinicoesSeFaltarem()
        gravarOrdemGraficos(ordem)
    }

    /** Ordem e visibilidade gravam-se juntas: vêm do mesmo ecrã de escolha. */
    @Transaction
    suspend fun definirGraficos(ordem: String, escondidos: String) {
        criarDefinicoesSeFaltarem()
        gravarOrdemGraficos(ordem)
        gravarGraficosEscondidos(escondidos)
    }

    @Transaction
    suspend fun definirTema(tema: String) {
        criarDefinicoesSeFaltarem()
        gravarTema(tema)
    }

    @Transaction
    suspend fun definirOrdemLivro(modo: String) {
        criarDefinicoesSeFaltarem()
        gravarOrdemLivro(modo)
    }

    @Transaction
    suspend fun definirAreaConstrucao(area: Double) {
        criarDefinicoesSeFaltarem()
        gravarAreaConstrucao(area)
    }

    /**
     * Fixa a ordem manual do livro: grava as posições dadas e passa o livro para
     * o modo manual. Vai tudo numa transação porque gravar as posições sem mudar
     * o modo (ou o contrário) deixaria o livro a mostrar outra coisa.
     */
    @Transaction
    suspend fun fixarOrdemManualDoLivro(eventos: List<EventoObra>) {
        atualizar(eventos)
        definirOrdemLivro(Definicoes.LIVRO_MANUAL)
    }

    /* -------------------------- livro de obra -------------------------- */

    @Query("SELECT * FROM eventos ORDER BY ordem ASC, dataEpochDay ASC, id ASC")
    fun eventos(): Flow<List<EventoObra>>

    @Insert
    suspend fun inserir(evento: EventoObra): Long

    @Update
    suspend fun atualizar(evento: EventoObra)

    @Update
    suspend fun atualizar(eventos: List<EventoObra>)

    @Delete
    suspend fun apagar(evento: EventoObra)

    @Query("SELECT COALESCE(MAX(ordem), -1) FROM eventos")
    suspend fun maiorOrdemEvento(): Int

    /* ---------------------------- importação ---------------------------- */

    @Query("DELETE FROM pagamentos")
    suspend fun apagarPagamentos()

    @Query("DELETE FROM categorias")
    suspend fun apagarCategorias()

    @Query("DELETE FROM eventos")
    suspend fun apagarEventos()

    @Query("DELETE FROM despesas")
    suspend fun apagarDespesas()

    @Query("DELETE FROM despesas_fixas")
    suspend fun apagarFixas()

    /**
     * Substitui tudo pelo conteúdo de um backup, numa só transação — ou entra
     * o ficheiro inteiro, ou não se perde nada do que já lá estava.
     *
     * Os pagamentos vêm com a categoria por nome; os ids são atribuídos aqui, à
     * medida que as categorias são criadas. Um pagamento cuja categoria não
     * exista no ficheiro passa a pertencer a uma categoria criada na hora com
     * esse nome, para nenhum pagamento se perder pelo caminho.
     */
    @Transaction
    suspend fun restaurar(conteudo: CsvBackup.Conteudo) {
        apagarPagamentos()
        apagarCategorias()
        apagarEventos()
        apagarDespesas()
        apagarFixas()

        val idPorNome = mutableMapOf<String, Long>()
        conteudo.categorias.forEach { c ->
            idPorNome[c.nome.lowercase()] = inserir(c.copy(id = 0))
        }

        conteudo.pagamentos.forEach { (nome, pagamento) ->
            val chave = nome.lowercase()
            val categoriaId = idPorNome[chave] ?: inserir(
                Categoria(
                    nome = nome.ifBlank { "Sem categoria" },
                    corIndex = idPorNome.size % N_CORES_CATEGORIA,
                ),
            ).also { idPorNome[chave] = it }
            inserir(pagamento.copy(id = 0, categoriaId = categoriaId))
        }

        conteudo.eventos.forEachIndexed { i, e ->
            inserir(e.copy(id = 0, ordem = i))
        }

        // As fixas primeiro: as despesas referem-nas pela chave do ficheiro.
        val fixaPorChave = conteudo.fixas.associate { (chave, f) -> chave to inserir(f.copy(id = 0)) }
        conteudo.despesas.forEach { (chave, d) ->
            inserir(d.copy(id = 0, fixaId = fixaPorChave[chave]))
        }

        // Só os dados da obra: a ordem e a escolha dos gráficos são preferências
        // do ecrã, não fazem parte do backup, e uma importação não as apaga.
        definirOrcamentoTotal(conteudo.orcamentoTotal)
        definirAreaConstrucao(conteudo.areaConstrucao)
    }
}

/** Versão atual da base de dados — subir sempre com uma migração nova. */
const val VERSAO_BD = 14

/**
 * Cria a linha única das definições, com todas as colunas. Partilhado entre o
 * DAO e a criação da base de dados: a semente antiga esquecia-se de uma coluna
 * e o `INSERT OR IGNORE` engolia o erro, deixando a instalação nova sem linha.
 */
const val SQL_CRIAR_DEFINICOES =
    "INSERT OR IGNORE INTO definicoes " +
        "(id, orcamentoTotal, ordemGraficos, tema, ordemLivro, graficosEscondidos, areaConstrucao) " +
        "VALUES (${Definicoes.ID}, 0.0, '', '${Definicoes.TEMA_SISTEMA}', " +
        "'${Definicoes.LIVRO_RECENTE}', '', 0.0)"

@Database(
    entities = [
        Categoria::class, Pagamento::class, Definicoes::class, EventoObra::class, Despesa::class,
        DespesaFixa::class,
    ],
    version = VERSAO_BD,
    exportSchema = true,
)
abstract class ObraDatabase : RoomDatabase() {

    abstract fun dao(): ObraDao

    companion object {
        const val NOME = "obragastos.db"

        @Volatile
        private var instancia: ObraDatabase? = null

        fun get(context: Context): ObraDatabase =
            instancia ?: synchronized(this) {
                instancia ?: construir(context.applicationContext).also { instancia = it }
            }

        /** Só para os testes: obriga o próximo [get] a abrir a base de dados de novo. */
        internal fun esquecerInstancia() {
            synchronized(this) {
                instancia?.close()
                instancia = null
            }
        }

        private fun construir(context: Context): ObraDatabase {
            copiarAntesDeMigrar(context)
            return Room.databaseBuilder(context, ObraDatabase::class.java, NOME)
                .addCallback(SeedCallback)
                .addMigrations(*MIGRACOES)
                .build()
        }

        /**
         * Antes de uma migração, guarda uma cópia do ficheiro tal como está em
         * `files/copias/antes-da-v<N>/`. Se alguma migração correr mal no
         * telemóvel, os dados antigos continuam lá, intactos.
         *
         * A versão lê-se diretamente do cabeçalho do SQLite (user_version, no
         * byte 60) sem abrir a base de dados. Se o valor mais recente ainda
         * estiver só no WAL, o cabeçalho mostra uma versão mais antiga e faz-se
         * uma cópia escusada — nunca o contrário.
         */
        internal fun copiarAntesDeMigrar(context: Context) {
            val ficheiro = context.getDatabasePath(NOME)
            if (!ficheiro.exists() || ficheiro.length() < 100) return
            val versao = try {
                RandomAccessFile(ficheiro, "r").use { it.seek(60); it.readInt() }
            } catch (e: Exception) {
                0
            }
            if (versao >= VERSAO_BD) return
            val destino = File(context.filesDir, "copias/antes-da-v$VERSAO_BD")
            if (File(destino, NOME).exists()) return
            destino.mkdirs()
            listOf("", "-wal", "-shm").forEach { sufixo ->
                val origem = File(ficheiro.path + sufixo)
                if (origem.exists()) origem.copyTo(File(destino, NOME + sufixo), overwrite = true)
            }
        }

        /**
         * Introduz [Pagamento.baseTributavelCent]. Nos registos que já existiam o IVA
         * incidia sobre a totalidade, por isso a base tributável passa a ser o
         * valor todo — assim os totais antigos mantêm-se exatamente iguais.
         */
        private val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pagamentos ADD COLUMN baseTributavel REAL NOT NULL DEFAULT 0")
                db.execSQL("UPDATE pagamentos SET baseTributavel = valorBase")
            }
        }

        /** Introduz as definições da obra (orçamento total) e o livro de obra. */
        private val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS definicoes (" +
                        "id INTEGER NOT NULL, orcamentoTotal REAL NOT NULL, PRIMARY KEY(id))",
                )
                db.execSQL(SQL_DEFINICOES_INICIAIS)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS eventos (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "dataEpochDay INTEGER NOT NULL, " +
                        "titulo TEXT NOT NULL, " +
                        "descricao TEXT NOT NULL, " +
                        "ordem INTEGER NOT NULL)",
                )
            }
        }

        /**
         * Introduz [Pagamento.contaParaObra]. Tudo o que já estava registado era
         * obra, por isso entra a `1` — os totais existentes não mudam.
         */
        private val MIGRACAO_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE pagamentos ADD COLUMN contaParaObra INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        /**
         * Introduz [Definicoes.ordemGraficos]. Vazio significa "ordem de
         * fábrica", por isso quem já usava a aplicação não vê nada mudar.
         */
        private val MIGRACAO_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE definicoes ADD COLUMN ordemGraficos TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /**
         * Introduz [Definicoes.tema]. Entra em `sistema`, por isso quem já usava
         * a aplicação continua a seguir o modo escuro do telemóvel.
         */
        private val MIGRACAO_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE definicoes ADD COLUMN tema TEXT NOT NULL " +
                        "DEFAULT '${Definicoes.TEMA_SISTEMA}'",
                )
            }
        }

        /**
         * "Sem IVA" passou a significar IVA evitado à taxa normal, e não isenção.
         * Os pagamentos antigos com taxa zero são reescritos em conformidade:
         * taxa normal sobre a totalidade, com o imposto por pagar. O que saiu da
         * conta mantém-se igual; passam é a contar como IVA poupado.
         */
        private val MIGRACAO_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE pagamentos SET taxaIva = $TAXA_IVA_NORMAL, " +
                        "baseTributavel = valorBase, ivaPago = 0 WHERE taxaIva <= 0",
                )
            }
        }

        /**
         * Introduz [Definicoes.ordemLivro]. Entra em `recente` para toda a gente,
         * inclusive para quem já tinha eventos arrumados à mão: o campo
         * [EventoObra.ordem] fica intacto e volta a mandar assim que o livro for
         * posto em modo manual outra vez.
         */
        private val MIGRACAO_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE definicoes ADD COLUMN ordemLivro TEXT NOT NULL " +
                        "DEFAULT '${Definicoes.LIVRO_RECENTE}'",
                )
            }
        }

        /**
         * A versão 9 chegou a existir em builds de desenvolvimento, com tabelas
         * para um separador de links que foi retirado antes de sair daqui. As
         * duas migrações têm o mesmo corpo de propósito: quem está na 9 fica sem
         * as tabelas, e para quem vem da 8 o `IF EXISTS` torna isto inofensivo.
         */
        private val MIGRACAO_8_10 = object : Migration(8, 10) {
            override fun migrate(db: SupportSQLiteDatabase) = largarTabelasDeLinks(db)
        }

        private val MIGRACAO_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) = largarTabelasDeLinks(db)
        }

        // Os links primeiro: têm chave estrangeira para as pastas.
        private fun largarTabelasDeLinks(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS links")
            db.execSQL("DROP TABLE IF EXISTS pastas_links")
        }

        /**
         * Versão 11, tudo de uma vez:
         *
         * - os montantes dos pagamentos passam a cêntimos (`valorBaseCent`,
         *   `baseTributavelCent`). O SQLite não muda o tipo de uma coluna, por
         *   isso a tabela é recriada e os dados copiados — só a dos pagamentos,
         *   que é filha: apagá-la não toca nas categorias. Durante as migrações
         *   o Room ainda não ligou as chaves estrangeiras, e mesmo com elas
         *   ligadas apagar a tabela filha não dispara nenhum CASCADE;
         * - `pagamentos.fornecedor`, `eventos.fotos`, `definicoes.graficosEscondidos`
         *   e `definicoes.areaConstrucao`, todas vazias / a zero;
         * - a tabela `despesas`;
         * - a linha das definições, para quem instalou com a semente estragada.
         */
        private val MIGRACAO_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pagamentos_novo` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`categoriaId` INTEGER NOT NULL, `descricao` TEXT NOT NULL, " +
                        "`dataEpochDay` INTEGER NOT NULL, `valorBaseCent` INTEGER NOT NULL, " +
                        "`baseTributavelCent` INTEGER NOT NULL, `taxaIva` REAL NOT NULL, " +
                        "`ivaPago` INTEGER NOT NULL, `contaParaObra` INTEGER NOT NULL, " +
                        "`notas` TEXT NOT NULL, `fornecedor` TEXT NOT NULL DEFAULT '', " +
                        "FOREIGN KEY(`categoriaId`) REFERENCES `categorias`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT INTO pagamentos_novo (id, categoriaId, descricao, dataEpochDay, " +
                        "valorBaseCent, baseTributavelCent, taxaIva, ivaPago, contaParaObra, notas, fornecedor) " +
                        "SELECT id, categoriaId, descricao, dataEpochDay, " +
                        "CAST(ROUND(valorBase * 100) AS INTEGER), " +
                        "CAST(ROUND(baseTributavel * 100) AS INTEGER), " +
                        "taxaIva, ivaPago, contaParaObra, notas, '' FROM pagamentos",
                )
                db.execSQL("DROP TABLE pagamentos")
                db.execSQL("ALTER TABLE pagamentos_novo RENAME TO pagamentos")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pagamentos_categoriaId` ON `pagamentos` (`categoriaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pagamentos_dataEpochDay` ON `pagamentos` (`dataEpochDay`)")

                db.execSQL("ALTER TABLE definicoes ADD COLUMN graficosEscondidos TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE definicoes ADD COLUMN areaConstrucao REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE eventos ADD COLUMN fotos TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `despesas` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rubrica` TEXT NOT NULL, " +
                        "`descricao` TEXT NOT NULL, `dataEpochDay` INTEGER NOT NULL, " +
                        "`valorCent` INTEGER NOT NULL, `capitalCent` INTEGER NOT NULL, " +
                        "`notas` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_despesas_dataEpochDay` ON `despesas` (`dataEpochDay`)")

                db.execSQL(SQL_CRIAR_DEFINICOES)
            }
        }

        /**
         * Versão 12: despesas fixas (tabela `despesas_fixas`) e, nas despesas,
         * a ligação à fixa que as lançou e a marca "a confirmar". As despesas
         * que já existem ficam sem fixa e confirmadas.
         */
        private val MIGRACAO_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `despesas_fixas` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rubrica` TEXT NOT NULL, " +
                        "`descricao` TEXT NOT NULL, `valorCent` INTEGER NOT NULL, " +
                        "`capitalCent` INTEGER NOT NULL, `valorVariavel` INTEGER NOT NULL, " +
                        "`dia` INTEGER NOT NULL, `ultimoMes` INTEGER NOT NULL, " +
                        "`fimEpochDay` INTEGER, `ativa` INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE despesas ADD COLUMN fixaId INTEGER")
                db.execSQL("ALTER TABLE despesas ADD COLUMN aConfirmar INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Versão 13: as recorrentes deixam de ser só mensais (unidade, intervalo,
         * fim ao fim de N vezes). O "último mês lançado" dá lugar a uma data de
         * início e a uma contagem: cada recorrente antiga passa a começar no seu
         * último mês lançado, com a próxima ocorrência a seguir — exatamente o
         * que já ia acontecer. A tabela é recriada porque perde uma coluna; não
         * tem chaves estrangeiras de nem para ela.
         */
        private val MIGRACAO_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `despesas_fixas_nova` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rubrica` TEXT NOT NULL, " +
                        "`descricao` TEXT NOT NULL, `valorCent` INTEGER NOT NULL, " +
                        "`capitalCent` INTEGER NOT NULL, `valorVariavel` INTEGER NOT NULL, " +
                        "`unidade` TEXT NOT NULL, `intervalo` INTEGER NOT NULL, `dia` INTEGER NOT NULL, " +
                        "`inicioEpochDay` INTEGER NOT NULL, `proximo` INTEGER NOT NULL, " +
                        "`lancadas` INTEGER NOT NULL, `fimEpochDay` INTEGER, `maxOcorrencias` INTEGER, " +
                        "`ativa` INTEGER NOT NULL)",
                )
                // O 1.º dia do último mês lançado, em dias desde 1970 (julianday
                // de 1970-01-01 é 2440587,5).
                db.execSQL(
                    "INSERT INTO despesas_fixas_nova (id, rubrica, descricao, valorCent, capitalCent, " +
                        "valorVariavel, unidade, intervalo, dia, inicioEpochDay, proximo, lancadas, " +
                        "fimEpochDay, maxOcorrencias, ativa) " +
                        "SELECT f.id, f.rubrica, f.descricao, f.valorCent, f.capitalCent, f.valorVariavel, " +
                        "'$UNIDADE_MES', 1, f.dia, " +
                        "CAST(julianday(printf('%04d-%02d-01', f.ultimoMes / 12, f.ultimoMes % 12 + 1)) - 2440587.5 AS INTEGER), " +
                        "1, MAX(1, (SELECT COUNT(*) FROM despesas d WHERE d.fixaId = f.id)), " +
                        "f.fimEpochDay, NULL, f.ativa FROM despesas_fixas f",
                )
                db.execSQL("DROP TABLE despesas_fixas")
                db.execSQL("ALTER TABLE despesas_fixas_nova RENAME TO despesas_fixas")
            }
        }

        /**
         * Versão 14: [Pagamento.origemFundos]. Os pagamentos que já existem
         * ficam como pessoais; os que foram pagos pelo banco mudam-se à mão.
         */
        private val MIGRACAO_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE pagamentos ADD COLUMN origemFundos TEXT NOT NULL DEFAULT '$ORIGEM_PESSOAL'",
                )
            }
        }

        /** Todas as migrações, também usadas pelo teste de migração. */
        internal val MIGRACOES: Array<Migration> = arrayOf(
            MIGRACAO_1_2, MIGRACAO_2_3, MIGRACAO_3_4, MIGRACAO_4_5, MIGRACAO_5_6,
            MIGRACAO_6_7, MIGRACAO_7_8, MIGRACAO_8_10, MIGRACAO_9_10, MIGRACAO_10_11,
            MIGRACAO_11_12, MIGRACAO_12_13, MIGRACAO_13_14,
        )

        /** Definições iniciais no esquema da versão 3, usado pela migração 2→3. */
        private const val SQL_DEFINICOES_INICIAIS =
            "INSERT OR IGNORE INTO definicoes (id, orcamentoTotal) VALUES (${Definicoes.ID}, 0.0)"

        /** Especialidades típicas de uma obra, criadas na primeira execução. */
        private val CATEGORIAS_INICIAIS = listOf(
            "Pedreiro" to 0,
            "Eletricista" to 1,
            "Canalizador" to 2,
            "Carpinteiro" to 3,
            "Pintor" to 4,
            "Materiais" to 5,
            "Projetos e licenças" to 6,
            "Outros" to 7,
        )

        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                CATEGORIAS_INICIAIS.forEach { (nome, cor) ->
                    db.execSQL(
                        "INSERT INTO categorias (nome, corIndex, orcamento) VALUES (?, ?, 0.0)",
                        arrayOf(nome, cor),
                    )
                }
                db.execSQL(SQL_CRIAR_DEFINICOES)
            }
        }
    }
}
