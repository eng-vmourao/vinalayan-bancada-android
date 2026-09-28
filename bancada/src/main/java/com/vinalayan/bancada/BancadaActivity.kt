package com.vinalayan.bancada

import android.app.PictureInPictureParams
import android.content.Intent
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vinalayan.bancada.protocol.DashButtons
import com.vinalayan.bancada.protocol.PanelEngine
import com.vinalayan.bancada.protocol.PanelLink
import com.vinalayan.bancada.protocol.PanelSnapshot
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Timer
import java.util.TimerTask

class BancadaViewModel : ViewModel() {
    val engine = PanelEngine()
    val decoder = H264Decoder()
    val snap = mutableStateOf(engine.snapshot())
    val logText = mutableStateOf("")
    val bike = mutableStateOf(BikeSim())
    val page = mutableStateOf(ClusterPage.ANALOG)
    private val timer = Timer("bancada-ui", true)

    init {
        engine.onAccessUnit = { nal, marker -> decoder.onNal(nal, marker) }
        engine.onState = { snap.value = it }
        engine.start()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                snap.value = engine.snapshot()
                logText.value = engine.recentLog().takeLast(80).joinToString("\n")
            }
        }, 200, 250)
    }

    override fun onCleared() {
        timer.cancel()
        decoder.release()
        engine.close()
    }
}

class BancadaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0C0D10))) {
                BancadaScreen(
                    onPip = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val params = PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(1, 1))
                                .build()
                            enterPictureInPictureMode(params)
                        }
                    },
                    onShare = { text ->
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }, "Exportar log"))
                    },
                )
            }
        }
    }
}

@Composable
private fun BancadaScreen(
    vm: BancadaViewModel = viewModel(),
    onPip: () -> Unit,
    onShare: (String) -> Unit,
) {
    val snap by vm.snap
    val bike by vm.bike
    val page by vm.page
    val log by vm.logText
    var tab by remember { mutableIntStateOf(0) }
    val ips = remember { localIpv4() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text("Bancada Himalayan 450", color = Color.White, fontSize = 20.sp)
        Text(
            "Painel em :${snap.ctrlPort}  vídeo :${snap.rtpPort}   ${linkPt(snap.link)}",
            color = Color(0xFFB7B1A6),
            fontSize = 13.sp,
        )
        Text(
            "Um aparelho: no Vinalayan Lab use 127.0.0.1. Dois aparelhos: ${ips.joinToString().ifBlank { "sem IPv4" }}",
            color = Color(0xFF8E8A82),
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf("Painel", "Controles", "Diagnóstico").forEachIndexed { i, name ->
                Button(
                    onClick = { tab = i },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tab == i) Color(0xFFE7E1D4) else Color(0xFF2A2C31),
                        contentColor = if (tab == i) Color.Black else Color.White,
                    ),
                ) { Text(name) }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onPip) { Text("PiP") }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> PanelTab(vm, bike, page, snap)
            1 -> ControlsTab(vm, bike)
            else -> DiagTab(vm, snap, log, onShare)
        }
    }
}

