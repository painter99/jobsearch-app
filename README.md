# Jobsearch

Research/dossier Android aplikace pro hloubkové prověřování nabídek práce v ČR.

**Filozofie „deštník" (Model A):** aplikace **nescrapuje a nerestribuuje obsah inzerátů** — výsledky hledání jsou **deep linky**, uživatel si každý inzerát otevře sám u zdroje. Data: oficiální otevřená data (ÚP ČR/MPSV, ARES) + výpisové stránky prace.cz (sitemap index).

**Pilíř č. 1:** detekce agenturní nabídky (ARES VR „Zprostředkování zaměstnání") + návrh kandidátů koncové firmy s confidence a zdůvodněním — **nikdy ne jako fakt, potvrzuje uživatel**.

## Stav

M0.1 — skeleton (Kotlin + Compose + Hilt), CI pipeline (WSW Olomouc template).
PRD v0.1 schváleno 6. 10. 2026 (android-spec-first, Pavel = validátor).

## Zdroje dat

- ÚP ČR/MPSV — [Volná místa za celou ČR](https://data.mpsv.cz/od/soubory/volna-mista/volna-mista.json) (otevřená data; osobní údaje nestahujeme — GDPR whitelist)
- ARES — [ekonomické subjekty REST](https://ares.gov.cz/ekonomicke-subjekty-v-be/rest)
- prace.cz — výpisové stránky `/nabidky/{kraj}/{obec}/` (sitemap index; deep linky only)

## Licence

MIT. Vlastní kód; žádný kód z GPL/proprietary projektů (čistá místnost).

## Atribuce

ÚP ČR / MPSV (otevřená data), ARES / MF ČR, prace.cz (zdroj deep linků).

## Build

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

CI (GitHub Actions) builduje APK artefakt na každý push; logy se zrcadlí na větev `ci-logs`.