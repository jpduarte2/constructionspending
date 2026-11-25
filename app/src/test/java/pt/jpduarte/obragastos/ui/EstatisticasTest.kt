package pt.jpduarte.obragastos.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.jpduarte.obragastos.data.Categoria
import pt.jpduarte.obragastos.data.Despesa
import pt.jpduarte.obragastos.data.ORIGEM_BANCARIO
import pt.jpduarte.obragastos.data.ORIGEM_NAO_APLICA
import pt.jpduarte.obragastos.data.ORIGEM_PESSOAL
import pt.jpduarte.obragastos.data.RUBRICA_PRESTACAO
import pt.jpduarte.obragastos.data.Pagamento
import java.time.LocalDate
import java.time.YearMonth

class EstatisticasTest {

    private val cat = Categoria(1, "Pedreiro", 0)

    private fun pag(
        dia: String, euros: Long, obra: Boolean = true, pago: Boolean = true, fornecedor: String = "",
        origem: String = ORIGEM_PESSOAL,
    ) = Pagamento(
        categoriaId = 1, descricao = "", dataEpochDay = LocalDate.parse(dia).toEpochDay(),
        valorBaseCent = euros * 100, baseTributavelCent = euros * 100, taxaIva = 23.0, ivaPago = pago,
        contaParaObra = obra, fornecedor = fornecedor, origemFundos = origem,
    )

    private fun dados(pags: List<Pagamento>, orcamento: Double = 0.0, hoje: String = "2026-04-15") =
        calcularDadosGraficos(listOf(cat), pags, emptyList(), orcamento, 0.0, LocalDate.parse(hoje))

