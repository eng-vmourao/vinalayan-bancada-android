package com.example.opendash.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opendash.BuildConfig
import com.example.opendash.dash.LabEndpointStore
import com.example.opendash.ui.theme.TextHi
import com.example.opendash.ui.theme.TextMid

@Composable
fun LabEndpointCard() {
    if (!BuildConfig.LAB_MODE) return
    val context = LocalContext.current
    val initial = remember { LabEndpointStore.read(context) }
    var mode by remember { mutableStateOf(initial.mode) }
    var host by remember { mutableStateOf(initial.host) }
    var ssid by remember { mutableStateOf(initial.ssid) }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text("Laboratório — não é a moto", color = TextHi, fontSize = 14.sp)
        Text(
            "Um aparelho fala com 127.0.0.1 e não ocupa a porta 2000. Dois aparelhos: IP do painel na mesma Wi-Fi. A rede RE_ não é exigida.",
            color = TextMid,
            fontSize = 12.sp,
        )
        Row {
            TextButton(onClick = {
                mode = LabEndpointStore.MODE_SAME
                LabEndpointStore.write(context, LabEndpointStore.LabTarget(mode, host, ssid))
            }) { Text(if (mode == LabEndpointStore.MODE_SAME) "● Um aparelho" else "Um aparelho") }
            TextButton(onClick = {
                mode = LabEndpointStore.MODE_TWO
                LabEndpointStore.write(context, LabEndpointStore.LabTarget(mode, host, ssid))
            }) { Text(if (mode == LabEndpointStore.MODE_TWO) "● Dois aparelhos" else "Dois aparelhos") }
        }
        if (mode == LabEndpointStore.MODE_TWO) {
            OutlinedTextField(
                value = host,
                onValueChange = {
                    host = it
                    LabEndpointStore.write(context, LabEndpointStore.LabTarget(mode, host, ssid))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IP do painel") },
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = ssid,
            onValueChange = {
                ssid = it
                LabEndpointStore.write(context, LabEndpointStore.LabTarget(mode, host, ssid))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("SSID enviado na autenticação") },
            singleLine = true,
        )
    }
}
