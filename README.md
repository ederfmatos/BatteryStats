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

- **mAh real por app.** Isso **não existe em nenhum nível de privilégio, nem com root.** O
  framework não mede corrente por app: ele mede tempo de uso de cada subsistema e multiplica por
  constantes que o fabricante declara em `power_profile.xml`
  ([fonte](https://source.android.com/docs/core/power)). Todo app que mostra "mAh por app" está
  mostrando esse modelo.
- O ranking da tela **Vilões** é **estimativa por correlação**: mede-se o dreno real de cada janela
  de tempo e divide-se entre os apps que estavam em primeiro plano naquela janela, proporcionalmente
  ao tempo de cada um. Isso é uma inferência, não uma medição por app. A tela diz isso no rodapé.
- **Consumo de apps em segundo plano.** Janelas com a tela desligada vão inteiras para o bucket
  "Sistema / segundo plano" — atribuir dreno de tela desligada a um app específico pelo
  `UsageEvents` seria invenção.
- **Capacidade absoluta ou saúde de fábrica da bateria.** A capacidade de projeto não é exposta por
  API pública. A tela de saúde mostra uma comparação **relativa**: a carga cheia observada agora
  contra a maior que o app já registrou.

## Modo avançado — um comando `adb`, uma vez

Por padrão o ranking é estimativa por correlação. Com **uma** permissão concedida por linha de
comando, ele passa a mostrar contadores **medidos** pelo próprio serviço de bateria do sistema.

```
adb shell pm grant dev.ederfmatos.batterystats android.permission.BATTERY_STATS
```

O comando exato, com botão de copiar e indicador ao vivo de concessão, está em **Ajustes →
Diagnóstico → Modo avançado**.

**Por que isso funciona.** O `protectionLevel` de `BATTERY_STATS` no AOSP é
`signature|privileged|development` — e `development` quer dizer concedível por shell. O app declara
a permissão no manifest (declarar não concede nada; é só o pré-requisito para o `pm grant`
funcionar).

**O que passa a ser fato medido**, via `SystemHealthManager.takeUidSnapshot` — SDK público desde a
API 24, sem reflection e sem API oculta:

- wakelocks parciais por app, com tag, contagem e duração — responde *quem* acorda o aparelho;
- tempo de GPS, câmera, áudio, vídeo, scans de Wi-Fi e Bluetooth;
- jobs e syncs executados;
- tempo em cada estado de processo (visível, foreground service, background, cached);
- CPU e bytes de rede por app.

**O que continua não existindo:** mAh por app. O app mostra os tempos justamente por isso.

O grant sobrevive a reiniciar o aparelho e às atualizações automáticas. Some só se você
desinstalar. Sem ele, o app funciona exatamente como antes.

> **Não** é preciso mexer em `hidden_api_policy`. Outros apps da categoria instruem isso; seria
> desligar a proteção de API oculta do aparelho inteiro para ganhar números que continuam
> modelados.

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

### Rede

A partir da auto-atualização o app declara `INTERNET`, com escopo estrito. Os **únicos** hosts que
ele contata:

| Host | Para quê |
|---|---|
| `github.com` | resolve `releases/latest/download/...` (redireciona) |
| `objects.githubusercontent.com` | onde o GitHub de fato serve o APK e os JSON |

São três GETs na vida do app: `latest.json`, `config.json` e o próprio APK. **Nenhum dado de
bateria sai do aparelho.** Não há analytics, crash reporting nem telemetria de espécie alguma, e
não há nenhum endpoint de escrita. `network_security_config.xml` bloqueia cleartext globalmente:
um redirect para `http://` falha em vez de baixar um APK por canal não autenticado.

Também **não há** `BATTERY_STATS` no manifest — ver "o que o app não mede".

## ⚠️ O keystore de release

O APK de release é assinado com um keystore fixo. O Android só instala uma atualização por cima do
app existente se ela vier assinada com **o mesmo certificado**.

> **Perder esse keystore significa nunca mais conseguir atualizar o app instalado.** A única saída
> seria desinstalar — o que **apaga o banco de amostras**. Faça backup offline.

O keystore vive em `~/BatteryStats-keystore/` (fora do repositório, e o `.gitignore` barra `*.jks`).
As credenciais estão em `~/BatteryStats-keystore/credenciais.txt`. No CI ele é reconstituído a
partir dos GitHub Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`, e
apagado do runner ao fim do job.

Para assinar localmente, copie `keystore.properties.example` para `keystore.properties` (barrado
pelo git) e preencha.

## Auto-atualização

> O repositório é **público** por necessidade desta funcionalidade: o app baixa de
> `releases/latest/download/...` sem autenticação, e num repositório privado essas URLs devolvem
> 404 para qualquer cliente sem token. Embutir um token no APK seria pior — ele vazaria junto com
> o APK.

O CI compila e publica uma Release a cada push em `main`. O app checa `latest.json`, compara o
`versionCode` com o instalado, baixa o APK e se instala.

**Antes de instalar, quatro verificações obrigatórias**, nesta ordem, falhando fechado:

1. SHA-256 do arquivo baixado igual ao publicado;
2. certificado de assinatura idêntico ao do app instalado;
3. mesmo `packageName` e `versionCode` maior;
4. `minSdk` do APK compatível com o aparelho.

Sem as quatro, isto seria um canal de execução remota de código no aparelho. Nenhuma está atrás de
flag.

## Instalar pelo próprio celular

**Só a primeira instalação é manual.** Depois disso o app se atualiza sozinho.

Abra este link no navegador **do celular** e toque em baixar:

```
https://github.com/ederfmatos/BatteryStats/releases/latest/download/app-release.apk
```

O Android vai pedir para permitir a instalação de fontes desconhecidas para o navegador. Autorize e
instale.

> A URL acima é estável: aponta sempre para a Release mais recente. É por isso que o asset da
> Release tem nome fixo (`app-release.apk`), enquanto o artifact do workflow leva o short SHA.

## Primeiro uso

1. Abra o app e toque em **Iniciar** na aba *Agora*. Ele vai pedir a permissão de notificação.
2. Ainda na aba *Agora*, conceda a **isenção de otimização de bateria** quando o card aparecer.
3. Na aba *Diagnóstico*, conceda o **acesso ao uso de apps** para habilitar o ranking.
4. Deixe rodando algumas horas. A calibração automática e as médias precisam de dados; a estimativa
   de saúde precisa de pelo menos 7 dias.

## Relatório

A aba **Relatório** monta um resumo em Markdown de 1 a 3 KB com tudo já agregado: cobertura real,
degrau de quantização e a incerteza que ele impõe, split de tela ligada/desligada, dreno por hora
do dia, top 10 apps e o bucket de sistema, mais as médias de brilho, rede e localização.

No fim vem um bloco de **ressalvas geradas automaticamente** — cobertura abaixo de 70%, excesso de
janelas de baixa confiança, degrau grosseiro, permissão de uso ausente. Quem analisar precisa saber
onde o dado é fraco antes de concluir.

Dois botões:

- **Compartilhar** — share sheet do Android, com o Markdown em `EXTRA_TEXT` e opcionalmente o JSON
  cru anexado. Funciona com qualquer app instalado.
- **Abrir no Claude** — monta `https://claude.ai/new?q=<relatório>`. É secundário de propósito:
  nada na documentação garante que o app do Claude capture essa URL no Android, então ela pode
  abrir no navegador. Se o relatório não couber no link, o app envia a versão curta e avisa na tela.

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
