package pt.jpduarte.obragastos.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import pt.jpduarte.obragastos.data.Despesa
import pt.jpduarte.obragastos.data.Pagamento

/**
 * Depois de apagar um pagamento ou uma despesa, o ecrã da lista mostra
 * "Desfazer" em vez de se ter pedido confirmação antes — é mais rápido no caso
 * normal e salva o engano, que é o caso que importa.
 */
@Composable
fun SnackbarDesfazer(vm: ObraViewModel, estado: SnackbarHostState) {
    val apagado by vm.apagado.collectAsState()
    LaunchedEffect(apagado) {
        val texto = when (apagado) {
            is Pagamento -> "Pagamento eliminado"
            is Despesa -> "Despesa eliminada"
            else -> return@LaunchedEffect
        }
        val r = estado.showSnackbar(texto, actionLabel = "Desfazer", duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) vm.desfazerApagar() else vm.esquecerApagado()
    }
}
