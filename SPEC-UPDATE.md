# Atualização do app de monitoramento de bateria — Fases 7, 8 e 9

Este documento substitui integralmente qualquer spec anterior das Fases 7, 8 e 9.

---

# Parte 0 — Contexto: o que os dados reais revelaram

Rodei o app por 3h18 e exportei 80 amostras (nível 67% → 34%, 1357 mAh, capacidade implícita
~4130 mAh). A análise apontou quatro defeitos. Leia os quatro antes de mexer em qualquer código:
o primeiro invalida a matemática atual.

### Achado 1 — `chargeCounterUah` é quantizado
O GCD de todos os deltas é exatamente **4076 µAh** (0,099% da capacidade). O aparelho só move o
contador em degraus de ~4,08 mAh. Numa janela de 60s, um degrau aparece como 245 mA.

Consequência medida: toda janela de 60s com tela desligada produziu valores múltiplos de 244
(0, 244, 488, 732, 976, 1220 mA) — arredondamento, não medição. Janelas acima de 300s convergiram
para 40–100 mA, que é o dreno real em repouso. A atribuição por app em janelas de 60s é ruído puro.

### Achado 2 — o serviço morre
14 janelas acima de 3 min, a maior de 19 min. **59% do tempo total caiu dentro de gaps.**
Foreground service derrubado pelo gerenciamento agressivo da Samsung.

### Achado 3 — 22% do consumo sem app atribuído
17 amostras com `screenOn: true` e `foregroundPackage: null`.

### Achado 4 — viés do observador no `CURRENT_NOW`
Nas janelas longas de tela desligada, o `currentNowRaw` é sistematicamente 2–4× maior que o dreno
real: janela de 742s → 40 mA reais, raw = -119; janela de 1137s → 65 mA reais, raw = -287.
A leitura instantânea acontece no exato momento em que o app acorda o aparelho e mede o próprio
custo da amostragem.

### Calibração já resolvida
Neste aparelho, `CURRENT_NOW` é reportado em **mA**, não em µA (razão mediana raw/derivado = 1,07),
com sinal negativo = descarga. Manter a autocalibração genérica para outros modelos, mas gravar
este resultado como valor conhecido.

### Diagnóstico do consumo em si
76% do consumo foi com a tela ligada (1039 de 1357 mAh, média 829 mA). O repouso real é ~55–65 mA.
Não há wakelock preso. Não é bateria defeituosa — foi uso pesado em deslocamento (GPS, rede móvel
em movimento, câmera, temperatura até 39,3 °C).

---

# Parte 1 — Fase 7: corrigir a medição

## 7.1 Janela adaptativa (prioridade máxima)

Detectar o degrau de quantização em runtime: manter o GCD corrente dos deltas não-nulos de
`chargeCounterUah` das últimas 200 amostras. Persistir como `quantizationStepUah`.

Substituir o cálculo por-par-de-amostras por um **acumulador de janela**, que só fecha e emite uma
medição quando:

- acumulou **≥ 4 degraus** de quantização, ou
- passou **≥ 5 min** e acumulou **≥ 1 degrau**

Enquanto não fecha, a janela acumula. Janela que fecha por tempo com 1 degrau único carrega flag
`lowConfidence` e **nunca alimenta o ranking por app**.

Adicionar à tabela de medições: `stepsAccumulated`, `lowConfidence`, `spanMs`. Na UI, valor de
baixa confiança aparece como **faixa** ("40–290 mA"), não como número exato. A incerteza é
`stepUah / spanHours` — calcular e exibir.

Continuar gravando as amostras cruas a cada 60s. O que muda é a agregação, não a coleta.

## 7.2 Gaps explícitos

Nova tabela `measurement_gap`: `startMs`, `endMs`, `reason` (SERVICE_KILLED, DOZE, REBOOT, UNKNOWN).

- Toda vez que o serviço iniciar, comparar com o timestamp da última amostra. Diferença acima de
  **3× o intervalo configurado** = gap gravado.
- **Nenhum cálculo pode atravessar um gap.** Janela aberta que encontra gap é descartada, não
  fechada.
