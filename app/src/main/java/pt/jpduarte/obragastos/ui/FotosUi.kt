package pt.jpduarte.obragastos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.jpduarte.obragastos.data.Fotos
import java.io.File

/** Lê a foto em segundo plano, já reduzida ao tamanho a que vai ser mostrada. */
@Composable
private fun rememberFoto(nome: String, ladoPx: Int): ImageBitmap? {
    val contexto = LocalContext.current
    val foto by produceState<ImageBitmap?>(null, nome, ladoPx) {
        value = withContext(Dispatchers.IO) { Fotos.carregar(contexto, nome, ladoPx)?.asImageBitmap() }
    }
    return foto
}

/** Uma miniatura quadrada, recortada ao centro. */
@Composable
fun Miniatura(nome: String, tamanho: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val px = with(LocalDensity.current) { tamanho.roundToPx() }
    val foto = rememberFoto(nome, px)
    Box(
        modifier
            .size(tamanho)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (foto != null) {
            Image(foto, contentDescription = "Foto", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * As fotos de um evento, uma de cada vez em ecrã inteiro; desliza-se para o
 * lado para passar à seguinte.
 */
@Composable
fun VisualizadorFotos(nomes: List<String>, inicial: Int, onFechar: () -> Unit) {
    val estado = rememberPagerState(initialPage = inicial) { nomes.size }
    val config = LocalConfiguration.current
    val ladoEcra = with(LocalDensity.current) { minOf(config.screenWidthDp, config.screenHeightDp).dp.roundToPx() }
    Dialog(onDismissRequest = onFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = estado, modifier = Modifier.fillMaxSize()) { pagina ->
                val foto = rememberFoto(nomes[pagina], ladoEcra)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (foto == null) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Image(foto, contentDescription = "Foto ${pagina + 1} de ${nomes.size}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            IconButton(onClick = onFechar, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
            }
            if (nomes.size > 1) {
                Text(
                    "${estado.currentPage + 1} / ${nomes.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
        }
    }
}

/**
 * A linha de fotos do diálogo do evento: as que já tem (com × para retirar) e
 * os botões para juntar da galeria ou tirar com a câmara. Cada foto é reduzida
 * ao entrar (ver [Fotos]); enquanto isso acontece aparece um indicador.
 */
@Composable
fun EditorFotos(
    vm: ObraViewModel,
    fotos: List<String>,
    aoJuntar: (String) -> Unit,
    aoRetirar: (String) -> Unit,
    aoVer: (Int) -> Unit,
) {
    val contexto = LocalContext.current
    val ambito = rememberCoroutineScope()
    var aProcessar by remember { mutableIntStateOf(0) }
    var falhou by remember { mutableStateOf(false) }

    fun importar(uri: Uri, depois: () -> Unit = {}) {
        aProcessar++
        ambito.launch {
            val nome = vm.importarFoto(uri)
            if (nome != null) aoJuntar(nome) else falhou = true
            aProcessar--
            depois()
        }
    }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        falhou = false
        uris.forEach { importar(it) }
    }

    // A câmara escreve para um ficheiro temporário; depois de reduzido, apaga-se.
    var temporario by remember { mutableStateOf<File?>(null) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = temporario ?: return@rememberLauncherForActivityResult
        if (ok) importar(Uri.fromFile(f)) { f.delete() } else f.delete()
    }

    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        fotos.forEachIndexed { i, nome ->
            Box {
                Miniatura(nome, 64.dp, onClick = { aoVer(i) })
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { aoRetirar(nome) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Retirar foto", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (aProcessar > 0) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
    if (fotos.isNotEmpty() || aProcessar > 0) Spacer(Modifier.size(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Galeria")
        }
        OutlinedButton(onClick = {
            val f = File(Fotos.pastaCamara(contexto), "camara-${System.currentTimeMillis()}.jpg")
            temporario = f
            camara.launch(FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos", f))
        }) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Câmara")
        }
    }
    if (falhou) {
        Text(
            "Não foi possível ler uma das imagens.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
