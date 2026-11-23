package pt.jpduarte.obragastos.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uma instalação de raiz tem de abrir a base de dados e semear as categorias. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalacaoLimpaTest {

    @Test
    fun abreESemeia() = runBlocking {
        val db = ObraDatabase.get(ApplicationProvider.getApplicationContext())
        val dao = db.dao()
        assertEquals(8, dao.categorias().first().size)
        val d = dao.definicoes().first()
        assertNotNull(d)
        assertEquals(Definicoes.LIVRO_RECENTE, d!!.ordemLivro)
        db.close()
    }
}
