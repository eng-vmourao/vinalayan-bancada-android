# Vinalayan

Aplicativo Android pessoal de Vinícius para sua Himalayan, baseado no [OpenDash](https://github.com/subtlesayak/open-dash). Reúne veículos, manutenção, abastecimentos, despesas e visualização de rotas.

Esta versão adota o nome Vinalayan. O histórico, a licença e os créditos do projeto original estão preservados. A prévia 0.1.5 para Android traz suporte a rotas do Waze, velocidade permitida da via, cronômetro de semáforo, voz FULL por padrão e conexão com a Tripper Dash.

## Upstream project notice

> [!WARNING]
> Royal Enfield contacted the OpenDash project, and the upstream public branch later removed dash connection and projection code. Vinalayan restores the implementation already present in this repository's history as an experimental, rider-controlled feature. Compatibility still depends on the Tripper Dash firmware and must be checked on the motorcycle.

## Overview

Vinalayan is an open-source Android app for motorcycle ownership, trip preparation, route preview, and Tripper Dash projection, based on OpenDash.

## Current Focus

- Route preview from shared map links or `geo:` links.
- Vehicle profiles with odometer, PUC, insurance, and service details.
- Garage and maintenance tracking for parts, service intervals, and service history.
- Expense tracking for fuel, repairs, accessories, riding gear, food, stays, transport, and other ownership costs.
- Downloadable wallpaper pack in Settings.
- Material 3 UI themes.
- Local-first storage, with optional bring-your-own Firebase/Google sync where configured.
- Home connection menu with live Tripper status.
- Wi-Fi discovery and pairing for Tripper networks beginning with `RE_`.
- Dash authentication, route projection, joystick controls, media/call cards, and wallpaper playback.

## Install

Baixe **Vinalayan-0.1.5-preview-universal.apk** na [página de versões](https://github.com/eng-vmourao/Vinalayan/releases), abra o arquivo no Android e permita a instalação quando solicitado. Nas próximas versões, use **More → Update from GitHub → Check** para baixar e validar a atualização pelo aplicativo. O app requer Android 7.0 ou posterior; a conexão com a Tripper requer Android 10 ou posterior.

A prévia é validada com testes unitários, Android Lint, verificações de assinatura e instalação/abertura do menu de conexão e das cinco abas em emulador Android 15. A conexão e a projeção precisam ser validadas com uma Tripper Dash real. Veja o [guia completo de instalação e atualização](docs/APK.md).

## First Use

1. Open Vinalayan.
2. On **Home**, tap **Connect to dash** while the motorcycle and Tripper Dash are on.
3. Accept the Android Wi-Fi and nearby-device permissions, then select the `RE_*` network shown by the bike.
4. Add your motorcycle in **Vehicles** and use **Garage** and **Expenses** for its records.
5. Share a destination or `geo:` link into Vinalayan, then choose **Send to Tripper Dash**.
6. Use **More** for account, sync, appearance, map provider, and updates.

## Main Tabs

| Tab | What it does |
| --- | --- |
| Home | Tripper status, connection menu, navigation, saved destinations, and rides |
| Vehicles | Add/edit vehicles and choose the active vehicle |
| Expenses | Add, filter, review, and export expenses |
| Garage | Odometer, mileage, spare parts, and service logging |
| More | Account, sync, themes, navigation provider, units, help, and wallpaper downloads |

Route preview opens from shared destinations and saved locations instead of being a permanent bottom tab.

## Build From Source

```bash
git clone https://github.com/eng-vmourao/Vinalayan.git
cd Vinalayan
./gradlew :app:assembleLocalDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleLocalDebug
```

Run local unit tests:

```bash
./gradlew :app:testLocalDebugUnitTest
```

The [APK guide](docs/APK.md) explains GitHub Actions builds, installation, signing, and updates. Release signing uses your own keystore through Gradle properties or CI secrets. Never commit keys, APKs, logs, `local.properties`, `key.properties`, `google-services.json`, keystores, tokens, or other private files.

## Release Variants

- `localRelease` builds signed APKs for GitHub releases with application id `com.vinalayan.app`.
- `playRelease` retains the optional Google Play bundle configuration with application id `com.vinalayan.app`; it requires its own service configuration before publication.
- The preview APK uses local storage and open map/route providers. Firebase sync and Mapbox are optional and require your own configuration.

## Privacy

- App data is local-first.
- Expense exports are created locally and shared only when you choose to share them.
- Firebase/Google sync is optional and bring-your-own-project.
- Release builds should avoid logging full URLs, coordinates, account IDs, or device identifiers.
- Tripper Wi-Fi credentials are stored locally with Android encrypted preferences when available.

## Contributing

Issues and pull requests are welcome for routes, Tripper compatibility, vehicles, garage, maintenance, expenses, sync, themes, and wallpapers.

Please remove personal data from logs and screenshots before sharing: coordinates, SSIDs, account IDs, tokens, and device identifiers.

## License

Vinalayan is based on OpenDash and distributed under the terms in [`LICENSE`](LICENSE). Original attribution is preserved in [`NOTICE`](NOTICE).

## References

- [norbertFeron/better-dash](https://github.com/norbertFeron/better-dash) - Early motivation
- [adityadasika21/NorthStar](https://github.com/adityadasika21/NorthStar) - Original app base
