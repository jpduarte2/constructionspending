package pt.jpduarte.obragastos.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile

/**
 * A migração 10 → 11 sobre uma base de dados criada com o esquema exato da
 * versão 10 (o `schemas/.../10.json`, exportado antes de mexer em nada). É o
 * que garante que quem instala a versão nova por cima não perde dados.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracaoTest {

    @get:Rule
    val ajudante = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ObraDatabase::class.java,
    )

    private val contexto get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun fechar() = ObraDatabase.esquecerInstancia()

    /** Dados como estariam no telemóvel na versão 10. */
    private fun semearV10(nome: String) {
        ajudante.createDatabase(nome, 10).apply {
            execSQL("INSERT INTO categorias (id, nome, corIndex, orcamento) VALUES (1, 'Pedreiro', 0, 90000.0)")
            execSQL("INSERT INTO categorias (id, nome, corIndex, orcamento) VALUES (2, 'Terreno', 3, 0.0)")
            execSQL(
                "INSERT INTO pagamentos (id, categoriaId, descricao, dataEpochDay, valorBase, baseTributavel, " +
                    "taxaIva, ivaPago, contaParaObra, notas) VALUES " +
                    "(1, 1, '1.ª tranche', 20000, 1234.56, 1234.56, 23.0, 1, 1, 'nota; com ponto e vírgula'), " +
                    "(2, 1, 'Honorários', 20010, 6000.0, 1500.0, 23.0, 1, 1, ''), " +
                    // 100,1 em Double é 100,0999…: tem de dar 10010 cêntimos, não 10009.
                    "(3, 1, 'Sem IVA', 20020, 100.1, 100.1, 23.0, 0, 1, ''), " +
                    "(4, 2, 'Escritura', 19000, 42000.0, 42000.0, 23.0, 0, 0, '')",
            )
            execSQL(
                "INSERT INTO definicoes (id, orcamentoTotal, ordemGraficos, tema, ordemLivro) " +
                    "VALUES (1, 250000.0, 'mensal,resumo', 'escuro', 'manual')",
            )
            execSQL("INSERT INTO eventos (id, dataEpochDay, titulo, descricao, ordem) VALUES (7, 20000, 'Fundações', 'betão', 3)")
            close()
        }
    }

    @Test
    fun migra10Para11SemPerderNada() {
        semearV10("migracao.db")
        val db = ajudante.runMigrationsAndValidate("migracao.db", 11, true, *ObraDatabase.MIGRACOES)

        db.query("SELECT id, categoriaId, descricao, valorBaseCent, baseTributavelCent, taxaIva, ivaPago, contaParaObra, notas, fornecedor FROM pagamentos ORDER BY id").use { c ->
            assertEquals(4, c.count)
            c.moveToNext()
            assertEquals(1L, c.getLong(0)); assertEquals(1L, c.getLong(1)); assertEquals("1.ª tranche", c.getString(2))
            assertEquals(123456L, c.getLong(3)); assertEquals(123456L, c.getLong(4))
            assertEquals(23.0, c.getDouble(5), 0.0); assertEquals(1, c.getInt(6)); assertEquals(1, c.getInt(7))
            assertEquals("nota; com ponto e vírgula", c.getString(8)); assertEquals("", c.getString(9))
            c.moveToNext()
            assertEquals(600000L, c.getLong(3)); assertEquals(150000L, c.getLong(4))
            c.moveToNext()
            assertEquals(10010L, c.getLong(3)); assertEquals(0, c.getInt(6))
            c.moveToNext()
            assertEquals(4200000L, c.getLong(3)); assertEquals(0, c.getInt(7))
        }
        db.query("SELECT orcamentoTotal, ordemGraficos, tema, ordemLivro, graficosEscondidos, areaConstrucao FROM definicoes").use { c ->
            assertEquals(1, c.count)
            c.moveToNext()
            assertEquals(250000.0, c.getDouble(0), 0.0)
            assertEquals("mensal,resumo", c.getString(1))
            assertEquals("escuro", c.getString(2))
            assertEquals("manual", c.getString(3))
            assertEquals("", c.getString(4))
            assertEquals(0.0, c.getDouble(5), 0.0)
        }
        db.query("SELECT id, titulo, descricao, ordem, fotos FROM eventos").use { c ->
            c.moveToNext()
            assertEquals(7L, c.getLong(0)); assertEquals("Fundações", c.getString(1))
            assertEquals("betão", c.getString(2)); assertEquals(3, c.getInt(3)); assertEquals("", c.getString(4))
        }
        db.query("SELECT COUNT(*) FROM categorias").use { c -> c.moveToNext(); assertEquals(2, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM despesas").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }

        // O autoincremento continua a seguir: um pagamento novo não reutiliza ids.
        db.execSQL(
            "INSERT INTO pagamentos (categoriaId, descricao, dataEpochDay, valorBaseCent, baseTributavelCent, " +
                "taxaIva, ivaPago, contaParaObra, notas) VALUES (1, 'novo', 20100, 100, 100, 23.0, 1, 1, '')",
        )
        db.query("SELECT MAX(id) FROM pagamentos").use { c -> c.moveToNext(); assertEquals(5L, c.getLong(0)) }
        // E a chave estrangeira com CASCADE sobreviveu à recriação da tabela.
        db.query("PRAGMA foreign_key_list(pagamentos)").use { c ->
            assertEquals(1, c.count); c.moveToNext()
            assertEquals("categorias", c.getString(c.getColumnIndexOrThrow("table")))
            assertEquals("CASCADE", c.getString(c.getColumnIndexOrThrow("on_delete")))
        }
        db.close()
    }

    /** 11 → 12: despesas fixas. As despesas que já existem ficam sem fixa e confirmadas. */
    @Test
    fun migra11Para12() {
        ajudante.createDatabase("migracao12.db", 11).apply {
            execSQL(
                "INSERT INTO despesas (id, rubrica, descricao, dataEpochDay, valorCent, capitalCent, notas) " +
                    "VALUES (3, 'Luz', 'março', 20500, 4520, 0, 'n')",
            )
            close()
        }
        val db = ajudante.runMigrationsAndValidate("migracao12.db", 12, true, *ObraDatabase.MIGRACOES)
        db.query("SELECT id, rubrica, valorCent, fixaId, aConfirmar FROM despesas").use { c ->
            c.moveToNext()
            assertEquals(3L, c.getLong(0)); assertEquals("Luz", c.getString(1)); assertEquals(4520L, c.getLong(2))
            assertTrue(c.isNull(3)); assertEquals(0, c.getInt(4))
        }
        db.query("SELECT COUNT(*) FROM despesas_fixas").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
        db.close()
    }

    /** 12 → 13: a recorrente mensal antiga continua exatamente onde estava. */
    @Test
    fun migra12Para13() {
        ajudante.createDatabase("migracao13.db", 12).apply {
            // ultimoMes 24321 = outubro de 2026
            execSQL(
                "INSERT INTO despesas_fixas (id, rubrica, descricao, valorCent, capitalCent, valorVariavel, dia, " +
                    "ultimoMes, fimEpochDay, ativa) VALUES (2, 'Luz', 'Contador', 6180, 0, 1, 10, 24321, NULL, 1)",
            )
            execSQL(
                "INSERT INTO despesas (rubrica, descricao, dataEpochDay, valorCent, capitalCent, notas, fixaId, aConfirmar) " +
                    "VALUES ('Luz', '', 20738, 5523, 0, '', 2, 0), ('Luz', '', 20708, 5000, 0, '', 2, 0)",
            )
            close()
        }
        val db = ajudante.runMigrationsAndValidate("migracao13.db", 13, true, *ObraDatabase.MIGRACOES)
        db.query("SELECT unidade, intervalo, dia, inicioEpochDay, proximo, lancadas, maxOcorrencias, valorVariavel FROM despesas_fixas").use { c ->
            c.moveToNext()
            assertEquals("mes", c.getString(0)); assertEquals(1, c.getInt(1)); assertEquals(10, c.getInt(2))
            assertEquals(java.time.LocalDate.of(2026, 10, 1).toEpochDay(), c.getLong(3))
            assertEquals(1, c.getInt(4)); assertEquals(2, c.getInt(5)); assertTrue(c.isNull(6)); assertEquals(1, c.getInt(7))
        }
        db.close()
    }

    /** 13 → 14: os pagamentos que já existem ficam com origem pessoal. */
    @Test
    fun migra13Para14() {
        ajudante.createDatabase("migracao14.db", 13).apply {
            execSQL("INSERT INTO categorias (id, nome, corIndex, orcamento) VALUES (1, 'Pedreiro', 0, 0.0)")
            execSQL(
                "INSERT INTO pagamentos (id, categoriaId, descricao, dataEpochDay, valorBaseCent, baseTributavelCent, " +
                    "taxaIva, ivaPago, contaParaObra, notas, fornecedor) VALUES " +
                    "(5, 1, 'Tranche', 20500, 100000, 100000, 23.0, 1, 1, '', 'Silva')",
            )
            close()
        }
        val db = ajudante.runMigrationsAndValidate("migracao14.db", 14, true, *ObraDatabase.MIGRACOES)
        db.query("SELECT id, valorBaseCent, fornecedor, origemFundos FROM pagamentos").use { c ->
            assertEquals(1, c.count)
            c.moveToNext()
            assertEquals(5L, c.getLong(0)); assertEquals(100000L, c.getLong(1))
            assertEquals("Silva", c.getString(2)); assertEquals(ORIGEM_PESSOAL, c.getString(3))
        }
        db.close()
    }

    /**
     * O caminho verdadeiro da app: a base de dados v10 no sítio dela, aberta por
     * [ObraDatabase.get]. Tem de ficar uma cópia intacta da v10 e os totais
     * calculados pela app têm de bater com os valores antigos.
     */
    @Test
    fun abrirAppSobreV10GuardaCopiaEMigra() = runBlocking {
        contexto.getDatabasePath(ObraDatabase.NOME).delete()
        File(contexto.filesDir, "copias").deleteRecursively()
        semearV10(ObraDatabase.NOME)

        val dao = ObraDatabase.get(contexto).dao()
        val pagamentos = dao.pagamentos().first()
        assertEquals(4, pagamentos.size)
        val p1 = pagamentos.first { it.id == 1L }
        assertEquals(1234.56, p1.valorBase, 0.0)
        // 1234,56 × 23% = 283,9488 → 283,95 (arredondado ao cêntimo, como na fatura)
        assertEquals(28395L, p1.ivaCent)
        assertEquals(1518.51, p1.totalPago, 0.0)
        val honorarios = pagamentos.first { it.id == 2L }
        assertEquals(34500L, honorarios.ivaCent)
        assertEquals(103500L, honorarios.ivaPoupadoCent)

        val d = dao.definicoes().first()!!
        assertEquals("manual", d.ordemLivro)
        assertEquals(250000.0, d.orcamentoTotal, 0.0)

        val copia = File(contexto.filesDir, "copias/antes-da-v$VERSAO_BD/${ObraDatabase.NOME}")
        assertTrue("devia existir a cópia de antes da migração", copia.exists())
        val versaoDaCopia = RandomAccessFile(copia, "r").use { it.seek(60); it.readInt() }
        assertEquals(10, versaoDaCopia)
    }
}