@Composable
private fun PanelTab(vm: BancadaViewModel, bike: BikeSim, page: ClusterPage, snap: PanelSnapshot) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(360.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(340.dp)) {
            ClusterCanvas(bike, page, snap, Modifier.fillMaxSize())
            if (page == ClusterPage.DIGITAL && bike.gear >= 0) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    vm.decoder.attach(Surface(st))
                                }
                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    vm.decoder.detach()
                                    return true
                                }
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = 48.dp, top = 24.dp)
                        .size(150.dp)
                        .clip(CircleShape),
                )
            }
        }
    }
    Text(
        "Quadros ${snap.videoFrames}   ${"%.1f".format(snap.videoFps)} fps   decoder ${vm.decoder.framesOut.get()}",
        color = Color(0xFFB7B1A6),
        fontSize = 12.sp,
    )
    if (vm.decoder.error.get() != null) {
        Text("MediaCodec: ${vm.decoder.error.get()}", color = Color(0xFFE23B2F), fontSize = 12.sp)
    }
    val chipScroll = rememberScrollState()
    Row(Modifier.fillMaxWidth().horizontalScroll(chipScroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ClusterPage.entries.forEach { p ->
            Button(
                onClick = { vm.page.value = p },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (page == p) Color(0xFFE7E1D4) else Color(0xFF2A2C31),
                    contentColor = if (page == p) Color.Black else Color.White,
                ),
            ) { Text(p.label, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ControlsTab(vm: BancadaViewModel, bike: BikeSim) {
    fun edit(block: (BikeSim) -> BikeSim) { vm.bike.value = block(vm.bike.value) }
    Text("Simulação da moto — não vem do protocolo", color = Color(0xFF8E8A82), fontSize = 12.sp)
    SliderRow("Velocidade ${bike.speed} km/h", bike.speed / 160f) { edit { b -> b.copy(speed = (it * 160).toInt()) } }
    SliderRow("Rotação ${bike.rpm} rpm", bike.rpm / 9000f) { edit { b -> b.copy(rpm = (it * 9000).toInt()) } }
    SliderRow("Marcha ${if (bike.gear == 0) "N" else bike.gear}", bike.gear / 6f) { edit { b -> b.copy(gear = (it * 6).toInt()) } }
    SliderRow("Combustível ${bike.fuelBars}/8", bike.fuelBars / 8f) { edit { b -> b.copy(fuelBars = (it * 8).toInt()) } }
    SliderRow("Temperatura ${bike.tempC}°C", (bike.tempC - 40) / 100f) {
        edit { b -> b.copy(tempC = 40 + (it * 100).toInt()) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { edit { it.copy(day = !it.day) } }) { Text(if (bike.day) "Tema claro" else "Tema escuro") }
        Button(onClick = {
            val next = RideMode.entries[(bike.mode.ordinal + 1) % RideMode.entries.size]
            edit { it.copy(mode = next) }
        }) { Text(if (bike.mode.rearAbsOff) "${bike.mode.label} ABS traseiro off" else "${bike.mode.label} ABS") }
    }
    Text("Luzes", color = Color.White, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Toggle("Facho", bike.highBeam) { edit { it.copy(highBeam = !it.highBeam) } }
        Toggle("Seta E", bike.leftTurn) { edit { it.copy(leftTurn = !it.leftTurn) } }
        Toggle("Seta D", bike.rightTurn) { edit { it.copy(rightTurn = !it.rightTurn) } }
        Toggle("ABS", bike.absWarn) { edit { it.copy(absWarn = !it.absWarn) } }
        Toggle("Motor", bike.engineWarn) { edit { it.copy(engineWarn = !it.engineWarn) } }
        Toggle("Cavalete", bike.sideStand) { edit { it.copy(sideStand = !it.sideStand) } }
    }
    Text("Ignição e enlace", color = Color.White, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.engine.setIgnition(true) }) { Text("Ignição ON") }
        Button(onClick = { vm.engine.setIgnition(false) }) { Text("Ignição OFF") }
        Button(onClick = { vm.engine.setLinkUp(false) }) { Text("Perder link") }
        Button(onClick = { vm.engine.setLinkUp(true) }) { Text("Restaurar") }
    }
    Text(
        "Joystick do guidão — bytes que o Vinalayan trata (DashViewModel.kt:179-184). Play, pausa e volume não têm código nesse arquivo e não são enviados.",
        color = Color(0xFF8E8A82),
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Joy("Esq 14", DashButtons.ZOOM_IN, vm)
        Joy("Dir 13", DashButtons.ZOOM_OUT, vm)
        Joy("Baixo 15", DashButtons.DOWN, vm)
        Joy("Click 18", DashButtons.CLICK, vm)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        Joy("Próxima 09", DashButtons.MEDIA_NEXT, vm)
        Joy("Anterior 0A", DashButtons.MEDIA_PREVIOUS, vm)
        Joy("Atender 06", DashButtons.CALL_ANSWER, vm)
        Joy("Recusar 07", DashButtons.CALL_REJECT, vm)
    }
}

@Composable
private fun DiagTab(vm: BancadaViewModel, snap: PanelSnapshot, log: String, onShare: (String) -> Unit) {
    Text("Telefone ${snap.phoneAddress ?: "—"}   SSID ${snap.ssid ?: "—"}   host ${snap.hostname ?: "—"}", color = Color.White, fontSize = 13.sp)
    Text("RX ${snap.packetsIn}   TX ${snap.packetsOut}   erro ${snap.lastError ?: "—"}", color = Color(0xFFB7B1A6), fontSize = 13.sp)
    Button(onClick = { onShare(vm.engine.exportLog()) }, modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Exportar log")
    }
    Text(log.ifBlank { "aguardando pacotes" }, color = Color(0xFFD5D0C6), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Text(label, color = Color.White, fontSize = 13.sp)
    Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange)
}

@Composable
private fun Toggle(label: String, on: Boolean, click: () -> Unit) {
    Button(
        onClick = click,
        colors = ButtonDefaults.buttonColors(containerColor = if (on) Color(0xFFE7E1D4) else Color(0xFF2A2C31), contentColor = if (on) Color.Black else Color.White),
    ) { Text(label, fontSize = 12.sp) }
}

@Composable
private fun Joy(label: String, code: Int, vm: BancadaViewModel) {
    Button(onClick = { vm.engine.sendButton(code) }) { Text(label, fontSize = 12.sp) }
}

private fun linkPt(link: PanelLink) = when (link) {
    PanelLink.WAITING -> "aguardando"
    PanelLink.AUTHENTICATING -> "autenticando"
    PanelLink.AUTHENTICATED -> "autenticado"
    PanelLink.PROJECTING -> "projetando"
    PanelLink.IGNITION_OFF -> "ignição off"
    PanelLink.LINK_DOWN -> "sem link"
}

private fun localIpv4(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().flatMap { nif ->
        nif.inetAddresses.toList().mapNotNull { addr ->
            if (!addr.isLoopbackAddress && addr is Inet4Address) addr.hostAddress else null
        }
    }
}.getOrDefault(emptyList())
