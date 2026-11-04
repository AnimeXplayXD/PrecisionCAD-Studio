# Application Identity, Trademark & Visual Asset Copyright Policy

While the **PrecisionCAD Studio** source code is open-source under the [Apache License, Version 2.0](LICENSE), **the application identity, product name, logos, and all visual/icon assets remain proprietary and copyrighted (`All Rights Reserved`)** to preserve the integrity and authenticity of the official application.

This policy works in conjunction with **Section 4(d) (`NOTICE`)** and **Section 6 (`Trademarks`)** of the [Apache License, Version 2.0](LICENSE) and the root [`NOTICE`](NOTICE) file.

---

## 1. Dual-Layer Licensing Scope

| Repository Component | Applicable License / Legal Status | Unrestricted Reuse Allowed? |
| :--- | :--- | :--- |
| **Kotlin / Compose Source Code, Shaders, Build Scripts, Tests, and Technical Documentation** | [Apache License, Version 2.0](LICENSE) | **Yes** (subject to Apache-2.0 terms and preservation of [`NOTICE`](NOTICE)) |
| **Bundled Typography (`app/src/main/res/font/*.ttf`)** | [SIL Open Font License 1.1](third_party_licenses/OFL-1.1.txt) | **Yes** (subject to OFL-1.1 terms; see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)) |
| **Application Name (`PrecisionCAD Studio`), Package Identity (`com.precisioncad.studio`), and Brand Marks** | **Proprietary Trademark & Product Identity** (Excluded by Apache-2.0 Section 6) | **No — All Rights Reserved** |
| **Application Icons, Adaptive Launcher Artwork, and Visual Brand Assets** | **Copyright © 2026 AnimeXplayXD — All Rights Reserved** (Excluded from Apache-2.0 Copyright Grant) | **No — All Rights Reserved** |

---

## 2. Reserved Application Identity & Trademarks

In accordance with **Section 6 (Trademarks)** of the Apache License, Version 2.0, the open-source license does **not** grant any right or license to use the following protected identifiers:

- **Application Name & Wordmarks**: **`PrecisionCAD Studio`** and **`PrecisionCAD`**
- **Official Android Application Identifier**: **`com.precisioncad.studio`** (when used to identify published binaries, APK/AAB packages, or app store listings)
- **Official Repository Brand Identity**: Visual identity and emblem associated with `AnimeXplayXD/PrecisionCAD-Studio`

---

## 3. Copyrighted Visual Assets & Icons (All Rights Reserved)

All application icons, adaptive launcher layers, raster mipmaps, vector launcher drawables, and brand artwork in this repository are **Copyright © 2026 AnimeXplayXD — All Rights Reserved**. They are **explicitly excluded** from the Apache License, Version 2.0 copyright license grant:

- `docs/assets/readme/precisioncad-studio-app-icon.png`
- `app/src/main/res/drawable-nodpi/precisioncad_foreground.png`
- `app/src/main/res/drawable-nodpi/precisioncad_background.png`
- `app/src/main/res/drawable-nodpi/precisioncad_monochrome.png`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/mipmap-mdpi/ic_launcher.png` & `app/src/main/res/mipmap-mdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-hdpi/ic_launcher.png` & `app/src/main/res/mipmap-hdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher.png` & `app/src/main/res/mipmap-xhdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` & `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` & `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png`

---

## 4. Prohibited Uses (No Unrestricted Use)

Without express prior written permission from the copyright holder (`AnimeXplayXD`), you may **NOT**:

1. **Redistribute Binaries Under the PrecisionCAD Studio Identity**: Build, publish, or distribute APK/AAB binaries, Play Store / F-Droid listings, or release packages that retain the **`PrecisionCAD Studio`** name, the **`com.precisioncad.studio`** `applicationId`, or any of the copyrighted launcher icons and visual assets listed in Section 3.
2. **Extract or Reuse Visual Assets Everywhere**: Copy, modify, redistribute, or embed the **PrecisionCAD Studio** icon or visual artwork in other software applications, websites, commercial products, marketing materials, or design libraries.
3. **Imply Official Endorsement**: Use the **PrecisionCAD Studio** name or visual identity in a way that suggests a fork, derivative work, or third-party distribution is authored, endorsed, or maintained by the original project owner.

---

## 5. Mandatory Requirements for Forks & Derivative Builds

If you fork this repository and distribute compiled binaries (APKs/AABs) or publish a derivative application publicly, you **MUST** complete all of the following before distribution:

1. **Change the Application Name**: Replace `<string name="app_name">PrecisionCAD Studio</string>` in `app/src/main/res/values/strings.xml` and `rootProject.name` in `settings.gradle.kts` with your own distinct product name.
2. **Change the Android `applicationId`**: Replace `applicationId = "com.precisioncad.studio"` in `app/build.gradle.kts` with your own unique reverse-domain identifier.
3. **Remove and Replace All Copyrighted Icons & Visual Assets**: Delete and replace all files listed in **Section 3** (`docs/assets/readme/precisioncad-studio-app-icon.png`, `app/src/main/res/drawable-nodpi/precisioncad_*.png`, `app/src/main/res/drawable/ic_launcher_*.xml`, and `app/src/main/res/mipmap-*/ic_launcher*`) with your own original artwork.
4. **Retain Attribution Notices**: Preserve the root [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE) files in source distributions as required by Section 4 of the Apache License, Version 2.0.

---

## 6. Permitted Nominative & Local Development Use

- **Source Cloning & Local Compilation**: You may clone this repository onto your local machine for personal study, testing, academic evaluation, and submitting pull requests back to the official repository without removing the visual assets during local development.
- **Nominative Fair Use (Text-Only Attribution)**: You may make truthful, text-only factual statements describing the lineage of your derivative work (for example: *"Based on the open-source source code of PrecisionCAD Studio (`github.com/AnimeXplayXD/PrecisionCAD-Studio`)"*), provided you do not use the copyrighted logos/icons or imply official endorsement.
