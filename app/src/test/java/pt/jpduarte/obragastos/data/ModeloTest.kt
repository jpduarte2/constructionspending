package pt.jpduarte.obragastos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** As contas do IVA, que são o centro da app. */
class ModeloTest {

    private fun pag(valor: Long, base: Long = valor, taxa: Double = 23.0, pago: Boolean = true) = Pagamento(
        categoriaId = 1, descricao = "", dataEpochDay = 0,
        valorBaseCent = valor, baseTributavelCent = base, taxaIva = taxa, ivaPago = pago,
    )

    @Test
    fun ivaPagoSobreTudo() {
        val p = pag(100_000)
        assertEquals(23_000L, p.ivaCent)
        assertEquals(123_000L, p.totalPagoCent)
        assertEquals(0L, p.ivaPoupadoCent)
        assertFalse(p.ivaParcial)
    }

    @Test
    fun ivaNaoPagoContaComoPoupado() {
        val p = pag(10_000, pago = false)
        assertEquals(10_000L, p.totalPagoCent)
        assertEquals(2_300L, p.ivaPoupadoCent)
    }

    /** O exemplo do README: 6000 € com IVA só sobre 1500 €. */
    @Test
    fun ivaParcial() {
        val p = pag(600_000, base = 150_000)
        assertTrue(p.ivaParcial)
        assertEquals(34_500L, p.ivaCent)
        assertEquals(634_500L, p.totalPagoCent)
        assertEquals(103_500L, p.ivaPoupadoCent)
    }

    @Test
    fun ivaArredondaAoCentimoPorFatura() {
        // 0,50 € × 23% = 0,115 € → 0,12 € (meio cêntimo arredonda para cima)
        assertEquals(12L, pag(50).ivaCent)
        // 1234,56 × 23% = 283,9488 → 283,95
        assertEquals(28_395L, pag(123_456).ivaCent)
        // taxa de 6%
        assertEquals(741L, pag(12_345, taxa = 6.0).ivaCent)
    }

    @Test
    fun conversaoDeEuros() {
        assertEquals(10_010L, centimos(100.1))
        assertEquals(123_456L, centimos(1234.56))
        assertEquals(0L, centimos(0.0))
        assertEquals(1234.56, 123_456L.emEuros(), 0.0)
    }

    @Test
    fun despesaSemCapitalEhTodaCusto() {
        val d = Despesa(rubrica = "Luz", dataEpochDay = 0, valorCent = 4_500)
        assertEquals(4_500L, d.custoRealCent)
        val prest = Despesa(rubrica = RUBRICA_PRESTACAO, dataEpochDay = 0, valorCent = 61_000, capitalCent = 38_000)
        assertEquals(23_000L, prest.custoRealCent)
    }

    @Test
    fun fotosDoEvento() {
        val e = EventoObra(dataEpochDay = 0, titulo = "x", fotos = juntarFotos(listOf("a.jpg", "b.jpg")))
        assertEquals(listOf("a.jpg", "b.jpg"), e.listaFotos)
        assertEquals(emptyList<String>(), EventoObra(dataEpochDay = 0, titulo = "x").listaFotos)
    }
}
