# Release notes

Konvence: jedna release note = jeden soubor `vX.Y.Z.md` v tomto adresáři (název = tag).

Release workflow (`.github/workflows/release.yml`) automaticky připojí `docs/release-notes/{tag}.md` k GitHub Release pro tag `v*`. Pokud soubor chybí, použije se default text.