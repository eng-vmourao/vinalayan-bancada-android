# Bancada Vinalayan para Android

Painel Tripper simulado e flavor de laboratório do [Vinalayan](https://github.com/eng-vmourao/Vinalayan), para testar o app sem a Himalayan. O repositório original não é alterado. O texto do Vinalayan de origem está em [docs/README_VINALAYAN.md](docs/README_VINALAYAN.md).

Dois APKs saem do workflow [bancada-ci.yml](.github/workflows/bancada-ci.yml), na Release `bancada-<número do run>`:

| APK | Pacote | Função |
|---|---|---|
| `Bancada-painel.apk` | `com.vinalayan.bancada` | O painel da direita. Escuta UDP 2000 (controle) e 5000 (vídeo). |
| `Vinalayan-lab.apk` | `com.vinalayan.lab` | O celular da esquerda, com endereço configurável e sem exigir a rede `RE_`. |

Os dois são assinados com a chave pública de laboratório em `lab-signing/lab.p12` (alias `bancada`, senha `bancada-lab`). Não é a chave de release da moto. O Android vai avisar que o pacote não veio da Play Store. Android 7.0 ou mais novo. O APK do painel e o do Vinalayan Lab saem do build type `release`, mas o flavor `lab` desliga o R8 — o shrink continua só em `local`, `play` e `mapboxTest`.

O flavor `local` (e `play` / `mapboxTest`) continua com `LAB_MODE` falso e com os endereços da moto: `192.168.1.1`, broadcast `192.168.1.255`, portas 2000, 2002 e 5000. O workflow confere com `git diff` que `dash/protocol`, `dash/video`, `DashAuth.kt`, `DashConfig.kt`, `DashWifiManager.kt` e `DashKeepAliveService.kt` não mudaram em relação a `1f4c49b`. O diff do que o laboratório mudou vai junto na Release, em `DIFF_LAB.txt`.

## Instalar pelo celular

1. Abra a Release mais recente deste repositório no GitHub, no navegador do aparelho.
2. Baixe `Bancada-painel.apk` e `Vinalayan-lab.apk`.
3. Abra cada arquivo e confirme a instalação. Se o Android bloquear, permita a instalação por esse navegador ou gerenciador de arquivos.
4. Não é preciso digitar a senha da chave. Ela só serve para quem for verificar a assinatura.

## Um aparelho

O painel ocupa as portas 2000 e 5000. O Vinalayan Lab, neste modo, envia o controle por uma porta efêmera para `127.0.0.1:2000` e continua escutando a resposta na 2002. Isso evita os dois processos brigarem pelo bind na 2000.

1. Abra **Bancada Himalayan** e deixe na aba Painel. A ignição precisa estar ligada (é o padrão).
2. Abra **Vinalayan Lab**. No topo da tela Dash, escolha **Um aparelho**. O SSID pode ficar `LAB_DASH`.
3. Toque em conectar, se o app não conectar sozinho. A rede `RE_` não é pedida.
4. Use tela dividida ou o botão **PiP** do painel para ver os dois.
5. Quando a autenticação fecha, o Vinalayan entra em projeção sozinho (`DashState.READY` chama `startStream`). O diagnóstico do painel deve ir de aguardando para autenticado e depois projetando. O vídeo, se o encoder do celular produzir quadros, aparece no modo Digital.

Este modo **não foi executado num celular** neste trabalho. O que passou foi o teste JVM `fakePhoneAuthenticatesAndSendsVideo`, no Linux, com sockets em `127.0.0.1`.

## Dois aparelhos

Os dois na mesma Wi-Fi ou no mesmo hotspot. O painel mostra o IPv4 dele na tela inicial.

1. No aparelho do painel, abra **Bancada Himalayan**.
2. No aparelho do Vinalayan Lab, escolha **Dois aparelhos** e digite esse IP. O SSID pode ficar `LAB_DASH`.
3. Conecte. O lab envia unicast para esse IP na porta 2000 (não para `192.168.1.255`, porque o hotspot do Android não é a rede da moto).

Este modo **também não foi executado**. Nenhum dos dois modos foi o que "funcionou no aparelho": o único enlace comprovado é o teste de protocolo no CI/JVM.

## Spotify

O teste real pedido é o Spotify oficial. Ele **não rodou** aqui.

Para tentar no celular: dê ao Vinalayan Lab o acesso de leitura de notificações, deixe uma faixa tocando e espere o painel chegar em projetando. Título e artista só são enviados em `DashState.STREAMING` (`DashSession.kt`, `launchMediaInfo`). Play, pausa e volume não têm byte no `DashViewModel` — está em [FALHAS_VINALAYAN.md](FALHAS_VINALAYAN.md), e o painel não finge esses comandos.

## O que o painel desenha

Lista completa, com o que é aproximado ou ausente: [CHECKLIST_TELAS.md](CHECKLIST_TELAS.md). O desenho é novo, a partir dos manuais da família Tripper, não uma foto da Himalayan 450 Brasil 2025. Não achei o PDF do manual brasileiro.

Firmware: o `CLAUDE.md` do autor original fala em 11.63. Um vídeo da moto foi lido, de forma aproximada, como REICVIS1162. **Os bytes implementados são os do código Vinalayan, não os de um desses rótulos.** Falta a escolha de qual firmware a bancada deve mirar.

Protocolo, com arquivo e linha: [docs/PROTOCOLO.md](docs/PROTOCOLO.md).

## O que foi testado, e o que não foi

- Testado em JVM, neste ambiente: `:dash-protocol:test` (7 testes: hex de auth, sequência K1G, hostname, now playing, projeção, RTP FU-A, e um telefone falso que autentica e manda um quadro).
- O CI, quando o workflow verde existir, roda esses testes e também `LabModeContractTest` nos variants `localRelease` e `labRelease`, monta os dois APKs e publica a Release. O link do run só vale depois que o GitHub Actions terminar — não antes.
- Não testado: instalação no Android, um aparelho, dois aparelhos, MediaCodec com vídeo de verdade, Spotify, a moto.

O workflow de APK de produção (`android-apk.yml`) ficou só manual. Ele precisa dos segredos de keystore e Firebase, que este fork não tem.
