# BatteryStats — App Android de monitoramento de bateria

App Android nativo que mede o consumo de bateria do aparelho e produz um ranking
aproximado de quais apps mais consomem. Uso pessoal, sideload — não vai para a Play Store.

Trabalhar em fases. Ao fim de cada fase: parar, rodar o build, reportar. Não avançar sem OK.

## Restrições técnicas (não negociáveis)

1. **BATTERY_STATS é signature|privileged.** APK sideloadado nunca consegue consumo exato em mAh
   por app. Não depender disso, não pedir no manifest, não tentar `dumpsys` via `Runtime.exec`.
2. **Consumo por app é estimado por correlação**: dreno medido na janela × app em primeiro plano
   naquela janela. Explícito na UI. Estimativa nunca é apresentada como medição.
3. **Sem permissão INTERNET.** Tudo local. Sem analytics, crash reporting ou telemetria.
4. `BATTERY_PROPERTY_CURRENT_NOW` é documentado como microampères, mas **OEMs mentem**: alguns
   reportam em mA, outros invertem o sinal (positivo descarregando). Tratar — ver Fase 3.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Room + KSP
- Coroutines / Flow
- minSdk 26, targetSdk na versão estável mais recente
- Gradle com version catalog (`gradle/libs.versions.toml`)
- Gráficos: lib leve de charts para Compose ou Canvas puro — escolher e justificar em uma linha.

**Verificar as versões estáveis atuais** de AGP, Kotlin, Compose BOM, Room e KSP antes de escrever
o `libs.versions.toml`. Não chutar versões. Confirmar compatibilidade Kotlin ↔ KSP ↔ AGP.

## Fase 1 — Esqueleto e leitura instantânea

Tela única, atualizando a cada 2s:
- Nível (%), status (carregando/descarregando/cheio), fonte (AC/USB/wireless)
- Temperatura (°C), voltagem (V)
- Corrente instantânea (mA)
- CHARGE_COUNTER (µAh)

Fontes:
- `ACTION_BATTERY_CHANGED` — sticky broadcast: estado inicial com `registerReceiver(null, filter)`,
  depois receiver em runtime para updates. Não registrar no manifest (não funciona desde API 26).
- `BatteryManager.getIntProperty` / `getLongProperty` para CURRENT_NOW, CHARGE_COUNTER, CAPACITY.

`BatteryReader` isolado da UI, com interface própria, para poder ser fakeado.

**Aceite:** APK debug instala, tela mostra números que mudam ao plugar/desplugar o cabo.

## Fase 2 — Amostragem contínua

**Foreground service** amostrando a cada 60s (configurável: 30s / 60s / 5min).

- Notificação persistente de baixa prioridade: dreno atual e projeção de horas restantes.
- `foregroundServiceType` adequado no manifest + permissão correspondente.
  `POST_NOTIFICATIONS` em runtime (API 33+).
- Isenção de otimização de bateria via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, **só depois
  de explicar por quê** — senão o app é morto justamente quando está medindo.
- `BOOT_COMPLETED` receiver para religar o serviço, atrás de um toggle nas configurações.
- WorkManager periódico como rede de segurança para agregação diária, não para amostragem.

Room, tabela `battery_sample`:
`id, timestampMs, levelPct, chargeCounterUah, currentNowRaw, temperatureDeciC, voltageMv, status, plugType, screenOn, foregroundPackage`

Receivers de SCREEN_ON / SCREEN_OFF / USER_PRESENT, gravando o estado da tela em cada amostra —
separar dreno com tela ligada de tela desligada é o dado mais útil do app inteiro.

Retenção: amostras cruas por 14 dias, agregados diários indefinidamente. Limpeza via WorkManager.

**Aceite:** celular parado 30 min, tela desligada → ~30 amostras coerentes no banco.

## Fase 3 — Cálculo de dreno e autocalibração

Duas fontes independentes de dreno por janela:
- **A (preferida):** Δ `chargeCounterUah` / Δt → mA reais.
- **B (fallback):** média de CURRENT_NOW entre amostras, para aparelhos onde CHARGE_COUNTER não se
  move ou é fixo.

Calibrador rodando nas primeiras horas: compara magnitude e sinal de CURRENT_NOW contra o dreno
derivado de A e deduz o fator de escala (1 ou 1000) e o sinal. Guardar o resultado e mostrar em
Configurações → Diagnóstico, com opção de forçar manualmente. Descartar amostras com status
CHARGING ao calcular dreno de descarga.

