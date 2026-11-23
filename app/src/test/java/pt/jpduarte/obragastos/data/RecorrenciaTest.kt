package pt.jpduarte.obragastos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecorrenciaTest {

    private val hoje = LocalDate.of(2026, 10, 28)

    private fun rec(
        inicio: String,
        dia: Int = LocalDate.parse(inicio).dayOfMonth,
        unidade: String = UNIDADE_MES,
        intervalo: Int = 1,
        variavel: Boolean = false,
        fim: LocalDate? = null,
        max: Int? = null,
        ativa: Boolean = true,
    ) = DespesaFixa(
        id = 7, rubrica = "Comissões bancárias", descricao = "Manutenção", valorCent = 1_248,
        valorVariavel = variavel, unidade = unidade, intervalo = intervalo, dia = dia,
        inicioEpochDay = LocalDate.parse(inicio).toEpochDay(), fimEpochDay = fim?.toEpochDay(),
        maxOcorrencias = max, ativa = ativa,
    )

    private fun datas(f: DespesaFixa, ate: LocalDate = hoje) = lancamentosEmFalta(f, ate).despesas.map { it.data }

    @Test
    fun fimDeSemanaPassaParaSegunda() {
        // 1 nov. 2026 é domingo; 7 nov. 2026 é sábado
        assertEquals(LocalDate.of(2026, 11, 2), dataDoDebito(YearMonth.of(2026, 11), 1))
        assertEquals(LocalDate.of(2026, 11, 9), dataDoDebito(YearMonth.of(2026, 11), 7))
        assertEquals(LocalDate.of(2026, 11, 5), dataDoDebito(YearMonth.of(2026, 11), 5))
    }

    @Test
    fun mesCurtoUsaOUltimoDiaEVoltaAo31() {
        // 31 jan. → 28 fev. 2027 (domingo → 1 mar.) → 31 mar.
        val f = rec("2027-01-29", dia = 31)
        assertEquals(LocalDate.of(2027, 3, 1), f.dataDaOcorrencia(1))
        assertEquals(LocalDate.of(2027, 3, 31), f.dataDaOcorrencia(2))
    }

    @Test
    fun mensalLancaOsMesesEmFalta() {
        // a despesa de 5 jul. é a ocorrência 0; faltam ago., set. (5 é sábado → 7) e out.
        val l = lancamentosEmFalta(rec("2026-07-05"), hoje)
        assertEquals(listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 5)), l.despesas.map { it.data })
        assertEquals(4, l.proximo)
        assertEquals(4, l.lancadas)
        assertTrue(l.despesas.all { it.fixaId == 7L && it.valorCent == 1_248L && !it.aConfirmar })
    }

    @Test
    fun deDoisEmDoisMesesEAnual() {
        assertEquals(listOf(LocalDate.of(2026, 7, 10)), datas(rec("2026-05-10", intervalo = 2), LocalDate.of(2026, 8, 31)))
        // anual: 10 out. 2025 → 12 out. 2026 (10 é sábado)
        assertEquals(listOf(LocalDate.of(2026, 10, 12)), datas(rec("2025-10-10", unidade = UNIDADE_ANO)))
    }

    @Test
    fun semanalEDiarioNaoFogemAoFimDeSemana() {
        // sábado 3 out. 2026, todas as semanas: continua ao sábado
        assertEquals(
            listOf("2026-10-10", "2026-10-17", "2026-10-24").map(LocalDate::parse),
            datas(rec("2026-10-03", unidade = UNIDADE_SEMANA)),
        )
        // de 2 em 2 semanas
        assertEquals(listOf(LocalDate.of(2026, 10, 17)), datas(rec("2026-10-03", unidade = UNIDADE_SEMANA, intervalo = 2)))
        // de 3 em 3 dias, desde 20 out.
        assertEquals(listOf("2026-10-23", "2026-10-26").map(LocalDate::parse), datas(rec("2026-10-20", unidade = UNIDADE_DIA, intervalo = 3)))
    }

    @Test
    fun terminaDepoisDeNVezes() {
        // 3 vezes, contando a primeira (5 jul.): só ago. e set.
        val l = lancamentosEmFalta(rec("2026-07-05", max = 3), hoje)
        assertEquals(2, l.despesas.size)
        assertNull(proximoDebito(rec("2026-07-05", max = 3).copy(proximo = l.proximo, lancadas = l.lancadas)))
    }

    @Test
    fun terminaNumaData() {
        val f = rec("2026-07-05", fim = LocalDate.of(2026, 9, 30))
        assertEquals(2, lancamentosEmFalta(f, hoje).despesas.size)
        assertNull(proximoDebito(f.copy(proximo = 3)))
    }

    @Test
    fun repetirOLancamentoNaoDuplica() {
        val f = rec("2026-07-05")
        val l = lancamentosEmFalta(f, hoje)
        assertEquals(0, lancamentosEmFalta(f.copy(proximo = l.proximo, lancadas = l.lancadas), hoje).despesas.size)
    }

    @Test
    fun naoLancaAntesDoDia() {
        assertEquals(0, lancamentosEmFalta(rec("2026-09-30"), hoje).despesas.size)
    }

    @Test
    fun variavelEntraAConfirmar() {
        assertTrue(lancamentosEmFalta(rec("2026-09-05", variavel = true), hoje).despesas.single().aConfirmar)
    }

    @Test
    fun pausadaNaoLancaERetomarSaltaOQuePassou() {
        val f = rec("2026-05-05", ativa = false)
        assertEquals(0, lancamentosEmFalta(f, hoje).despesas.size)
        assertNull(proximoDebito(f))
        // retomada a 28 out.: jun.–out. já passaram; recomeça em nov.
        val k = ocorrenciaAoRetomar(f, hoje)
        assertEquals(LocalDate.of(2026, 11, 5), f.copy(ativa = true, proximo = k).dataDaOcorrencia(k))
        assertEquals(0, lancamentosEmFalta(f.copy(ativa = true, proximo = k), hoje).despesas.size)
    }

    @Test
    fun mudarARegraComecaNoProximoDebito() {
        // mensal ao dia 5 desde jul., já lançada até out. (próximo = nov.); passa a de 2 em 2 meses
        val f = rec("2026-07-05").copy(proximo = 4, lancadas = 4)
        val g = f.comRegra(UNIDADE_MES, 2, 5)
        assertEquals(LocalDate.of(2026, 11, 5), g.dataNominal(0))
        assertEquals(LocalDate.of(2027, 1, 5), g.dataNominal(1))
        assertEquals(4, g.lancadas)
        // sem mudança de regra, nada muda
        assertEquals(f, f.comRegra(UNIDADE_MES, 1, 5))
    }

    @Test
    fun indiceDoMesIdaEVolta() {
        val m = YearMonth.of(2026, 12)
        assertEquals(m, mesDoIndice(m.indiceMes()))
    }
}
