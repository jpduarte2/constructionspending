package pt.jpduarte.obragastos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class CsvBackupTest {

    private val dia = LocalDate.of(2026, 3, 14).toEpochDay()

    @Test
    fun exportarEImportarDaOMesmo() {
        val categorias = listOf(Categoria(10, "Pedreiro", 2, 90_000.0), Categoria(11, "Eletricista; e afins", 5))
        val pagamentos = listOf(
            Pagamento(
                id = 1, categoriaId = 10, descricao = "Tranche \"1\"", dataEpochDay = dia,
                valorBaseCent = 123_456, baseTributavelCent = 100_000, taxaIva = 23.0, ivaPago = true,
                contaParaObra = true, notas = "linha 1\nlinha 2", fornecedor = "Silva, Lda.",
            ),
            Pagamento(
                id = 2, categoriaId = 11, descricao = "", dataEpochDay = dia,
                valorBaseCent = 5, baseTributavelCent = 5, taxaIva = 13.0, ivaPago = false, contaParaObra = false,
                origemFundos = ORIGEM_BANCARIO,
            ),
        )
        val eventos = listOf(EventoObra(dataEpochDay = dia, titulo = "Laje", descricao = "ok", fotos = "a.jpg|b.jpg"))
        val fixas = listOf(
            DespesaFixa(
                id = 9, rubrica = RUBRICA_PRESTACAO, descricao = "Crédito", valorCent = 61_012, capitalCent = 38_000,
                valorVariavel = true, dia = 25, inicioEpochDay = LocalDate.of(2025, 9, 25).toEpochDay(),
                proximo = 8, lancadas = 8, fimEpochDay = LocalDate.of(2055, 1, 25).toEpochDay(),
            ),
            DespesaFixa(
                id = 4, rubrica = "Seguros", valorCent = 31_850, unidade = UNIDADE_SEMANA, intervalo = 2, dia = 3,
                inicioEpochDay = LocalDate.of(2026, 1, 3).toEpochDay(), proximo = 0, lancadas = 3,
                maxOcorrencias = 10, ativa = false,
            ),
        )
        val despesas = listOf(
            Despesa(rubrica = RUBRICA_PRESTACAO, descricao = "março", dataEpochDay = dia, valorCent = 61_012, capitalCent = 38_000, fixaId = 9, aConfirmar = true),
            Despesa(rubrica = "Condomínio", dataEpochDay = dia, valorCent = 4_000, notas = "a;b"),
        )

        val csv = CsvBackup.exportar(250_000.0, categorias, pagamentos, eventos, despesas, 210.5, fixas)
        val c = CsvBackup.importar(csv)

        assertEquals(250_000.0, c.orcamentoTotal, 0.0)
        assertEquals(210.5, c.areaConstrucao, 0.0)
        assertEquals(categorias.map { it.copy(id = 0) }, c.categorias)
        assertEquals(listOf("Pedreiro", "Eletricista; e afins"), c.pagamentos.map { it.categoriaNome })
        assertEquals(pagamentos.map { it.copy(id = 0, categoriaId = 0) }, c.pagamentos.map { it.pagamento })
        assertEquals(eventos.map { it.copy(ordem = 0) }, c.eventos)
        assertEquals(despesas.map { it.copy(fixaId = null) }, c.despesas.map { it.despesa })
        assertEquals(listOf("9", ""), c.despesas.map { it.fixa })
        assertEquals(listOf("9", "4"), c.fixas.map { it.first })
        assertEquals(fixas.map { it.copy(id = 0) }, c.fixas.map { it.second })
    }

    /** Um ficheiro exportado pela versão 1 (sem despesas, fornecedor, fotos nem área). */
    @Test
    fun importaFicheiroDaVersao1() {
        val v1 = "﻿#OBRAGASTOS;versao;1\r\n\r\n" +
            "#ORCAMENTO\r\norcamentoTotal\r\n150000,00\r\n\r\n" +
            "#CATEGORIAS\r\nnome;corIndex;orcamento\r\nPedreiro;0;0,00\r\n\r\n" +
            "#PAGAMENTOS\r\ndata;categoria;descricao;valorBase;baseTributavel;taxaIva;ivaPago;contaParaObra;notas\r\n" +
            "2025-10-01;Pedreiro;Sinal;1 000,50 €;;0,00;Não;Sim;\r\n\r\n" +
            "#EVENTOS\r\ndata;titulo;descricao\r\n01/10/2025;Início;\r\n"
        val c = CsvBackup.importar(v1)
        assertEquals(150_000.0, c.orcamentoTotal, 0.0)
        assertEquals(0.0, c.areaConstrucao, 0.0)
        val p = c.pagamentos.single().pagamento
        assertEquals(100_050L, p.valorBaseCent)
        // taxa zero = IVA poupado à taxa normal
        assertEquals(TAXA_IVA_NORMAL, p.taxaIva, 0.0)
        assertEquals(false, p.ivaPago)
        assertEquals(100_050L, p.baseTributavelCent)
        assertEquals("", p.fornecedor)
        // sem a coluna da origem dos fundos, o pagamento é pessoal
        assertEquals(ORIGEM_PESSOAL, p.origemFundos)
        assertEquals(LocalDate.of(2025, 10, 1).toEpochDay(), c.eventos.single().dataEpochDay)
        assertEquals("", c.eventos.single().fotos)
        assertEquals(emptyList<CsvBackup.DespesaImportada>(), c.despesas)
        assertEquals(0, c.fixas.size)
    }

    /** Recorrentes da versão 3 (só mensais, com o último mês lançado). */
    @Test
    fun importaRecorrentesDaVersao3() {
        val v3 = "#OBRAGASTOS;versao;3\r\n" +
            "#DESPESAS_FIXAS\r\nchave;rubrica;descricao;valor;capital;valorVariavel;dia;ultimoMes;fim;ativa\r\n" +
            "5;Luz;Contador;55,23;0,00;Sim;10;2026-09;;Sim\r\n"
        val f = CsvBackup.importar(v3).fixas.single().second
        assertEquals(UNIDADE_MES, f.unidade)
        assertEquals(1, f.intervalo)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), f.inicioEpochDay)
        // a próxima é outubro, como era na versão 3
        assertEquals(LocalDate.of(2026, 10, 12), proximoDebito(f))
    }

    @Test
    fun recusaFicheiroQueNaoEhDaApp() {
        assertThrows(IllegalArgumentException::class.java) { CsvBackup.importar("nome;valor\r\nx;1\r\n") }
    }
}
