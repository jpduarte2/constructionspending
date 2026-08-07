package pt.jpduarte.obragastos.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private enum class Aba(val rota: String, val titulo: String, val icone: ImageVector) {
    Pagamentos("pagamentos", "Pagamentos", Icons.AutoMirrored.Outlined.ReceiptLong),
    Graficos("graficos", "Gráficos", Icons.Outlined.BarChart),
    Livro("livro", "Livro", Icons.Outlined.MenuBook),
    Categorias("categorias", "Categorias", Icons.Outlined.Category),
}

private const val ROTA_FORM = "pagamento?id={id}"

private fun rotaForm(id: Long = 0L) = "pagamento?id=$id"

@Composable
fun ObraApp(vm: ObraViewModel) {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route

    Scaffold(
        bottomBar = {
            // A barra só aparece nos destinos principais; o formulário é modal.
            if (Aba.entries.any { it.rota == rotaAtual }) {
                NavigationBar {
                    Aba.entries.forEach { aba ->
                        NavigationBarItem(
                            selected = rotaAtual == aba.rota,
                            onClick = {
                                if (rotaAtual != aba.rota) {
                                    nav.navigate(aba.rota) {
                                        popUpTo(Aba.Pagamentos.rota) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Icon(aba.icone, contentDescription = null) },
                            label = { Text(aba.titulo) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Aba.Pagamentos.rota) {

            composable(Aba.Pagamentos.rota) {
                PagamentosScreen(
                    vm = vm,
                    padding = padding,
                    onNovo = { nav.navigate(rotaForm()) },
                    onEditar = { id -> nav.navigate(rotaForm(id)) },
                )
            }

            composable(Aba.Graficos.rota) {
                GraficosScreen(vm = vm, padding = padding)
            }

            composable(Aba.Livro.rota) {
                LivroScreen(vm = vm, padding = padding)
            }

            composable(Aba.Categorias.rota) {
                CategoriasScreen(vm = vm, padding = padding)
            }

            composable(
                route = ROTA_FORM,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { backStack ->
                PagamentoFormScreen(
                    vm = vm,
                    pagamentoId = backStack.arguments?.getLong("id") ?: 0L,
                    onSair = { nav.popBackStack() },
                )
            }
        }
    }
}