- A UI mostra a **cobertura real** do período ("medido 41% das últimas 24h"). Sem isso, todo
  agregado mente por omissão.

## 7.3 Manter o serviço vivo

`AlarmManager.setExactAndAllowWhileIdle` como watchdog redundante, reagendado a cada amostra, que
ressuscita o serviço se ele morreu. Pedir `SCHEDULE_EXACT_ALARM` / usar `USE_EXACT_ALARM` conforme
a API alvo.

Tela "Saúde da coleta": cobertura das últimas 24h, número de mortes do serviço, e botão que abre as
configurações de otimização de bateria, com instrução específica para Samsung (Ajustes → Bateria →
Limites de uso em segundo plano → Apps que nunca entram em suspensão).

Se a cobertura das últimas 24h ficar abaixo de 70%, banner persistente na home com essa instrução.
É inútil analisar dados com 59% de buraco.

## 7.4 Foreground package sem lacunas

Persistir `lastKnownForegroundPackage` e `lastProcessedEventMs`. Em cada amostra, consultar
`queryEvents(lastProcessedEventMs, now)` — **nunca** `(now - 60s, now)`. Se nenhum evento novo veio,
o app em primeiro plano continua sendo o último conhecido, desde que a tela não tenha apagado no
meio.

Ao ligar a tela, disparar amostra imediata. Ao apagar, também — e limpar o
`lastKnownForegroundPackage`.

Só marcar null quando a permissão de uso não estiver concedida ou houve gap. Distinguir os casos no
schema: `fgReason` em (`NO_PERMISSION`, `GAP`, `SCREEN_OFF`).

## 7.5 Corrente instantânea sem o próprio ruído

Ao amostrar, aguardar ~800 ms depois de acordar antes de ler `CURRENT_NOW`, e tirar a **mediana de
5 leituras** espaçadas de 150 ms. Gravar também `currentNowSamples` (a lista) para poder auditar a
dispersão.

Deixar explícito na arquitetura: **Δ `chargeCounter` / Δt em janela adaptativa é a fonte de verdade
dos relatórios.** `CURRENT_NOW` serve apenas para o número ao vivo na tela, rotulado como
"instantâneo".

## 7.6 Novos campos por amostra

Leituras baratas, sem permissão sensível, e que são os maiores multiplicadores de dreno:

- `screenBrightness` — `Settings.System.SCREEN_BRIGHTNESS` (0–255) + se o modo automático está ligado
- `networkType` — `ConnectivityManager` / `NetworkCapabilities`: WIFI, CELLULAR, NONE; e se é metered
- `locationEnabled` — `LocationManager.isLocationEnabled`
- `powerSaveMode` — `PowerManager.isPowerSaveMode`
- `deviceIdleMode` — `PowerManager.isDeviceIdleMode`
- `interactiveMs` — tempo acumulado de tela ligada no dia

Migração Room com `AutoMigration` onde der; senão migração manual escrita à mão. **Não apagar o
banco existente** — as amostras já coletadas devem ser mantidas.

---

# Parte 2 — Fase 8: auto-atualização

## 8.0 O que não fazer, e por quê

O pedido original era "baixar todo o código sem gerar novo APK". **Não implementar isso por
carregamento dinâmico de código** (`DexClassLoader` / `PathClassLoader` com dex baixado). Motivos:
não é possível alterar manifest, permissões, `foregroundServiceType` ou libs nativas por essa via;
quebra as premissas do R8/Compose; e transforma qualquer comprometimento do canal de download em
execução arbitrária de código no aparelho, sem verificação de assinatura pelo sistema. Se parecer
viável, argumentar antes de implementar.

O desenho é outro: o APK continua existindo, mas o usuário nunca toca nele. O CI compila a cada
push, o app percebe a versão nova, baixa, verifica e se instala. Primeira instalação manual, tudo
depois automático.

E a parte que de fato muda com frequência — limiares e heurísticas — vira config remota (8.6) e
atualiza sem APK nenhum.

## 8.1 Isto revisa duas regras das fases anteriores

**`INTERNET` passa a ser necessária.** Escopo estrito: checagem de versão, download do APK, config
remota. Nenhuma telemetria, nenhum analytics, nenhum dado de bateria enviado para servidor algum.
Configurar `network_security_config.xml` bloqueando cleartext e documentar no README exatamente
quais hosts o app contata.

