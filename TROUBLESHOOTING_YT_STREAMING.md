# Troubleshooting YouTube Music Streaming Failures

This doc exists because YouTube's InnerTube API is unofficial and undocumented — Google
changes client requirements, PoToken/SABR enforcement, and per-client login rules without
notice. This is *not* caused by NexMusic's old `echomusic.fun` backend being discontinued;
core streaming has always been resolved on-device against `music.youtube.com` directly
(see `innertube/` module) — no NexMusic-owned backend is in the loop for playback.

## Background concepts

- **InnerTube client**: YouTube's internal API pretends every request comes from a specific
  official app/client (WEB_REMIX, ANDROID_VR, TVHTML5, etc — see
  `innertube/src/main/kotlin/com/music/innertube/models/YouTubeClient.kt`). Each client has
  different rules for login, PoToken, and what content it's allowed to serve.
- **PoToken**: A proof-of-origin token YouTube increasingly requires to prove the request
  came from a real browser/app, not a script. Generated on-device here via a hidden WebView
  running Google's challenge JS — see `app/src/main/kotlin/com/nexapp/nexmusic/utils/potoken/`.
- **Cipher / n-transform**: YouTube obfuscates the final stream URL's signature and `n`
  parameter; must be deciphered to get a working URL. See
  `app/src/main/kotlin/com/nexapp/nexmusic/utils/cipher/CipherDeobfuscator.kt` and
  `.../sabr/EjsNTransformSolver.kt`.
- **SABR**: YouTube's newer server-side adaptive bitrate streaming mode; not all client types
  return classic progressive/adaptive `adaptiveFormats` under SABR enforcement, which is part
  of why some clients silently return empty format lists instead of an explicit error.

## Where the logic lives

All playback resolution is in `app/src/main/kotlin/com/nexapp/nexmusic/utils/YTPlayerUtils.kt`:

1. `mainClientFor(isLoggedIn)` picks the first client to try (via `RemoteStreamingConfig`).
2. If that fails, `streamFallbackClientsFor(isLoggedIn)` provides an ordered list of clients
   to retry against, skipping any that require login the user doesn't have.
3. For each client that returns `playabilityStatus == OK`, we try to find an audio format,
   resolve its stream URL (direct / cipher / NewPipe fallback), and validate it with a HEAD
   request before committing to it.
4. Client selection is centralized in
   `app/src/main/kotlin/com/nexapp/nexmusic/utils/RemoteStreamingConfig.kt` — **this is the
   file to edit for a fast hotfix** when a client type breaks. It also has an
   `applyRemoteOverrides(json)` entry point so a future remote-config fetch can change
   client ordering without an app release (not wired to a live fetch yet — see TODO there).

## 2026-08 incident: new (non-downloaded) tracks failing with LOGIN_REQUIRED

**Symptom**: downloaded tracks play fine; any new/non-downloaded track logs
`Unplayable content detected: videoId=..., status=LOGIN_REQUIRED` repeatedly, even though
the user is logged into a YouTube account in-app.

**Root cause**: `MAIN_CLIENT` was hardcoded to `ANDROID_VR_1_43_32`, which has
`loginSupported = false` in `YouTubeClient.kt` (the code comment on the sibling
`ANDROID_VR_1_61_48` literally says *"This client can only be used when logged out"*).
`YouTubeClient.toContext()` only attaches `onBehalfOfUser = dataSyncId` `if (loginSupported)`
— so this client **always requested as an anonymous guest**, regardless of the user's actual
login state in the app. YouTube tightened guest-session restrictions around August 2026,
so this previously-reliable guest bypass client started returning `LOGIN_REQUIRED` for
nearly everything. The fallback chain would eventually reach an authenticated client
(e.g. `WEB_CREATOR`) and sometimes resolve a player response, but retries/instability
followed because the primary path was fighting the guest restriction on every call.

**Fix applied**: `mainClientFor(isLoggedIn)` now picks `WEB_REMIX` (authenticated,
`loginSupported = true`, PoToken-capable) as the main client when the user is logged in, and
only uses the guest VR client for actual guests. The fallback ordering was likewise split
into a logged-in list (authenticated clients first) and a guest list (unchanged). See
`RemoteStreamingConfig.kt`.

