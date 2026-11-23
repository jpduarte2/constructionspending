package pt.jpduarte.obragastos.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
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
    Despesas("despesas", "Despesas", Icons.Outlined.AccountBalance),
}

private const val ROTA_FORM = "pagamento?id={id}"
private const val ROTA_DESPESA = "despesa?id={id}&copiaDe={copiaDe}"
private const val ROTA_ESCOLHER_GRAFICOS = "graficos/escolher"
private const val ROTA_GRAFICOS_DESPESAS = "despesas/graficos?rubrica={rubrica}"
private const val ROTA_FIXAS = "despesas/fixas"

private fun rotaForm(id: Long = 0L) = "pagamento?id=$id"
private fun rotaDespesa(id: Long = 0L, copiaDe: Long = 0L) = "despesa?id=$id&copiaDe=$copiaDe"

@Composable
fun ObraApp(vm: ObraViewModel) {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route

    Scaffold(
        bottomBar = {
            // A barra só aparece nos destinos principais; os formulários são modais.
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
                            // Cinco separadores num telemóvel estreito: o rótulo
                            // nunca passa para duas linhas.
                            label = {
                                Text(
                                    aba.titulo,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
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
                    onEscolherGraficos = { nav.navigate(ROTA_ESCOLHER_GRAFICOS) },
                )
            }

            composable(Aba.Despesas.rota) {
                DespesasScreen(
                    vm = vm,
                    padding = padding,
                    onNova = { nav.navigate(rotaDespesa()) },
                    onEditar = { id -> nav.navigate(rotaDespesa(id)) },
                    onVerGraficos = { rubrica ->
                        nav.navigate("despesas/graficos?rubrica=${Uri.encode(rubrica.orEmpty())}")
                    },
                    onFixas = { nav.navigate(ROTA_FIXAS) },
                )
            }

            composable(ROTA_FIXAS) {
                DespesasFixasScreen(
                    vm = vm,
                    onSair = { nav.popBackStack() },
                    onNova = { nav.navigate(rotaDespesa()) },
                )
            }

            composable(
                route = ROTA_GRAFICOS_DESPESAS,
                arguments = listOf(
                    navArgument("rubrica") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStack ->
                DespesasGraficosScreen(
                    vm = vm,
                    rubricaInicial = backStack.arguments?.getString("rubrica").orEmpty().ifBlank { null },
                    onSair = { nav.popBackStack() },
                )
            }

            composable(Aba.Graficos.rota) {
                GraficosScreen(
                    vm = vm,
                    padding = padding,
                    onEscolher = { nav.navigate(ROTA_ESCOLHER_GRAFICOS) },
                )
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

            composable(
                route = ROTA_DESPESA,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("copiaDe") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { backStack ->
                DespesaFormScreen(
                    vm = vm,
                    despesaId = backStack.arguments?.getLong("id") ?: 0L,
                    copiaDe = backStack.arguments?.getLong("copiaDe") ?: 0L,
                    onSair = { nav.popBackStack() },
                    // A cópia substitui o formulário atual, para "voltar" ir à lista.
                    onRepetir = { id ->
                        nav.navigate(rotaDespesa(copiaDe = id)) {
                            popUpTo(ROTA_DESPESA) { inclusive = true }
                        }
                    },
                )
            }

            composable(ROTA_ESCOLHER_GRAFICOS) {
                EscolherGraficosScreen(vm = vm, onSair = { nav.popBackStack() })
            }
        }
    }
}