**Keystore de release fixo passa a ser obrigatório.** Uma atualização só instala sobre o app
existente se estiver assinada com o mesmo certificado. Debug keys variam por máquina e não servem.

## 8.2 Keystore e CI

Gerar um keystore de release. **Não comitar.** Guardar como base64 em GitHub Secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Documentar no README, em destaque: **perder esse keystore significa nunca mais conseguir atualizar
o app instalado** — só desinstalando e perdendo o banco. Instruir backup offline.

`versionCode` derivado do número da run do GitHub Actions, monotônico. `versionName` do último tag
ou short SHA.

Workflow: build → testes → assina release → gera `latest.json` → publica GitHub Release com o APK e
o JSON como assets.

O asset da Release precisa ter **nome fixo**: `app-release.apk`. Isso revisa a Fase 6, que pedia
nome com short SHA — o SHA fica no `versionName` e no nome do artifact do workflow, mas o asset da
Release tem nome constante, senão o link estável de 8.5 quebra.

`latest.json`:

```json
{
  "versionCode": 42,
  "versionName": "1.4.0",
  "minSdk": 26,
  "apkUrl": "https://github.com/<user>/<repo>/releases/download/v1.4.0/app-release.apk",
  "sha256": "<hex minúsculo do APK>",
  "sizeBytes": 8123456,
  "publishedAtMs": 1788271356442,
  "changelog": "- Janela adaptativa\n- Correção de gaps",
  "mandatory": false
}
```

## 8.3 Checagem e download

`UpdateChecker` consulta o `latest.json` em `releases/latest/download/latest.json`, que sempre
aponta para a release mais recente.

Quando checar: na abertura do app, no máximo uma vez a cada 6h; mais WorkManager diário com
constraint de rede não medida e bateria não baixa; mais botão "Verificar agora" nas configurações.

Compara `versionCode` remoto com o local (`PackageInfo.longVersionCode`). Menor ou igual = nada a
fazer.

Download com WorkManager, foreground worker com progresso na notificação, retomável, salvando em
`cacheDir`. Só em rede não medida, salvo se o usuário tocar em "baixar agora".

## 8.4 Verificação antes de instalar — obrigatória

Nesta ordem, falhando fechado em qualquer etapa:

1. SHA-256 do arquivo baixado igual ao do `latest.json`. Diferente = apaga e aborta.
2. Assinatura: `PackageManager.getPackageArchiveInfo` com `GET_SIGNING_CERTIFICATES`, comparando o
   certificado do APK baixado com o do app instalado. Diferente = apaga e aborta com erro visível.
3. `packageName` idêntico e `versionCode` maior que o instalado.
4. `minSdk` do APK compatível com o aparelho.

Sem esses quatro checks isto é um canal de execução remota de código no celular. Não pular nenhum,
não colocá-los atrás de flag.

## 8.5 Instalação em cascata

Regra geral: nunca deixar o usuário num beco sem saída. Cada degrau só é oferecido quando o
anterior falha, e cada falha vira uma ação concreta na tela, nunca um toast genérico.

### Degrau 1 — Silencioso
`SessionParams.setRequireUserAction(USER_ACTION_NOT_REQUIRED)` com a permissão normal
`UPDATE_PACKAGES_WITHOUT_USER_ACTION` (API 31+). Só funciona da segunda auto-atualização em diante,
quando o app já é o installer of record de si mesmo e a assinatura confere. Tentar sempre; se a
sessão retornar `STATUS_PENDING_USER_ACTION`, seguir para o degrau 2 sem tratar como erro.

Depois de um silencioso bem-sucedido, mostrar notificação discreta com versão nova e changelog.
Atualizar sem avisar não é aceitável.

### Degrau 2 — Diálogo do sistema
O `PendingIntent` da sessão devolve `STATUS_PENDING_USER_ACTION`: disparar o Intent que vem em
`EXTRA_INTENT`. Um toque do usuário. Este é o caminho esperado na primeira auto-atualização.

