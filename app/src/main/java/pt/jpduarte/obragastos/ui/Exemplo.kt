package pt.jpduarte.obragastos.ui

import pt.jpduarte.obragastos.data.Categoria
import pt.jpduarte.obragastos.data.Despesa
import pt.jpduarte.obragastos.data.EventoObra
import pt.jpduarte.obragastos.data.ORIGEM_BANCARIO
import pt.jpduarte.obragastos.data.ORIGEM_PESSOAL
import pt.jpduarte.obragastos.data.Pagamento
import pt.jpduarte.obragastos.data.RUBRICA_PRESTACAO
import java.time.LocalDate

/**
 * Uma obra inventada, para mostrar como fica cada gráfico antes de haver dados
 * que o desenhem (no ecrã de escolha dos gráficos e nas pré-visualizações).
 * Passa pelas mesmas contas que os dados verdadeiros — [calcularDadosGraficos].
 */
object Exemplo {

    val hoje: LocalDate = LocalDate.of(2026, 6, 18)

    val categorias = listOf(
        Categoria(1, "Pedreiro", 0, 90_000.0),
        Categoria(2, "Eletricista", 1, 18_000.0),
        Categoria(3, "Canalizador", 2, 15_000.0),
        Categoria(4, "Carpinteiro", 3, 0.0),
        Categoria(5, "Materiais", 5, 40_000.0),
        Categoria(6, "Projetos e licenças", 6, 0.0),
    )

    private val fornecedores = mapOf(
        1L to "Construções Silva",
        2L to "EletroNorte",
        3L to "Águas & Tubos",
        4L to "Madeiras Costa",
        5L to "Leroy Merlin",
        6L to "Gabinete Arq. Lima",
    )

    /**
     * Dez meses de obra, com o ritmo a variar de propósito: arranque lento,
     * picos na estrutura e nos acabamentos.
     */
    val pagamentos: List<Pagamento> = buildList {
        var id = 1L
        val inicio = LocalDate.of(2025, 9, 3)
        // (mês desde o início, categoria, valor sem IVA, taxa, IVA pago)
        val linhas = listOf(
            Triple(0, 6L, 4_500.0), Triple(0, 1L, 6_000.0),
            Triple(1, 1L, 14_000.0), Triple(1, 5L, 6_200.0),
            Triple(2, 1L, 18_500.0), Triple(2, 5L, 9_800.0), Triple(2, 3L, 1_200.0),
            Triple(3, 1L, 12_000.0), Triple(3, 5L, 4_100.0),
            Triple(4, 2L, 5_600.0), Triple(4, 3L, 4_300.0), Triple(4, 5L, 2_400.0),
            Triple(5, 1L, 9_000.0), Triple(5, 2L, 3_900.0),
            Triple(6, 4L, 7_800.0), Triple(6, 5L, 5_300.0), Triple(6, 3L, 2_600.0),
            Triple(7, 1L, 6_500.0), Triple(7, 4L, 4_200.0),
            Triple(8, 2L, 2_800.0), Triple(8, 5L, 3_700.0),
            Triple(9, 4L, 2_100.0), Triple(9, 1L, 3_000.0),
        )
        linhas.forEachIndexed { i, (mes, cat, valor) ->
            val semIva = i % 4 == 1
            val parcial = i % 7 == 3
            val taxa = if (cat == 5L && i % 3 == 0) 6.0 else 23.0
            add(
                Pagamento(
                    id = id++,
                    categoriaId = cat,
                    descricao = "",
                    dataEpochDay = inicio.plusMonths(mes.toLong()).plusDays((i * 5L) % 25).toEpochDay(),
                    valorBaseCent = (valor * 100).toLong(),
                    baseTributavelCent = if (parcial) (valor * 25).toLong() else (valor * 100).toLong(),
                    taxaIva = taxa,
                    ivaPago = !semIva,
                    fornecedor = fornecedores[cat].orEmpty(),
                    // Os dois primeiros meses com poupanças; depois, sobretudo o crédito.
                    origemFundos = if (mes >= 2 && i % 3 != 2) ORIGEM_BANCARIO else ORIGEM_PESSOAL,
                ),
            )
        }
        add(
            Pagamento(
                id = id, categoriaId = 6L, descricao = "Escritura do terreno",
                dataEpochDay = LocalDate.of(2024, 11, 20).toEpochDay(),
                valorBaseCent = 4_200_000, baseTributavelCent = 4_200_000, taxaIva = 23.0,
                ivaPago = false, contaParaObra = false, origemFundos = ORIGEM_BANCARIO,
            ),
        )
    }

    val eventos: List<EventoObra> = listOf(
        "2025-09-05" to "Montagem do estaleiro",
        "2025-09-22" to "Fundações",
        "2025-11-10" to "Laje do rés-do-chão",
        "2026-01-15" to "Cobertura",
        "2026-03-02" to "Vistoria da câmara",
        "2026-05-20" to "Início dos acabamentos",
    ).mapIndexed { i, (d, t) -> EventoObra(i + 1L, LocalDate.parse(d).toEpochDay(), t) }

    val despesas: List<Despesa> = buildList {
        var id = 1L
        for (m in 0 until 10) {
            val dia = LocalDate.of(2025, 9, 25).plusMonths(m.toLong()).toEpochDay()
            add(Despesa(id++, RUBRICA_PRESTACAO, "", dia, valorCent = 61_000, capitalCent = 38_000 + m * 300L))
            add(Despesa(id++, "Luz", "", dia, valorCent = 4_200 + m * 150L))
            if (m % 3 == 0) add(Despesa(id++, "Comissões bancárias", "", dia, valorCent = 1_250))
        }
        add(Despesa(id, "Seguros", "Seguro multirriscos", LocalDate.of(2026, 1, 10).toEpochDay(), valorCent = 32_000))
    }

    val despesasResumo: ResumoDespesas by lazy { resumoDespesas(despesas) }

    val dados: DadosGraficos by lazy {
        calcularDadosGraficos(
            categorias = categorias,
            pagamentos = pagamentos,
            eventos = eventos,
            orcamentoTotal = 180_000.0,
            areaConstrucao = 210.0,
            hoje = hoje,
        )
    }
}