> **Superseded by hotfix v2 (2026-08, later):** live logcat evidence showed `WEB_CREATOR`
> resolving successfully far more consistently than `WEB_REMIX` as MAIN_CLIENT, so
> `mainClientLoggedIn` was moved to `WEB_CREATOR`. See the dated comment block directly
> above `mainClientLoggedIn` in `RemoteStreamingConfig.kt` for the current source of truth —
> this section is kept for incident history, not as the current behavior.

## 2026-08-29 incident: WEB_CREATOR misses falling through 3 dead clients before recovering

**Symptom**: when `WEB_CREATOR` (MAIN_CLIENT) fails validation (HTTP 403), some tracks take
2-4s extra to start, and a smaller number stall for up to ~60s or never start.

**Root cause**: `fallbackLoggedIn` still listed `TVHTML5, MWEB, IOS` *before* `WEB_REMIX`,
even though this file's own hotfix-v2 note already documented that TVHTML5/MWEB/IOS return
UNPLAYABLE/403 for nearly every video post-2026-08, while WEB_REMIX resolves. Every
WEB_CREATOR miss burned three guaranteed-dead round trips (each a full player-response
fetch, and for PoToken-requiring clients, potentially a WebView spin-up) before ever
reaching WEB_REMIX, which explains both the few-second stalls and the rare full-minute
hangs when a track also failed on WEB_REMIX and had to burn through the rest of the list.

**Fix applied**: reordered `fallbackLoggedIn`/`fallbackGuest` to try `WEB_REMIX`
immediately after the main client, ahead of `TVHTML5`/`MWEB`/`IOS`.

## How to diagnose a similar break fast

1. Reproduce with logcat filtered to the relevant tags (Windows: `findstr`, macOS/Linux: `grep`):
   ```
   adb logcat | findstr /I "YTPlayerUtils PlaybackException BotDetection CipherDeobfuscator"
   ```
2. Look for the per-attempt trail line: `Successfully obtained playback data ... Trail: ...`
   or, on total failure, `Bad stream player response - all clients failed. Trail: ...`.
   The trail (e.g. `WEB_REMIX:OK ANDROID_CREATOR:LOGIN_REQUIRED ...`) shows exactly which
   client(s) were tried and their resulting `playabilityStatus` in order — no manual repro
   needed to see the failure pattern across clients.
3. Cross-reference the failing client(s) against `YouTubeClient.kt`: check `loginRequired`,
   `loginSupported`, and `useWebPoTokens` for a mismatch with the account's actual state
   (e.g. a client claiming `loginSupported = false` silently drops an active login, as above).
4. If PoToken generation itself is failing (`PoToken generation failed: ...` in logs), the
   issue is likely in `utils/potoken/PoTokenWebView.kt` — YouTube changed the challenge JS.
   This needs re-deriving the challenge extraction, not just a client reorder.
5. If everything returns `OK` but playback still stalls/loops, suspect SABR: the resolved
   URL may need the `pot=` streaming-data PoToken appended (already handled for
   `useWebPoTokens` clients) or the CDN itself may be rejecting the client's IP/UA combo.
6. Once root-caused, prefer changing `RemoteStreamingConfig.kt` defaults over touching the
   resolution loop in `YTPlayerUtils.kt` — keeps the fix isolated and easy to review/revert.

## Known constraints / non-goals of this fix

- This sandbox/investigation had no network access to `youtube.com` / `googlevideo.com`, so
  the fix above is based on static analysis of `YouTubeClient.kt`'s own documented
  `loginSupported` semantics plus the logcat trail provided, not a live reproduction against
  YouTube's servers. **Test on-device before shipping** (both FOSS and GMS variants, logged
  in and as guest) using the logcat command above.
- `applyRemoteOverrides()` is not yet wired to a live remote-config fetch — it's a hook for
  a future change, not a shipped feature. Today's fix is a static default change.
- Offline playback, downloads, and non-YouTube sources (kugou, simpmusic, local media) are
  untouched by this change — only the YouTube client/PoToken selection logic was modified.
