# NexMusic v5.5.0

### In-app update — everything now happens inside the app
- **No more website redirects**: the update dialog, the Settings → System update screen and the update notification all open the built-in download screen. The APK downloads in-app with a live progress bar and installs with a single tap.
- **Background auto-download**: once an update is detected, the APK downloads automatically in the background — by the time you tap, it is ready to install (no manual download, no reinstall dance).
- **Fixed update notification**: a non-dismissible "Update available" notification stays in the shade next to the player notification (with an Update action) until the app is actually updated. It clears itself once you are on the latest version.
- **Correct update detection**: version comparison now properly understands semver and beta tags — older releases no longer trigger false "update available" prompts.
- **Private release repos supported**: updates keep working whether the release repository is private or public (token handled automatically in CI builds).
- Rich release notes (changelog.json) are now generated automatically for every release.

### Fixes for already-shipped behavior
- Tapping "Update" on the startup update dialog now reliably opens the update screen (the previous navigation could silently do nothing).
- "Update ready" notifications are fixed in the shade until installed, so they cannot be lost.
