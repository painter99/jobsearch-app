# Jobsearch

**Research/dossier aplikace pro Android** — hloubkové prověřování nabídek práce v ČR nad oficiálními otevřenými daty. Aplikace **nescrapuje a nerestribuuje obsah inzerátů**: výsledky hledání jsou **deep linky**, každý inzerát si uživatel otevře sám u zdroje. Nic se neodešle za uživatele („assistant never clicks for you").

## Pilíř č. 1: agentura detector + resolver koncové firmy

- **Detekce agenturní nabídky** — primárně oficiální [seznam agentur práce MPSV](https://data.mpsv.cz/od/soubory/agentury-prace/agentury-prace.json) (§435/2004 Sb., ~1 900 agentur), doplněk ARES VR (předmět podnikání „Zprostředkování zaměstnání").
- **Návrh kandidátů koncové firmy** — stopy z inzerátu → ARES (CZ-NACE, region, velikost firmy) → kandidáti s confidence a zdůvodněním. **Nikdy ne jako fakt — potvrzuje uživatel.**

## Zdroje dat

| Zdroj | Typ | Kadence | Poznámka |
|---|---|---|---|
| [ÚP ČR/MPSV — Volná místa](https://data.mpsv.cz/od/soubory/volna-mista/volna-mista.json) (~187 MB) | otevřená data | jednorázový bootstrap | obsahuje osobní údaje → ukládáme jen pole z whitelistu (GDPR) |
| [ÚP ČR/MPSV — Přírůstky volných míst](https://data.mpsv.cz/od/soubory/volna-mista-prirustek/) (~1 MB gz/den) | otevřená data | denní sync | novy/zmeneny/zruseny; `urlAdresa` = hotový deep link na portál ÚP |
| [MPSV — Agentury práce](https://data.mpsv.cz/od/soubory/agentury-prace/agentury-prace.json) | otevřená data | denně | ukládáme jen množinu IČO |
| [MPSV — číselník obcí](https://data.mpsv.cz/od/soubory/ciselniky/obce.json) (6 258 obcí) | otevřená data | občas | autocomplete lokalit + join RÚIAN kódů |
| [ARES REST](https://ares.gov.cz/ekonomicke-subjekty-v-be/rest) (MF ČR) | otevřené API | on-demand | limit 500 dotazů/min, bez tokenu |
| [prace.cz](https://www.prace.cz) výpisové stránky (sitemap index) | deep linky | **opt-in, default OFF** | ToS §4.7(e) → in-app disclosure + rate limit |

## Datové vrstvy (M1.4)

- **Bootstrap:** full dump MPSV (187 MB) se stahuje jednorázově streamem do souboru (nikdy celý v paměti) a parsuje streamujícím JSON parserem (Moshi `JsonReader.nextSource()`) — do lokálního úložiště se zapisuje jen whitelistovaná podmnožina (~12 MB JSON Lines). Osobní údaje (kontaktní osoby, telefony, e-maily) se do modelu nemapují a na disk se nedostanou.
- **Denní sync:** přírůstek `volna-mista-prirustek-YYYY-MM-DD.json.gz` (~1 MB gz, ~1 650 záznamů/den) — `novy` = insert, `zmeneny` = update, `zruseny` = delete. Chybějící den (HTTP 404) je běžný stav publikace MPSV — přeskočí se a zkusí se příště. Catch-up po offline dnech projde všechny dny od poslední kotvy.
- **prace.cz (opt-in):** stahuje JEN výpisové stránky sledovaných lokalit (`/nabidky/{kraj}/{obec}/`), extrahuje deep linky na detaily inzerátů (bez tracking parametrů `?rps=`/`utm_*`). Nikdy se nestahuje obsah inzerátů. Volá se pouze na explicitní opt-in uživatele (default OFF).

## Funkce (plán dle PRD v0.2)

- **Filtry celorepublikově:** směna, mzda, lokalita (obec/okres/kraj; autocomplete přes oficiální číselník 6 258 obcí).
- **Dossier k nabídce:** primární inzerát (deep link) → rejstřík (ARES) → checklist → evidence matrix → verdikt GO/PODMÍNĚNĚ/VYŘAZENO.
- **Checklist auto-fill:** strukturovaná pole z MPSV dat („Dovolená navíc", „Zvláštní prémie"…) předvyplňují checklist automaticky.
- **AI volitelně (OpenRouter BYOK):** seřazení/shrnutí dossieru s vlastním klíčem uživatele; aplikace je plně funkční i bez klíče. AI vždy jen doporučuje.
- **Offline-first:** vše lokálně (Room), žádný backend, žádný server.

## Status

M0.1 — skeleton (Kotlin + Compose + Hilt), CI pipeline (šablona [WSW Olomouc](https://github.com/painter99/wsw-olomouc)). PRD v0.2 schváleno 6. 10. 2026. Další milníky: M1.1 core-models/filters → M1.2 detekce agentury → M1.3 resolver → M1.4 data MPSV → M1.5 storage → M1.6 UI. Python referenční vrstva slouží jako testovací oracle (stejné vstupy → stejné výstupy).

## Tech stack

Kotlin, Jetpack Compose, Hilt, OkHttp, Moshi (JSON streaming), jsoup (HTML výpisy), Room, DataStore, WorkManager. JDK 17, Android SDK 35 (minSdk 26).

## Build

```bash
./gradlew assembleDebug       # build
./gradlew testDebugUnitTest   # unit testy
```

CI (GitHub Actions) builduje APK na každý push; logy se zrcadlí na veřejnou větev `ci-logs`; tag `v*` publikuje GitHub Release s APK.

## Licence a atribuce

MIT — viz [LICENSE](LICENSE). Žádný kód z GPL/proprietary projektů (čistá místnost).

Atribuce: ÚP ČR / MPSV (otevřená data), ARES / MF ČR, prace.cz (zdroj deep linků), OpenRouter (volitelná AI vrstva).

**GDPR:** z MPSV dat se osobní údaje (jména, telefony, e-maily kontaktních osob) **neukládají** — parser drží explicitní whitelist povolených polí, vše ostatní zahodí.