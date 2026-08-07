package pt.jpduarte.obragastos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.jpduarte.obragastos.data.Definicoes
import pt.jpduarte.obragastos.ui.EcraAbertura
import pt.jpduarte.obragastos.ui.ObraApp
import pt.jpduarte.obragastos.ui.ObraViewModel
import pt.jpduarte.obragastos.ui.theme.ObraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Criado fora do tema, porque é ele que guarda a preferência de tema.
            // Continua no âmbito da Activity, partilhado por todos os destinos.
            val vm: ObraViewModel = viewModel()

            val tema by vm.tema.collectAsState()
            val doSistema = isSystemInDarkTheme()
            val escuro = when (tema) {
                Definicoes.TEMA_CLARO -> false
                Definicoes.TEMA_ESCURO -> true
                else -> doSistema
            }

            // rememberSaveable: rodar o ecrã não repete a abertura.
            var aAbrir by rememberSaveable { mutableStateOf(true) }

            ObraTheme(escuro = escuro) {
                Crossfade(targetState = aAbrir, animationSpec = tween(400), label = "abertura") {
                    if (it) EcraAbertura(onTerminar = { aAbrir = false }) else ObraApp(vm)
                }
            }
        }
    }
}
