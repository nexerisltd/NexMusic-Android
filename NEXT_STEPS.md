# NexMusic — Rebrand Complete: What's Left For You

Everything in `NexMusic_Rebrand_Prompt.md` has been executed directly in the codebase (zipped as `NexMusic-Android.zip`). Summary of what was done, plus the handful of things only you can finish.

## ✅ Done in the code
- Package renamed: `iad1tya.echo.music` → `com.nexapp.nexpass` (~490 files)
- App name, theme, all 61 locale files: "Echo Music" → "NexMusic"
- Feature brand names updated too: "Echo Brain" → "NexMusic Brain", "Echo Find" → "NexMusic Find"
- Full new launcher icon set generated from your logo (all densities, adaptive icon + monochrome layer)
- About screen + Welcome dialog: old developer's avatar/links replaced with your identity (Arabi x ARX) and photo; funding section fully removed
- Repo/Discord/Telegram links updated to yours across the whole codebase
- FUNDING.yml emptied, CI donation message removed, automated bot commit identity changed
- Docs (README, CONTRIBUTING, SETUP, PRIVACY_POLICY, SECURITY, CODE_OF_CONDUCT) rewritten
- Privacy Policy: added a disclosure section for the upstream backend features you chose to keep (Canvas, Listen Together, update-check) — transparent, not hidden
- Fastlane/Play Store metadata (title, descriptions) updated
- **Git history wiped and reinitialized** — the old repo's commit history (with the original developer's name on every commit) is gone; you now have a single clean "Initial commit" authored as Arabi x ARX, with `origin` already pointed at `github.com/nexerisltd/NexMusic-Android`
- Third-party library attributions (e.g. the vendored `Kyant0/backdrop` Apache-2.0 code) were **left untouched** — required by that library's license, not part of the old dev's branding

## ⚠️ Only you can do these

**1. Push to your GitHub repo**
```bash
cd Echo-Music   # (unzip NexMusic-Android.zip first)
git push -u origin main
```

**2. Generate a NEW release keystore** (never reuse the original developer's):
```bash
keytool -genkeypair -v -keystore keystore/release.keystore \
  -alias nexmusic -keyalg RSA -keysize 2048 -validity 10000
```
Then set `STORE_PASSWORD` and `KEY_ALIAS` as GitHub Actions secrets (used by `.github/workflows/gradle.yml`), and keep the `.keystore` file itself out of git (already gitignored).

**3. Confirm the open items** before your first public release:
- Telegram handle: I used `arabiislam46r` exactly as you typed it — confirm it's not a typo for `arabiislam46ar`
- Discord row in the About screen currently shows your username as plain text (not clickable) — send me a numeric Discord user ID if you want it to open a profile link
- Confirm `nexmusicog.vercel.app` has live `/download`, `/obtainium`, `/p/privacy-policy`, `/p/toc` pages, or tell me and I'll point those buttons elsewhere for now
- Weblate project `hosted.weblate.org/projects/nexmusic/` doesn't exist yet — either create it there or remove the translation badges/links from README until it does

**4. Try a real build once you're on a machine with full network access** — I verified everything statically (brace balance, XML validity, resource-reference audit) but couldn't run `./gradlew build` in this sandbox (Gradle's distribution download is blocked here). Run a real build before you ship.

## 📦 Files delivered
- `NexMusic-Android.zip` — the full rebranded source tree, ready to unzip and push
- `NexMusic_Rebrand_Prompt.md` — the original spec (for reference / audit trail)