### Degrau 3 — Permissão de fontes desconhecidas ausente
Se `packageManager.canRequestPackageInstalls()` for falso, não tentar a sessão. Tela explicando em
uma frase por que a permissão é necessária, com botão que abre
`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` passando `Uri.parse("package:$packageName")`. Ao
voltar, re-checa e retoma de onde parou — o APK já está baixado, não baixar de novo.

Permissão `REQUEST_INSTALL_PACKAGES` declarada no manifest.

### Degrau 4 — Abrir o APK já baixado no instalador do sistema
Se a sessão do `PackageInstaller` falhar por qualquer motivo, oferecer "Instalar manualmente", que
abre o arquivo já em cache:

- `Intent.ACTION_VIEW`, mime `application/vnd.android.package-archive`
- Uri via `FileProvider` (`content://`, nunca `file://`)
- `FLAG_GRANT_READ_URI_PERMISSION` + `FLAG_ACTIVITY_NEW_TASK`
- Adicionar o cache-path correspondente no `file_paths.xml` do provider

Abre o instalador do Android direto, sem novo download. É o caminho mais rápido quando a sessão
programática dá problema.

### Degrau 5 — Link direto de download
Último recurso, e também botão permanente na tela "Sobre": "Baixar a versão mais recente", usando a
URL estável que sempre aponta para a release mais nova:

```
https://github.com/<user>/<repo>/releases/latest/download/app-release.apk
```

Três ações lado a lado: Abrir no navegador (`ACTION_VIEW`), Copiar link, Compartilhar
(`ACTION_SEND`).

## 8.5.1 Mensagens de erro que servem para algo

Mapear os `STATUS_FAILURE_*` do `PackageInstaller` para texto em português com ação, nunca para o
código numérico:

| Situação | Mensagem e ação |
|---|---|
| `STATUS_FAILURE_CONFLICT` / assinatura divergente | "Esta versão foi assinada com outra chave e não pode substituir a instalada." → botão de exportar o banco + instrução de desinstalar e reinstalar |
| `STATUS_FAILURE_INCOMPATIBLE` | "Versão incompatível com este Android." → mostra minSdk exigido vs o do aparelho |
| `STATUS_FAILURE_STORAGE` | "Espaço insuficiente." → mostra quanto falta |
| `STATUS_FAILURE_ABORTED` | "Instalação cancelada." → tentar de novo, sem baixar outra vez |
| `STATUS_FAILURE_BLOCKED` | "Bloqueado pelo sistema ou por app de segurança." → cai no degrau 4 |
| Hash SHA-256 divergente | "Download corrompido." → apaga, tenta baixar 1× de novo, e se falhar cai no degrau 5 |

Gravar cada tentativa numa tabela `update_attempt` (`versionCode`, degrau, resultado,
`timestampMs`, erro) e exibir na tela de histórico de atualizações. Quando o usuário trouxer um
problema de atualização, esse histórico é o que vai dizer onde travou.

## 8.5.2 Estado da atualização na UI

Uma única tela de "Atualização" com estado explícito, sem spinner infinito:

Verificando → Nenhuma nova versão / Nova versão disponível (v1.4.0, 8,1 MB, changelog) →
Baixando 43% → Verificando integridade → Instalando → Concluído / Falhou: `<mensagem + ação>`

Cada estado com botão de cancelar quando fizer sentido. O APK baixado só é apagado depois de
instalação confirmada — se travar no meio, o botão "Instalar manualmente" precisa achar o arquivo
lá.

## 8.6 Config remota — a parte que atualiza sem APK

Um `config.json` na mesma release, com os parâmetros que mais mudam:

```json
{
  "configVersion": 7,
  "minStepsToClose": 4,
  "maxWindowMs": 300000,
  "idleBaselineMaxMA": 120,
  "highIdleWarnMA": 150,
  "samplingIntervalMs": 60000,
  "deviceOverrides": {
    "samsung/<modelo>": { "currentNowUnit": "mA", "currentNowSignInverted": false }
  }
}
```

Cacheado localmente, com fallback para os valores compilados. Isso permite ajustar limiares e a
tabela de calibração por modelo sem build nenhum. Validar o schema antes de aplicar e ignorar
config malformada em silêncio, mantendo a anterior.

