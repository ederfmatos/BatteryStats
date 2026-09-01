package dev.ederfmatos.batterystats.data.update

/**
 * Os únicos endereços que o app contata. Todos apontam para `releases/latest/download/...`, o link
 * estável do GitHub que sempre resolve para a Release mais recente — por isso os assets precisam
 * ter nome fixo.
 */
object UpdateEndpoints {
    const val REPOSITORY = "ederfmatos/BatteryStats"

    const val LATEST_MANIFEST_URL =
        "https://github.com/$REPOSITORY/releases/latest/download/latest.json"

    const val REMOTE_CONFIG_URL =
        "https://github.com/$REPOSITORY/releases/latest/download/config.json"

    /** Botão permanente da tela Sobre e último degrau da cascata de instalação. */
    const val LATEST_APK_URL =
        "https://github.com/$REPOSITORY/releases/latest/download/app-release.apk"

    const val RELEASES_PAGE_URL = "https://github.com/$REPOSITORY/releases/latest"
}
