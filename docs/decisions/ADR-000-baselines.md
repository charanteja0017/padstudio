# ADR-000 — Baselines (sources, versions, provenance)

Date: 2026-10-11 · Status: accepted

Everything we build or measure against is pinned here so results are reproducible and upstream drift is visible.

## Cloned sources (shallow; not version-controlled in this repo — see `.gitignore`)

| Path | Upstream | Ref | Commit | Commit date / subject |
|---|---|---|---|---|
| `forks/padpen` | github.com/keymapperorg/KeyMapper | `develop` (+ tag `v4.6.1` fetched) | `813028bc749348298200225c610ea928195c1b25` | 2026-10-10 "bump version to 4.6.2" |
| `forks/padpen` @ `v4.6.1` | same | tag `v4.6.1` | `45c0a2ce701d329bd15935f8059dd21686260145` | release used on the device |
| `reference/lawnchair-parts` | github.com/LawnchairLauncher/lawnchair (sparse: `flowerpot/`, `lawnchair/assets/flowerpot/`, `lawnchair/src/app/lawnchair/{flowerpot,icons}/`) | `16-dev` | `4793ba3a238bb1a3e786a3471a6d84767ed05267` | 2026-10-06 "fix: Apply scaling to icon shape preview…" |
| `reference/focuspen-pro-x` | github.com/HMQYHM/FocusPenProX | `main` | `a45f65ef675bb6ff351c53c19c72625adb027078` | 2026-08-20 (reference only — root/LSPosed, never built) |

`forks/padpen`: remote renamed `origin → upstream`; working branch `pad/main` (no changes — the fork is only built if gate G4 fails).

**Schema check:** `git diff --stat v4.6.1 HEAD -- data/ base/src/main/java/io/github/sds100/keymapper/base/backup/` is empty → the backup/entity schema on `develop` is identical to the installed release 4.6.1.

## Upstream APKs (in `apks/`, not version-controlled)

| File | Package / version | SHA-256 (file) | Signer (V2 certificate) |
|---|---|---|---|
| `keymapper-4.6.1-foss.apk` | `io.github.sds100.keymapper` 4.6.1-foss (versionCode 286), targetSdk 37 | `cb26fb63378bfac629721a49e81407cdf541bb4c2159a7bc6279d0b10994565d` | CN=Seth Schroeder … C=UK · cert SHA-256 `2f41a816b8d60aa1d7cd4976b4452b0335e583d4f4ab14071f303177850ce460` |
| `Lawnicons.2.18.0.apk` | `app.lawnchair.lawnicons` 2.18.0 (versionCode 26), targetSdk 37 | `e830b37e1cd7cd66487492f4a1084ba086254b2b09541d729da0ec2169a73bfe` | CN=Lawnchair … C=PL · cert SHA-256 `747c3645b357258b2e23e851e53c96747fe0add007e5ba2cd97e8c85572e4dc5` |
| `Lawnchair.15.0.0.Beta.3.0.apk` | `app.lawnchair` 15.Beta 3 (versionCode 1500020300), targetSdk 35 — **only for gate G2, uninstalled afterwards** | `d4200d0985169fd79ba1bd225d653f2a2fe7b50aa07cb0d05ca64c7623f86059` | CN=Lawnchair … C=PL · cert SHA-256 `747c3645…4dc5` |

Downloaded with `gh release download` from each project's GitHub release; package names checked with `aapt2 dump badging`, signatures with `apksigner verify --print-certs` (build-tools 37.0.0, JDK 17).

## Toolchain (Mac)

- Apple M4, 16 GB RAM, macOS 27.0.1; Android SDK `/opt/homebrew/share/android-commandlinetools` (platform android-37.0, build-tools 36.0.0 + 37.0.0, platform-tools/adb 37.0.1).
- JDK 17 (`/opt/homebrew/opt/openjdk@17`) pinned per project via `org.gradle.java.home`; default `java` is JDK 27 (not used for builds).
- Gradle 9.8.1; `gh` authenticated as `charanteja0017`.
- Not installed on purpose: JDK 21, NDK, Rust (only needed if a fork is triggered — see PLAN §8).