## 8.7 Rede de segurança

- Gravar o `versionCode` anterior e o caminho do APK anterior em `cacheDir`, mantendo apenas o
  último.
- Contador de crashes no início: se o app crashar nos 2 primeiros arranques depois de uma
  atualização, abrir tela de recuperação com a versão anterior e link para a release antiga.
  Downgrade não instala por cima (o sistema exige `versionCode` maior), então essa tela precisa
  instruir a desinstalação — e avisar que isso apaga o banco. Oferecer exportar antes.
- Tela de histórico de atualizações: versão, quando, resultado, changelog.

---

# Parte 3 — Fase 9: relatório e envio

## 9.1 O relatório precisa ser agregado, não cru

O JSON de 80 amostras cruas tinha 18 KB e exigiu scripts para achar o degrau de quantização. Gerar
um relatório pré-agregado em Markdown, de 1 a 3 KB, contendo:

- Aparelho: fabricante, modelo, Android, capacidade implícita mediana
- Período, cobertura real (%), número e duração total de gaps
- Degrau de quantização detectado e a incerteza resultante no intervalo de amostragem
- Calibração: unidade e sinal do `CURRENT_NOW` detectados
- Split tela ligada / desligada: horas, mAh, mA médio, contagem de janelas de alta confiança
- Baseline de idle (percentil 10 do dreno com tela desligada em janelas ≥300s)
- Dreno por hora do dia, últimos 7 dias
- Top 10 apps por mAh estimado, com minutos em primeiro plano, mA médio e flag de confiança
- Bucket de tela desligada / segundo plano, sempre presente
- Médias de brilho, tipo de rede, % do tempo com localização ativa
- Faixa de temperatura e máxima

No fim, um bloco de **ressalvas geradas automaticamente**: cobertura abaixo de 70%, janelas de
baixa confiança acima de 30%, degrau de quantização grosseiro, permissão de uso não concedida.
Quem analisar precisa saber onde o dado é fraco antes de concluir.

## 9.2 Botão de enviar — share sheet como caminho principal

`ACTION_SEND` com `EXTRA_TEXT` (relatório Markdown + frase de instrução) e, opcionalmente, o JSON
cru anexado via `EXTRA_STREAM` com `FileProvider`. Funciona com o app do Claude, WhatsApp, Drive,
e-mail, qualquer coisa, e não depende de esquema de URL de terceiro.

Dois botões na tela de relatório: "Compartilhar relatório" e "Copiar".

## 9.3 Deeplink — o que a documentação realmente diz

Verificado na documentação oficial:

- O esquema `claude://` no app mobile cobre apenas rotas do Claude Code: `claude://code`,
  `claude://code/new?q=...`, e universal links em `https://claude.ai/code/...`. Depende de ter
  acesso ao Claude Code na conta.
  https://support.claude.com/en/articles/14898120-open-the-claude-mobile-app-with-a-link
- `claude://claude.ai/new?q=...` está documentado para desktop, não mobile, com `q` truncado por
  volta de 14.000 caracteres.
  https://support.claude.com/en/articles/14729294-open-claude-desktop-with-a-link
- `https://claude.ai/new?q=...` é a URL web de chat com prompt pré-preenchido. No Android pode ser
  capturada pelo app ou abrir no navegador; sem garantia documentada.

Implementar em cascata, sem prometer o que não é documentado:

1. Botão principal: share sheet (9.2). Sempre funciona.
2. Botão secundário "Abrir no Claude": monta `https://claude.ai/new?q=<relatório URL-encoded>` com
   `Intent.ACTION_VIEW`. Se o relatório encodado passar de ~8.000 caracteres, gerar automaticamente
   uma versão curta (cabeçalho, split de tela, top 5 apps, ressalvas) e avisar na UI que foi
   truncado.
3. Se nada resolver o intent, cair para o share sheet com aviso curto.

Não hardcodar `claude://` como caminho principal e não assumir que o app captura o universal link.
Tratar `ActivityNotFoundException` em todos os casos.

## 9.4 Frase que acompanha o relatório

Prefixar o texto enviado com:

