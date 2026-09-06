# Couch Controls - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Drop the jar in your client's `mods` folder alongside its declared dependencies (see `fabric.mod.json`). No server-side installation needed. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Building

Couch Controls compiles against Pandorical's live source for `NavigableScreen`, not a published artifact: `settings.gradle` includes `../pandorical`. Check both out side by side or the build fails before it starts. Pandorical is still only a *runtime* soft dependency; the compile-time one is what makes the integration drift into a build error instead of a silent no-op.

```bash
./gradlew build
```

The Minecraft dependency is `>=26.3-alpha.1`, not the suite's usual `>=26.2`: SDL input is verified present on the 26.3 snapshots and nowhere earlier. **Write it as `alpha`, not `snapshot`** — Fabric normalizes `26.3-snapshot-8` to the semver prerelease `26.3-alpha.8`, so a predicate written against the Mojang spelling silently matches nothing and the mod refuses to load on the exact version it targets. The build stays green either way; only a launch catches it.