Métricas derivadas, por dia e por hora do dia:
- mA médio com tela ligada vs tela desligada
- %/hora em cada regime
- Autonomia projetada sobre o padrão real das últimas 24h — não extrapolação linear do último minuto
- Aviso quando o dreno com tela desligada passar de um limiar (sintoma clássico de wakelock preso)

Testes unitários para toda essa matemática, com séries sintéticas: descarga normal, aparelho
carregando, gap de amostragem (celular dormiu), CHARGE_COUNTER travado, CURRENT_NOW invertido.
Lógica testável sem device.

## Fase 4 — Atribuição por app

`PACKAGE_USAGE_STATS`: declarar no manifest, detectar se está concedida, levar o usuário a
`Settings.ACTION_USAGE_ACCESS_SETTINGS` com texto curto explicando. O app funciona em modo
degradado sem ela.

`UsageStatsManager.queryEvents` com ACTIVITY_RESUMED / ACTIVITY_PAUSED para reconstruir a timeline
de primeiro plano. Não usar `queryUsageStats` agregado — perde granularidade.

Algoritmo de atribuição:
1. Para cada par de amostras consecutivas, calcular o mAh consumido na janela.
2. Fatiar a janela pelos intervalos de primeiro plano da timeline.
3. Distribuir o mAh proporcionalmente ao tempo de cada app na janela.
4. Janelas com tela desligada vão para o bucket "Sistema / segundo plano", nunca para um app
   específico — atribuir dreno de tela desligada a um app via queryEvents seria invenção.
5. Subtrair uma baseline de idle (mA mínimo observado com tela desligada nas últimas 24h) antes de
   atribuir, para não creditar aos apps o consumo de repouso do aparelho.

Tela "Vilões": ranking por mAh estimado no período (hoje / 7 dias), com nome e ícone do app, tempo
em primeiro plano, mA médio em foreground, e o bucket de segundo plano sempre visível na lista.
Rodapé fixo: estimativa por correlação, não medição direta.

## Fase 5 — Histórico, saúde e export

- Gráfico de nível ao longo do tempo, com faixas sombreadas de tela ligada e marcadores de carga.
- Gráfico de dreno (mA) por hora do dia, agregando os últimos 7 dias — é aqui que aparece
  "toda noite entre 2h e 5h algo acorda o aparelho".
- Estimativa de saúde da bateria: CHARGE_COUNTER observado próximo de 100% comparado com o maior
  valor já registrado. Rotular como estimativa relativa; capacidade de projeto não é exposta por
  API pública, não inventar número absoluto.
- Export CSV e JSON de amostras e agregados, via `ACTION_CREATE_DOCUMENT`.
- Tela de Diagnóstico: fabricante, modelo, Android, resultado da calibração, contagem de amostras,
  gaps detectados, permissões concedidas.

## Fase 6 — Build no CI

`.github/workflows/build.yml`:

- Dispara em `push` e `workflow_dispatch`
- JDK 17, cache de Gradle
- `./gradlew testDebugUnitTest assembleDebug`
- `upload-artifact` com o APK, retenção de 30 dias
- Nome do artifact com o short SHA

O APK debug é assinado com a debug key automática — suficiente para sideload. Não configurar
signing de release nem colocar keystore no repo.

`README.md` com: como baixar o APK dos artifacts pelo próprio celular, permissões necessárias e por
quê, o que o app mede e o que ele não mede, e as ressalvas de precisão do CURRENT_NOW.

## Padrões de código

- Arquitetura em camadas: `data` (Room, readers, receivers) / `domain` (cálculo, atribuição —
  Kotlin puro, zero import de `android.*`) / `ui` (Compose + ViewModels).
- A camada `domain` tem que ser testável em JVM pura. Se um cálculo precisar de contexto Android,
  ele está na camada errada.
- Sem exceção engolida em silêncio. Todo `catch` ou trata ou loga com contexto.
- Nada de `!!` fora de teste.
- Strings em `strings.xml`, em português.
- Suporte a tema escuro e Material You.

## Não faça

- Não pedir permissões que o app não usa. Justificar cada uma no README.
- Não criar onboarding de múltiplas etapas. Pedir permissão no momento em que ela é necessária,
  com uma frase de contexto.
- Não adicionar lib de DI (Hilt/Koin) — injeção manual via um container simples basta.
- Não inventar APIs. Se não houver certeza de que um método ou constante existe na versão alvo,
  verificar antes de usar.