```
Relatório do meu app de monitoramento de bateria (Android, sideload).
Os dados são estimativa por correlação, não medição por app.
Leia as ressalvas no fim antes de concluir. O que dá para melhorar?
```

---

# Ordem de execução

1. **7.1, 7.2 e 7.4** — as correções que tornam o dado confiável. Nada mais importa antes disso.
   Parar e mostrar.
2. **7.3, 7.5, 7.6** — robustez da coleta e novos campos. Parar e mostrar.
3. **8.1 a 8.4** — keystore, CI, checagem, verificação. Parar e mostrar.
4. **8.5 a 8.7** — cascata de instalação, config remota, rede de segurança. Parar e mostrar.
5. **9.1 a 9.4** — relatório e envio.

Antes de escrever qualquer código: confirmar na documentação oficial as APIs de `PackageInstaller`,
os nomes exatos das permissões da Parte 2, e em que nível de API cada uma foi introduzida. Não
escrever de memória — versão ou constante inventada é o erro mais comum aqui.

# Regras de código (mantidas da spec original)

- Camada `domain` em Kotlin puro, sem `android.*`, testável em JVM.
- Testes unitários para toda a matemática de janela adaptativa, incluindo quantização grosseira,
  gap no meio da janela e `chargeCounter` travado.
- Sem `!!` fora de teste.
- Strings em `strings.xml`, em português.


---

# Adendo — correção factual da premissa sobre BATTERY_STATS

*(escrito em 2026-09-01, depois de pesquisa em fonte primária)*

As fases 1 a 9 partiram de que `BATTERY_STATS` é `signature|privileged` e portanto inalcançável.
**Isso está errado.** O `protectionLevel` real no AOSP é:

```xml
<permission android:name="android.permission.BATTERY_STATS"
    android:protectionLevel="signature|privileged|development" />
```

Verificado em `core/res/AndroidManifest.xml` de `refs/heads/main`. O flag `development` significa
concedível por `adb shell pm grant`, e `PermissionManagerServiceImpl.grantRuntimePermission`
aceita explicitamente permissões `development`.

Consequências:

- **O que continua verdadeiro:** mAh medido por app **não existe em nenhum nível de privilégio**,
  nem com root. O framework mede tempo e multiplica por constantes que o fabricante declara em
  `power_profile.xml` ([source.android.com/docs/core/power](https://source.android.com/docs/core/power)).
  A regra "estimativa nunca é apresentada como medição" continua valendo integralmente.
- **O que muda:** com o grant, `SystemHealthManager.takeUidSnapshot(uid)` — **SDK público desde a
  API 24**, sem reflection e sem API oculta — entrega, por app: wakelocks parciais com tag,
  contagem e duração; tempo de GPS, câmera, áudio, vídeo, scans; jobs e syncs; tempo em cada
  estado de processo (top, foreground, foreground service, background, cached); CPU e bytes de
  rede. Tudo contador medido.
- **A regra revogada:** "não declarar `BATTERY_STATS` no manifest". Ela precisa ser declarada,
  senão o `pm grant` falha — o PackageManager recusa conceder permissão que o app não pediu.
  Declarar não concede nada numa instalação normal.
- **O grant persiste** através de reinício e das auto-atualizações da Fase 8. Some só ao
  desinstalar.

**O que continua fora de alcance sem root:** kernel wakelocks, `/sys/class/power_supply/battery/*`
e `/proc/stat` (bloqueados por SELinux para `untrusted_app`), e desligar o gerenciamento agressivo
da Samsung.

**Decisão de desenho:** o modo avançado mostra os **temporizadores medidos**, não os campos
`MEASUREMENT_*_POWER_MAMS` do `HealthStats`. Aqueles vêm prontos e são tentadores, mas são o mesmo
modelo de sempre com mais decimais. "Wakelock parcial: 47 min" é verificável; "180 mAh" não é.

**Não adotado de propósito:** `adb shell settings put global hidden_api_policy 1`, que GSam e
BetterBatteryStats instruem. Desliga a proteção de API oculta do aparelho inteiro, para todos os
apps, em troca de `BatteryUsageStats` — cujos números continuam modelados.
