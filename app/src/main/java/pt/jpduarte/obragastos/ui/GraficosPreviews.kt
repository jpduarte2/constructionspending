package pt.jpduarte.obragastos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.jpduarte.obragastos.ui.theme.ObraTheme

/*
 * Pré-visualizações do ecrã de gráficos para o painel do Android Studio, com a
 * obra inventada de [Exemplo]. Usam as mesmas margens e o mesmo fundo do ecrã
 * verdadeiro, para se ver a largura de cada cartão face à do telemóvel.
 */

/** Os blocos do topo do ecrã, pela ordem de fábrica, e o da origem dos fundos. */
private val BLOCOS_PREVIEW = listOf("resumo", "orcamento", "comparacao_mensal", "custo_m2", "origem_fundos")

@Composable
private fun EcraGraficosPreview() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BLOCOS_PREVIEW.mapNotNull { definicaoDe(it) }.forEach { bloco ->
            bloco.conteudo(Exemplo.dados, AcoesGraficos.NENHUMA)
        }
    }
}

@Preview(name = "Ecrã Gráficos · claro", showBackground = true, widthDp = 380, heightDp = 1500)
@Composable
private fun PreviewEcraGraficos() = ObraTheme(escuro = false) { EcraGraficosPreview() }

@Preview(name = "Ecrã Gráficos · escuro", showBackground = true, widthDp = 380, heightDp = 1500)
@Composable
private fun PreviewEcraGraficosEscuro() = ObraTheme(escuro = true) { EcraGraficosPreview() }
