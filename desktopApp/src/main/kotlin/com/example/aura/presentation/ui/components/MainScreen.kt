package com.example.aura.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aura.desktopapp.generated.resources.Res
import aura.desktopapp.generated.resources.add_to_queue_svgrepo_com
import org.jetbrains.compose.resources.painterResource

@Composable
fun MainScreen(modifier: Modifier = Modifier,pahtActual: MutableState<String>) {
    val scrollState = rememberScrollState()
    var textButtom: String = ""
    if (pahtActual.value == "image") {
        textButtom = "imagen"
    }
    if (pahtActual.value == "video") {
        textButtom = "video"
    }
    Row(
        modifier = modifier
            .fillMaxSize() // 2. Cambiamos fillMaxHeight(0.9f) por fillMaxSize()
            // Quitamos el padding(16.dp) de aquí si ya le pusiste padding a la pantalla padre,
            // o lo dejas si quieres un doble margen. Lo dejaré por ahora.
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // PANEL PRINCIPAL (Izquierda - 70% del ancho)
        Column(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight() // Llena la altura total de la fila
                .background(Color(0xFF222222), shape = RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Espacio superior para el contenido futuro
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp)
            ){
                FlowRow(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ){
                    repeat(10){index ->
                        CardMultimedia()
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .background(Color.Gray, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    adapter = rememberScrollbarAdapter(scrollState = scrollState),
                )
            }
            // BOTÓN DE IMPORTAR IMAGEN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF333333))
                    .clickable { /* Acción para abrir el selector de archivos */ }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(Res.drawable.add_to_queue_svgrepo_com),
                    contentDescription = "Icono de agregar a la cola",
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Color.White)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Importar $textButtom",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // PANEL LATERAL (Derecha - 30% del ancho)
        Column(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight() // Llena la altura total de la fila
        ) {
            // Tarjeta de Imagen Actual
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF222222), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Imagen actual",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Contenedor cuadrado para mostrar la imagen seleccionada
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f) // Obliga a la caja a ser un cuadrado perfecto
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Ícono temporal mientras no hay imagen cargada
                    Image(
                        painter = painterResource(Res.drawable.add_to_queue_svgrepo_com),
                        contentDescription = "Previsualización vacía",
                        modifier = Modifier.size(64.dp),
                        colorFilter = ColorFilter.tint(Color.DarkGray)
                    )
                }
            }
        }
    }
}