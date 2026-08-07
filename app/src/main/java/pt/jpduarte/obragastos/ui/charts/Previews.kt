package pt.jpduarte.obragastos.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.jpduarte.obragastos.ui.theme.LocalViz
import pt.jpduarte.obragastos.ui.theme.ObraTheme

/*
 * Pré-visualizações para o painel do Android Studio. Servem para mexer nos
 * gráficos sem arrancar o emulador — os dados abaixo são fictícios.
 *
 * Cada gráfico aparece em claro e em escuro, porque os dois modos usam passos
 * de cor diferentes e um só se valida a olho lado a lado.
 */

private val GASTOS = listOf(
    Fatia("Pedreiro", 14000.0),
    Fatia("Materiais", 13683.0),
    Fatia("Carpinteiro", 8600.0),
    Fatia("Eletricista", 5276.0),
    Fatia("Projetos e licenças", 4305.0),
    Fatia("Canalizador", 2900.0),
    Fatia("Pintor", 2600.0),
    Fatia("Outros", 554.0),
)

private val MESES = listOf(
    Fatia("Fev.", 12305.0),
    Fatia("Mar.", 11366.0),
    Fatia("Abr.", 10282.5),
    Fatia("Mai.", 7361.0),
    Fatia("Jun.", 5281.4),
    Fatia("Jul.", 5721.5),
)

@Composable
private fun Moldura(titulo: String, conteudo: @Composable () -> Unit) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Text(
            titulo,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        conteudo()
    }
}

/* ------------------------------- claro ------------------------------- */

@Preview(name = "Especialidades · claro", showBackground = true, widthDp = 380)
@Composable
private fun PreviewBarras() = ObraTheme(escuro = false) {
    Moldura("Gastos por especialidade") { BarrasHorizontais(GASTOS) }
}

@Preview(name = "Meses · claro", showBackground = true, widthDp = 380)
@Composable
private fun PreviewColunas() = ObraTheme(escuro = false) {
    Moldura("Gastos por mês") { ColunasMensais(MESES) }
}

@Preview(name = "Donut · claro", showBackground = true, widthDp = 380)
@Composable
private fun PreviewDonut() = ObraTheme(escuro = false) {
    Moldura("Peso de cada especialidade") { Donut(GASTOS, textoCentro = "total") }
}

@Preview(name = "IVA · claro", showBackground = true, widthDp = 380)
@Composable
private fun PreviewIva() = ObraTheme(escuro = false) {
    val viz = LocalViz.current
    Moldura("IVA pago vs IVA poupado") {
        BarraEmpilhada(
            listOf(
                Fatia("IVA pago", 3487.0, viz.series[0]),
                Fatia("IVA poupado", 7337.0, viz.series[1]),
            ),
        )
        Spacer(Modifier.height(12.dp))
        Legenda(
            listOf(
                "IVA pago · 3 487 €" to viz.series[0],
                "IVA poupado · 7 337 €" to viz.series[1],
            ),
        )
    }
}

@Preview(name = "Medidores · claro", showBackground = true, widthDp = 380)
@Composable
private fun PreviewMedidores() = ObraTheme(escuro = false) {
    Moldura("Orçamento — normal, aviso, excedido") {
        Column(Modifier.fillMaxWidth()) {
            Medidor(gasto = 2900.0, limite = 3500.0)   // 83% — normal
            Spacer(Modifier.height(12.dp))
            Medidor(gasto = 14000.0, limite = 15000.0) // 93% — aviso
            Spacer(Modifier.height(12.dp))
            Medidor(gasto = 13683.0, limite = 12000.0) // excedido
        }
    }
}

/* ------------------------------- escuro ------------------------------- */

@Preview(name = "Especialidades · escuro", showBackground = true, widthDp = 380)
@Composable
private fun PreviewBarrasEscuro() = ObraTheme(escuro = true) {
    Moldura("Gastos por especialidade") { BarrasHorizontais(GASTOS) }
}

@Preview(name = "Meses · escuro", showBackground = true, widthDp = 380)
@Composable
private fun PreviewColunasEscuro() = ObraTheme(escuro = true) {
    Moldura("Gastos por mês") { ColunasMensais(MESES) }
}

@Preview(name = "Donut · escuro", showBackground = true, widthDp = 380)
@Composable
private fun PreviewDonutEscuro() = ObraTheme(escuro = true) {
    Moldura("Peso de cada especialidade") { Donut(GASTOS, textoCentro = "total") }
}

@Preview(name = "IVA · escuro", showBackground = true, widthDp = 380)
@Composable
private fun PreviewIvaEscuro() = ObraTheme(escuro = true) {
    val viz = LocalViz.current
    Moldura("IVA pago vs IVA poupado") {
        BarraEmpilhada(
            listOf(
                Fatia("IVA pago", 3487.0, viz.series[0]),
                Fatia("IVA poupado", 7337.0, viz.series[1]),
            ),
        )
        Spacer(Modifier.height(12.dp))
        Legenda(
            listOf(
                "IVA pago · 3 487 €" to viz.series[0],
                "IVA poupado · 7 337 €" to viz.series[1],
            ),
        )
    }
}

@Preview(name = "Medidores · escuro", showBackground = true, widthDp = 380)
@Composable
private fun PreviewMedidoresEscuro() = ObraTheme(escuro = true) {
    Moldura("Orçamento — normal, aviso, excedido") {
        Column(Modifier.fillMaxWidth()) {
            Medidor(gasto = 2900.0, limite = 3500.0)
            Spacer(Modifier.height(12.dp))
            Medidor(gasto = 14000.0, limite = 15000.0)
            Spacer(Modifier.height(12.dp))
            Medidor(gasto = 13683.0, limite = 12000.0)
        }
    }
}
