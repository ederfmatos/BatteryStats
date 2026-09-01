# BatteryStats

App Android de uso pessoal que mede o consumo de bateria do próprio aparelho e monta um **ranking
aproximado** de quais apps mais gastam. Instalação por sideload — não vai para a Play Store.

## O que o app mede

- **Nível, status, fonte de energia, temperatura e voltagem** — direto do broadcast
  `ACTION_BATTERY_CHANGED`.
- **Corrente instantânea (`CURRENT_NOW`)** e **contador de carga (`CHARGE_COUNTER`)** — via
  `BatteryManager`.
- **Dreno real por janela de tempo**, em mA, preferindo Δ`CHARGE_COUNTER`/Δt. Isso é medição.
- **Dreno separado por tela ligada e tela desligada.** É o dado mais útil daqui: dreno alto com a
  tela desligada é o sintoma clássico de wakelock preso.
- **Dreno por hora do dia**, agregado nos últimos 7 dias — é onde aparece "toda madrugada entre 2h e
  5h alguma coisa acorda o aparelho".
- **Autonomia projetada**, calculada sobre o padrão real das últimas 24h, não sobre o consumo do
  último minuto.

## O que o app NÃO mede

- **Consumo real por app.** A permissão `BATTERY_STATS`, que dá o mAh por UID, é
  `signature|privileged`: só apps assinados com a chave da plataforma ou instalados em
  `/system/priv-app` conseguem. Um APK sideloadado nunca vai receber, e ler `dumpsys` via
  `Runtime.exec` falha sem root. O app não tenta nenhum dos dois.
- O ranking da tela **Vilões** é **estimativa por correlação**: mede-se o dreno real de cada janela
  de tempo e divide-se entre os apps que estavam em primeiro plano naquela janela, proporcionalmente
  ao tempo de cada um. Isso é uma inferência, não uma medição por app. A tela diz isso no rodapé.
- **Consumo de apps em segundo plano.** Janelas com a tela desligada vão inteiras para o bucket
  "Sistema / segundo plano" — atribuir dreno de tela desligada a um app específico pelo
  `UsageEvents` seria invenção.
- **Capacidade absoluta ou saúde de fábrica da bateria.** A capacidade de projeto não é exposta por
  API pública. A tela de saúde mostra uma comparação **relativa**: a carga cheia observada agora
  contra a maior que o app já registrou.

## Precisão de `CURRENT_NOW`

A documentação diz que `BATTERY_PROPERTY_CURRENT_NOW` devolve **microampères**, com valor **negativo
durante a descarga**. Na prática:

- alguns aparelhos reportam em **miliampères** (mil vezes menor);
- alguns **invertem o sinal** (positivo durante a descarga);
- alguns simplesmente devolvem `0` ou um valor fixo.

Por isso o app trata `CURRENT_NOW` como valor bruto de unidade desconhecida e roda um **calibrador**
nas primeiras horas: compara a magnitude e o sinal dele contra o dreno derivado do `CHARGE_COUNTER`,
que não mente sobre magnitude, e deduz o fator de escala (1 ou 1000) e o sinal. O resultado aparece
em **Diagnóstico**, com opção de forçar manualmente.

Em aparelhos onde o `CHARGE_COUNTER` fica travado não há como calibrar automaticamente — nesses
casos vale conferir o valor bruto na tela inicial e forçar a calibração à mão.

## Permissões, e por que cada uma

| Permissão | Para quê |
|---|---|
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Manter a amostragem viva. Em background o Android congela o processo e a medição para justamente nas horas mais interessantes. `specialUse` é o tipo honesto: monitorar a própria bateria não é `dataSync` nem `health`. |
| `POST_NOTIFICATIONS` | O Android exige uma notificação visível enquanto um foreground service roda. Sem ela o serviço não sobe (API 33+). |
| `RECEIVE_BOOT_COMPLETED` | Religar a amostragem depois de reiniciar o aparelho. Fica atrás de um toggle nos Ajustes e vem desligado. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Pedir a isenção que evita o app ser morto enquanto mede. O app só abre esse diálogo depois de explicar o porquê. |
| `PACKAGE_USAGE_STATS` | Reconstruir a timeline de app em primeiro plano, que é o que permite montar o ranking. Concedida numa tela de Configurações do sistema, não em runtime. **O app funciona sem ela**, em modo degradado: continua medindo a bateria, só joga tudo no bucket de sistema. |

**Não há permissão `INTERNET`.** Tudo é local: sem analytics, sem crash reporting, sem telemetria.
Como a permissão não está declarada, nem uma dependência transitiva consegue abrir socket.

Também **não há** `BATTERY_STATS` no manifest — ver "o que o app não mede".

## Instalar o APK pelo próprio celular

O CI compila e publica o APK a cada push.

1. Abra `https://github.com/ederfmatos/BatteryStats/actions` no navegador **do celular**.
2. Toque na execução mais recente com o check verde.
3. Role até **Artifacts** e baixe `batterystats-<sha>`. O GitHub entrega um `.zip`.
4. Descompacte (o gerenciador de arquivos do Android faz isso) e abra o `app-debug.apk`.
5. O Android vai pedir para permitir a instalação de fontes desconhecidas para o app que está
   abrindo o arquivo. Autorize e instale.

O APK é assinado com a **debug key** gerada automaticamente pelo AGP. É o suficiente para sideload —
não há keystore de release neste repositório.

> Os artifacts do GitHub Actions expiram em 30 dias. Passado esse prazo, rode o workflow de novo em
> Actions → build → *Run workflow*.

## Primeiro uso

1. Abra o app e toque em **Iniciar** na aba *Agora*. Ele vai pedir a permissão de notificação.
2. Ainda na aba *Agora*, conceda a **isenção de otimização de bateria** quando o card aparecer.
3. Na aba *Diagnóstico*, conceda o **acesso ao uso de apps** para habilitar o ranking.
4. Deixe rodando algumas horas. A calibração automática e as médias precisam de dados; a estimativa
   de saúde precisa de pelo menos 7 dias.

## Retenção de dados

Amostras cruas ficam **14 dias**; os resumos diários ficam **para sempre**. A limpeza roda uma vez
por dia num worker do WorkManager, junto com a consolidação do dia anterior e a autocalibração.

Dá para exportar tudo em **CSV** ou **JSON** pelos Ajustes — o arquivo é salvo onde você escolher,
via `ACTION_CREATE_DOCUMENT`, sem nenhuma permissão de armazenamento.

## Arquitetura

```
data/    Room, readers de bateria, receivers, UsageStats, DataStore, foreground service
domain/  cálculo de dreno, calibração, atribuição, saúde, export — Kotlin puro, zero android.*
ui/      Compose + um ViewModel
```

A camada `domain` roda em JVM pura e é onde estão os testes. Se um cálculo precisa de `Context`,
ele está na camada errada.

Injeção manual via `AppContainer` — o projeto é pequeno demais para justificar Hilt ou Koin.

## Build local

Requer JDK 17+ e o Android SDK com a plataforma correspondente ao `compileSdk`.

```bash
./gradlew testDebugUnitTest assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.
