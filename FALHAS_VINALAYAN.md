# Falhas e limites observados no Vinalayan

Nada aqui foi corrigido. Nada aqui foi reproduzido numa moto ou num celular;
a leitura é do código em `1f4c49b`.

## 1. Play, pausa e volume não têm byte

`DashViewModel.kt:179-184` só nomeia atender `0x06`, recusar `0x07`,
zoom `0x14`/`0x13`, próxima `0x09` e anterior `0x0A`.

Como reproduzir a leitura: abrir esse trecho e procurar `volume`, `pause` ou
`play`. Não há. O painel da bancada deixa esses controles de fora de propósito.
O Spotify, se a notificação chegar ao app, ainda pode mostrar título e artista
pelo TLV `05 0D` (`DashCommands.kt:266-274`), mas o painel não consegue mandar
play/pausa de volta.

## 2. Título e artista só saem depois que o vídeo começa

`DashSession.launchMediaInfo` (`DashSession.kt:400-416`) exige
`DashState.STREAMING`. Autenticar não basta.

Como reproduzir no código: seguir `startStreaming` (`DashSession.kt:118-126`),
que é quem chama `launchMediaInfo`. Antes disso `nowPlaying` não é enviado.

## 3. O workflow de fumaça pede o APK 0.1.4 e o de build gera 0.1.5

- `.github/workflows/android-smoke.yml` linha 47: `Vinalayan-0.1.4-preview-universal.apk`
- `.github/workflows/android-apk.yml` linha 75: `Vinalayan-0.1.5-preview-$abi.apk`

Como reproduzir: comparar os dois caminhos. O job de fumaça, se rodar em cima
de um build 0.1.5, não acha o arquivo. Este fork não executou esse workflow.

## 4. Portas e IP cravados

`DashSocket.kt:27-31` fixa `192.168.1.1`, broadcast `192.168.1.255` e as portas
2000, 2002 e 5000, e o construtor faz bind na 2000 (`DashSocket.kt:53`).
Dois processos no mesmo aparelho não podem os dois escutar a 2000.
O flavor `lab` contorna isso sem mudar esses constantes. O flavor `local`
continua igual, coberto por `LabModeContractTest`.

## 5. O workflow de APK pede o pacote SDK `tools`, que o Google tirou do ar

O pin `android-actions/setup-android@9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407`
instala `tools` por padrão. Em 28 de setembro de 2026 o `sdkmanager` responde
`Failed to find package 'tools'` e o job morre em cerca de 10 s. O run que
mostrou isso neste fork é
<https://github.com/eng-vmourao/vinalayan-bancada-android/actions/runs/36400444976>.

O contorno usado aqui, só no YAML, é `packages: platform-tools`. O código do
app não mudou por causa disso.

