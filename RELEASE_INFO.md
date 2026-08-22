# NexMusic v5.2.90

### Bug Fixes
- **YouTube Playback Reliability**: Fixed a burst-request pattern where the app could fire two full client-fallback attempts at nearly the same instant (e.g. while preloading the next track), which could trip YouTube's rate limiting and cause playback failures. Playback requests are now properly serialized.
- **Track Skipping**: Fixed a bug where quickly skipping tracks could cause the playback resolver to burn through every fallback client unnecessarily before reporting failure, sometimes surfacing a confusing "sign in to confirm you're not a bot" error that wasn't the real cause.
- **Faster Failure Recovery**: Stream URL validation now fails fast on a slow/unresponsive CDN edge instead of hanging for up to 20 seconds, so a bad fallback attempt no longer stalls playback.
- **Better Diagnostics**: Playback logs now capture the exact reason a stream URL validation failed (HTTP status, client, timing) instead of a generic failure, making future playback issues much faster to diagnose and fix.

---


We have completely removed lossless music streaming, downloads, and tracking features. Maintaining the lossless music database is expensive and quite difficult. Soon, the entire lossless database will be archived from GitHub as well. 

We tried our best to maintain it, and while many of you have asked us to use a free cloud service instead, the process requires automation (where a user can upload a track and it gets added automatically). Doing this manually for every track simply isn't viable. Thank you for understanding.
