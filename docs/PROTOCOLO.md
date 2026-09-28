# Protocolo do painel simulado

O lado da moto foi lido do Vinalayan (cópia em `1f4c49b`), não inventado.
O módulo `:dash-protocol` repete esses bytes para o painel e para os testes.
O módulo `:app` continua com as classes originais; o flavor `lab` só troca
endereço e porta.

## Portas

| Uso | Valor | Origem |
|---|---|---|
| Celular envia controle, origem | UDP 2000 | `DashSocket.kt:15-16` e `:53` |
| Destino do controle na moto | broadcast `192.168.1.255:2000` | `DashSocket.kt:28-29` e `:80` |
| Celular recebe | UDP 2002, aberto antes do primeiro TX | `DashSocket.kt:17-20` e `:58` |
| Vídeo RTP/H.264 | `192.168.1.1:5000` | `DashSocket.kt:21` e `:31` |
| SSID / senha de fábrica | prefixo `RE_`, senha `12345678` | `DashConfig.kt:97-98` |

No laboratório o controle vai em unicast para o IP digitado (ou `127.0.0.1`)
e, num aparelho só, o celular **não** faz bind na 2000 (bind efêmero).
A moto real não usa esse desvio: `DashEndpoints.PRODUCTION` mantém os valores
da tabela. Ver `LabModeContractTest`.

## Pacote de saída (celular → painel)

`K1GPacket.kt:8-16` e `build` nas linhas 35–55.

- `[0:2]` tamanho total, `[2:4]` `1 + N` segmentos
- `[4:8]` zeros, `[8:12]` `02 01 00 05`, `[12:16]` `K1G `
- `[16]` sequência, gravada na hora do envio (`K1GPacket.kt:59-65`, chamada em `DashSocket.kt:75`)
- TLV: tipo, sub, tamanho u16 big-endian, valor

O painel lê os TLV a partir do byte seguinte à sequência (`K1G.parseOutgoing`).

## Pacote de entrada (painel → celular)

`K1GPacket.parseIncoming`, linhas 73–90. Cabeçalho curto: tamanho, quantidade
de TLV, 4 bytes ignorados, TLV a partir do offset 8. A resposta sai para o
IP de origem do celular, porta 2002 — não para a porta de origem do datagrama.

## Autenticação

1. Rajada inicial, inclusive `authRequest` `08 04 00 01 01` (`DashCommands.kt:13` e `:24-35`).
2. Painel manda a RSA-1024 em dois pacotes, como `DashAuth.kt:25-26` permite:
   `07 00` módulo e `07 03` expoente (`DashAuth.kt:40-41`).
3. Celular cifra `SSID UTF-8 ‖ chave AES-256` com `RSA/ECB/PKCS1Padding`
   (`DashAuth.kt:62-71`) e manda `08 00` com 128 bytes (`DashCommands.kt:16-18`).
4. `07 01` com valor `01` confirma; qualquer outro valor rejeita
   (`DashAuth.kt:42-43`). O painel de laboratório aceita o SSID decifrado
   em vez de exigir o SSID do Wi-Fi da moto.

## Depois da autenticação

| Mensagem | Onde |
|---|---|
| Projeção on `06 05` = `55` | `DashCommands.kt:90` |
| Projeção off `06 05` = `AA` | `DashCommands.kt:92` |
| Keep-alive `05 56` = `55`, 4 Hz | `DashCommands.kt:89`, `DashSession.kt:360-366` |
| Hora `06 06` (h, min, s) | `DashCommands.kt:44-53` |
| Nome do aparelho, no fio `06 0B` (o `01` depois do magic é a sequência) | `DashCommands.kt:57-69` |
| Música `05 0D`, campos separados por NUL, 20 bytes | `DashCommands.kt:266-274` |
| Chamada `05 22` | `DashCommands.kt:278-285` |
| Agora tocando / chamada só com estado STREAMING | `DashSession.kt:400-416` |
| IDR decodificado: painel manda `09 06 55`; celular responde `06 11 55` | `DashSession.kt:297-301`, `DashCommands.kt:96` |
| Botão `09 00`, o celular usa o último byte | `DashSession.kt:312-316` |

Botões que o Vinalayan trata (`DashViewModel.kt:179-184`):

| Byte | Efeito no app |
|---|---|
| `09` | próxima faixa (ou zoom se não houver mídia) |
| `0A` | faixa anterior |
| `14` | zoom in (o better-dash chama este byte de LEFT) |
| `13` | zoom out (better-dash: RIGHT) |
| `06` | atender |
| `07` | recusar |

Play, pausa e volume **não aparecem** nesse arquivo. O painel não os envia.

## Vídeo

`RtpPacketizer.kt:15-83`: RTP PT 96, relógio de 90 kHz, sem STAP-A, FU-A tipo 28
acima de 1380 bytes, marker só no último pacote do quadro.
`NalProcessor.kt:25-36` junta SPS+PPS+IDR com start codes Annex-B. O painel
separa esses start codes antes do `MediaCodec`.

## Firmware

Há duas etiquetas e nenhuma foi conferida numa Himalayan:

- `11.63`, citada em `CLAUDE.md:24` como firmware do autor original, e o próprio texto diz que ainda não foi validada no hardware dele.
- `REICVIS1162`, trazida neste pedido como leitura aproximada de um vídeo. Não está no repositório.

O código segue os bytes do Vinalayan, não uma das duas etiquetas.
Qual das duas a bancada deve passar a mirar?
