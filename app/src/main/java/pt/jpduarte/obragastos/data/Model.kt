package pt.jpduarte.obragastos.data

import android.content.Context
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
import java.time.LocalDate

/**
 * Uma especialidade / rubrica de obra (pedreiro, eletricista, materiais, ...).
 *
 * [orcamento] a zero significa "sem orçamento definido" — nesse caso a categoria
 * não aparece na secção de orçamentos dos gráficos.
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
 * O valor é sempre guardado **sem IVA** em [valorBase]; [taxaIva] é a percentagem
 * aplicável e [ivaPago] diz se o IVA saiu efetivamente da conta. Quando o IVA não
 * foi pago, o montante correspondente conta como poupança — ver [ivaPoupado].
 *
 * O IVA pode incidir só sobre **parte** do pagamento: é o que [baseTributavel]
 * registra. Numa fatura de 6000 € em que só 1500 € foram tributados a 23%, fica
 * `valorBase = 6000`, `baseTributavel = 1500`, e o IVA são 345 € — não 1380 €.
 * Quando o IVA incide sobre tudo, [baseTributavel] é igual a [valorBase].
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
    /** Valor total do pagamento, sem IVA. */
    val valorBase: Double,
    /** Parte de [valorBase] sobre a qual o IVA incide. Igual a [valorBase] quando incide sobre tudo. */
    val baseTributavel: Double,
    /** Taxa de IVA em percentagem (23.0, 13.0, 6.0, 0.0, ou outra). */
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
)

/**
 * Taxa normal de IVA. Assume-se que tudo seria tributado a esta taxa, por isso
 * um pagamento "sem IVA" é registado com ela e com o imposto por pagar — a
 * diferença conta como poupança.
 */
const val TAXA_IVA_NORMAL = 23.0

/** Montante de IVA que a taxa implica sobre a base tributável, tenha sido pago ou não. */
val Pagamento.valorIva: Double get() = baseTributavel * taxaIva / 100.0

/**
 * O IVA que o pagamento teria se a taxa incidisse sobre a totalidade — a
 * referência contra a qual se mede a poupança.
 */
val Pagamento.ivaSeTudoTributado: Double get() = valorBase * taxaIva / 100.0

/** true quando o IVA incidiu só sobre parte do pagamento. */
val Pagamento.ivaParcial: Boolean get() = taxaIva > 0.0 && baseTributavel < valorBase - 0.005

/** O que efetivamente saiu da conta. */
val Pagamento.totalPago: Double get() = valorBase + if (ivaPago) valorIva else 0.0

/** IVA efetivamente pago. */
val Pagamento.ivaEfetivo: Double get() = if (ivaPago) valorIva else 0.0

/**
 * IVA que se evitou pagar — inclui as duas formas de o evitar: não pagar o IVA
 * devido, e o IVA não ter incidido sobre a totalidade. Numa fatura de 6000 € a
 * 23% em que só 1500 € foram tributados, pagaram-se 345 € e pouparam-se os
 * 1035 € que os restantes 4500 € teriam custado de imposto.
 */
val Pagamento.ivaPoupado: Double
    get() = (ivaSeTudoTributado - ivaEfetivo).coerceAtLeast(0.0)

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
)

val EventoObra.data: LocalDate get() = LocalDate.ofEpochDay(dataEpochDay)

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

    /* ------------------------------ definições ------------------------------ */

    @Query("SELECT * FROM definicoes WHERE id = ${Definicoes.ID}")
    fun definicoes(): Flow<Definicoes?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(definicoes: Definicoes)

    @Query("INSERT OR IGNORE INTO definicoes (id, orcamentoTotal, ordemGraficos, tema, ordemLivro) VALUES (${Definicoes.ID}, 0.0, '', '${Definicoes.TEMA_SISTEMA}', '${Definicoes.LIVRO_RECENTE}')")
    suspend fun criarDefinicoesSeFaltarem()

    @Query("UPDATE definicoes SET orcamentoTotal = :valor WHERE id = ${Definicoes.ID}")
    suspend fun gravarOrcamentoTotal(valor: Double)

    @Query("UPDATE definicoes SET ordemGraficos = :ordem WHERE id = ${Definicoes.ID}")
    suspend fun gravarOrdemGraficos(ordem: String)

    @Query("UPDATE definicoes SET tema = :tema WHERE id = ${Definicoes.ID}")
    suspend fun gravarTema(tema: String)

    @Query("UPDATE definicoes SET ordemLivro = :modo WHERE id = ${Definicoes.ID}")
    suspend fun gravarOrdemLivro(modo: String)

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

        // Só o orçamento: a ordem dos gráficos é uma preferência do ecrã, não
        // faz parte do backup, e não deve ser apagada por uma importação.
        definirOrcamentoTotal(conteudo.orcamentoTotal)
    }
}

@Database(
    entities = [Categoria::class, Pagamento::class, Definicoes::class, EventoObra::class],
    version = 10,
    exportSchema = false,
)
abstract class ObraDatabase : RoomDatabase() {

    abstract fun dao(): ObraDao

    companion object {
        @Volatile
        private var instancia: ObraDatabase? = null

        fun get(context: Context): ObraDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    ObraDatabase::class.java,
                    "obragastos.db",
                )
                    .addCallback(SeedCallback)
                    .addMigrations(
                        MIGRACAO_1_2, MIGRACAO_2_3, MIGRACAO_3_4, MIGRACAO_4_5, MIGRACAO_5_6,
                        MIGRACAO_6_7, MIGRACAO_7_8, MIGRACAO_8_10, MIGRACAO_9_10,
                    )
                    .build()
                    .also { instancia = it }
            }

        /**
         * Introduz [Pagamento.baseTributavel]. Nos registos que já existiam o IVA
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
                db.execSQL(
                    "INSERT OR IGNORE INTO definicoes (id, orcamentoTotal, ordemGraficos, tema) " +
                        "VALUES (${Definicoes.ID}, 0.0, '', '${Definicoes.TEMA_SISTEMA}')",
                )
            }
        }
    }
}
