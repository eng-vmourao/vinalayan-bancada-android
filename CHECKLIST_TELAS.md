# Checklist das telas do painel — Himalayan 450

Fontes usadas, nesta ordem. Não houve manual em PDF da Royal Enfield Brasil
aberto neste trabalho; onde o manual brasileiro não foi encontrado, a tela
fica marcada com a fonte real.

- Tripper Dash, site da marca: modo analógico (padrão) e digital (mapa inteiro), dia/noite, chamadas, música, combustível, marcha. <https://www.royalenfield.com/in/en/tripper-dash/>
- Manual do proprietário da Himalayan (alemão, mesma família de painel), julho de 2024: joystick à esquerda abre Trip 1, Trip 2, autonomia, consumo instantâneo, bateria, revisão e temperatura; joystick para cima/baixo escolhe analógico ou digital. <https://www.royalenfield.com/content/dam/open-pdf/royal-enfield_new-himalayan-owners-manual-german.pdf>
- Manual da Guerrilla 450 (mesma família TFT redonda de 4"): legenda do analógico e do digital (relógio, temperatura externa, modo, velocidade, hodômetro, setas, ABS, facho, cavalete, marcha). <https://www.royalenfield.com/content/dam/open-pdf/royal-enfield-guerrilla-450-owners-manual.pdf>
- Imprensa do lançamento no Brasil em 2025: TFT redondo, analógico e digital, navegação, Eco e Performance. Não lista Rain, Tour nem Off-road.

Modos de pilotagem implementados, os quatro do manual: Performance ou ECO, cada um com ABS ligado ou ABS traseiro desligado.

| Tela | Status | Nota |
|---|---|---|
| Home analógico (velocidade, conta-giros, marcha, combustível, hodômetro, relógio) | aproximado | Desenho novo a partir dos manuais, não é foto do painel BR 2025 |
| Home digital com área de mapa | aproximado | Vídeo RTP entra no círculo; o enquadramento não foi comparado com a moto |
| Tema claro e tema escuro | implementado | Troca manual na bancada. O painel real também segue o ambiente; isso não foi reproduzido |
| Trip A e Trip B | aproximado | Números simulados na bancada, não vêm do barramento da moto |
| Autonomia | aproximado | 17 L × barras/8 × consumo simulado |
| Consumo instantâneo / médio | aproximado | Um número só; o manual separa instantâneo e média |
| Medidor de bateria (4 segmentos de 1 V) | aproximado | Mostra volts, não os quatro segmentos desenhados |
| Revisão (km e dias) | aproximado | Valor simulado |
| Temperatura do motor | aproximado | Faixa 60–120 °C citada no manual; aqui é um número |
| Marcha e ponto morto | implementado | 0 desenha N |
| Luzes: facho, setas, ABS, motor, cavalete | aproximado | Ícones em texto, acesos pelos controles da bancada |
| Conectividade do celular | aproximado | Vira o estado autenticado/projetando, não o ícone oficial |
| Navegação curva a curva no modo analógico | ausente | O vídeo cobre o modo digital. Não há seta de manobra desenhada |
| Mapa / vídeo no digital | implementado | `MediaCodec` no app; o teste JVM só confere o NAL remontado |
| Música (título e artista) | implementado | TLV `05 0D`. Álbum fica no log, não no desenho |
| Chamada (nome) | implementado | TLV `05 22` |
| Modos Eco e Performance | implementado | Quatro combinações com ABS |
| Rain, Tour, Off-road | ausente | Não aparecem nas fontes da Himalayan 450 consultadas |
| Menu de ajustes (unidades, reset de trip, relógio, brilho) | ausente | Só a troca analógico/digital e o tema |
| Tela de ignição desligada | implementado | Para de responder no protocolo |
| Diagnóstico (estado, hex, fps, log) | implementado | Aba Diagnóstico, exportar por compartilhamento do Android |
| Joystick do guidão | implementado | Envia os bytes que o Vinalayan conhece. Play/pausa/volume não têm byte |

Nada desta tabela foi conferido num aparelho físico nem numa Himalayan.