    @Test
    fun mesesSemSaltosEAcumulado() {
        val d = dados(listOf(pag("2026-01-10", 100, pago = false), pag("2026-03-02", 200, pago = false)))
        assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)), d.porMes.map { it.first })
        assertEquals(listOf(100.0, 0.0, 200.0), d.porMes.map { it.second })
        assertEquals(listOf(100.0, 100.0, 300.0), d.acumulado.map { it.second })
        assertEquals(listOf(23.0, 23.0, 69.0), d.ivaPoupadoAcumulado.map { it.second })
    }

    @Test
    fun custosForaDaObraNaoEntramNaEvolucao() {
        val d = dados(listOf(pag("2026-01-10", 100, pago = false), pag("2020-01-10", 50_000, obra = false, pago = false)))
        assertEquals(1, d.porMes.size)
        assertEquals(50_000.0, d.resumoExtra.totalPago, 0.0)
    }

    /** A média ignora o mês corrente (vai a meio) e usa os 3 últimos completos. */
    @Test
    fun previsaoAoRitmoDosUltimosMeses() {
        val d = dados(
            listOf(
                pag("2025-12-05", 9_000, pago = false), // fica de fora: só contam os 3 últimos completos
                pag("2026-01-05", 1_000, pago = false),
                pag("2026-02-05", 2_000, pago = false),
                pag("2026-03-05", 3_000, pago = false),
                pag("2026-04-05", 100, pago = false), // mês corrente
            ),
            orcamento = 20_000.0,
        )
        val p = d.previsao()
        assertNotNull(p)
        assertEquals(2_000.0, p!!.ritmoMensal, 0.001)
        // gasto 15 100 → faltam 4 900 → 3 meses a 2 000 → julho
        assertEquals(YearMonth.of(2026, 7), p.mesEsgota)
        assertEquals(15_100.0, p.projecao.first().second, 0.001)
        assertEquals(21_100.0, p.projecao.last().second, 0.001)
    }

    @Test
    fun previsaoComOrcamentoJaPassado() {
        val d = dados(listOf(pag("2026-01-05", 1_000, pago = false), pag("2026-02-05", 2_000, pago = false)), orcamento = 2_000.0)
        val p = d.previsao()!!
        assertTrue(p.jaExcedeu)
        assertNull(p.mesEsgota)
    }

    @Test
    fun semDoisMesesNaoHaPrevisao() {
        assertNull(dados(listOf(pag("2026-01-05", 1_000))).previsao())
    }

    @Test
    fun comparacaoComMesAnteriorEMedia() {
        val d = dados(
            listOf(
                pag("2026-01-05", 1_000, pago = false),
                pag("2026-02-05", 2_000, pago = false),
                pag("2026-03-05", 3_000, pago = false),
                pag("2026-04-05", 4_000, pago = false),
            ),
        )
        val c = d.comparacao()!!
        assertEquals(4_000.0, c.esteMes, 0.0)
        assertEquals(3_000.0, c.mesAnterior, 0.0)
        assertEquals(2_000.0, c.media, 0.0)
    }

    @Test
    fun fornecedoresJuntamGrafias() {
        val d = dados(
            listOf(
                pag("2026-01-05", 100, fornecedor = "Silva Lda"),
                pag("2026-01-06", 100, fornecedor = "Silva Lda"),
                pag("2026-01-07", 100, fornecedor = " silva lda "),
                pag("2026-01-08", 50, fornecedor = "Costa"),
                pag("2026-01-09", 10),
            ),
        )
        assertEquals(listOf("Silva Lda", "Costa"), d.fornecedores.map { it.nome })
        assertEquals(3, d.fornecedores.first().n)
        assertEquals(1, d.nSemFornecedor)
    }

    /** Total da casa (obra e fora) por origem; mês a mês só a obra, sem saltos. */
    @Test
    fun origemDosFundos() {
        val d = dados(
            listOf(
                pag("2026-01-05", 100),
                pag("2026-01-20", 300, origem = ORIGEM_BANCARIO),
                pag("2026-03-02", 200, pago = false, origem = ORIGEM_BANCARIO),
                pag("2020-01-10", 1_000, obra = false, pago = false, origem = ORIGEM_BANCARIO),
                // N/A: fora da comparação, mas continua no total da obra
                pag("2026-01-25", 50, pago = false, origem = ORIGEM_NAO_APLICA),
            ),
        )
        assertEquals(50.0, d.origem.naoAplica, 0.001)
        assertEquals(123.0 + 369.0 + 200.0 + 1_000.0, d.origem.total, 0.001)
        assertEquals(123.0 + 369.0 + 200.0 + 50.0, d.resumo.totalPago, 0.001)
        // 100 e 300 com IVA pago (×1,23); 200 e 1000 sem IVA
        assertEquals(123.0, d.origem.pessoal, 0.001)
        assertEquals(369.0 + 200.0 + 1_000.0, d.origem.bancario, 0.001)
        assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)), d.origemPorMes.map { it.mes })
        assertEquals(listOf(123.0, 0.0, 0.0), d.origemPorMes.map { it.origem.pessoal })
        assertEquals(listOf(369.0, 0.0, 200.0), d.origemPorMes.map { it.origem.bancario })
    }

    @Test
    fun classesDeIva() {
        val d = dados(listOf(pag("2026-01-05", 100), pag("2026-01-06", 200, pago = false)))
        assertEquals(listOf("23%", "Sem IVA"), d.taxas.map { it.rotulo })
        assertEquals(listOf(100.0, 200.0), d.taxas.map { it.valor })
    }

    @Test
    fun ordemDosGraficosAceitaBlocosNovos() {
        // Uma ordem gravada pela versão anterior (sem os blocos novos).
        val antiga = listOf("mensal", "resumo", "orcamento", "extra", "iva", "categorias", "donut", "obra_vs_fora", "maiores", "orcamentos")
        val ordem = ordemUtil(antiga)
        assertEquals(CATALOGO_GRAFICOS.size, ordem.size)
        // o que o utilizador arrumou mantém-se
        assertEquals("mensal", ordem.first())
        // um bloco novo entra junto do seu vizinho de fábrica
        assertEquals(ordem.indexOf("mensal") + 1, ordem.indexOf("mensal_categorias"))
        // ids desconhecidos (blocos retirados) desaparecem
        assertEquals(ordemUtil(antiga + "iva_extra"), ordem)
    }

    @Test
    fun exemploTemDadosParaTodosOsGraficos() {
        CATALOGO_GRAFICOS.forEach { g ->
            assertTrue("o exemplo não desenha ${g.id}", g.temDados(Exemplo.dados))
        }
    }

    /** Prestações separadas em juros e capital, mês a mês e sem saltos. */
    @Test
    fun prestacoesEmJurosECapital() {
        fun d(dia: String, rubrica: String, valor: Long, capital: Long = 0) =
            Despesa(rubrica = rubrica, dataEpochDay = LocalDate.parse(dia).toEpochDay(), valorCent = valor, capitalCent = capital)
        val r = resumoDespesas(
            listOf(
                d("2026-01-25", RUBRICA_PRESTACAO, 61_000, 38_000),
                d("2026-03-25", RUBRICA_PRESTACAO, 61_000, 38_500),
                d("2026-02-10", "Luz", 4_500),
                // sem capital indicado: não entra na divisão
                d("2026-04-25", RUBRICA_PRESTACAO, 61_000),
            ),
        )
        assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)), r.prestacoes.map { it.mes })
        assertEquals(listOf(230.0, 0.0, 225.0), r.prestacoes.map { it.juros })
        assertEquals(listOf(380.0, 0.0, 385.0), r.prestacoes.map { it.capital })
        assertEquals(765.0, r.capital, 0.0)
        // juros 230 + 225, luz 45, e a prestação sem capital conta toda (610)
        assertEquals(1_110.0, r.custoReal, 0.001)
    }
}
