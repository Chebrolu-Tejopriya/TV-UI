# Spotify home controls

The native Android home screen observes only `com.spotify.tv.android` through Android's media-session API. It displays album artwork, song title, artist, progress, and Previous / Play-Pause / Next controls. A green animated indicator under the Spotify dock icon means playback is active; a static pause symbol means Spotify is paused. The bars are a playback animation, not an audio spectrum.

The player is now a black Dynamic Island in the header gap between date/time and the right-side buttons. It shows artwork, track, artist, and animated playback bars in its compact state. Remote focus smoothly expands the capsule to reveal progress, elapsed/duration, and Previous / Play-Pause / Next. It remains visible alongside YouTube recents.

From a dock app other than YouTube, press Up to select Play/Pause. From YouTube, Up first selects the video cards; Up again enters the island. Left and Right choose controls, and OK activates the selected control. Right beyond Next moves to Live TV in the header; Left from Live TV returns to Next. Down returns to the content row or dock, collapsing the island. Home Settings > Spotify widget hides the island while retaining the dock indicator. The island is hidden when there is no Spotify playback session, and fades out with the home screen when All Apps or the drawer opens.

Android requires Notification access to expose other apps' media sessions to this unprivileged launcher. The service is labelled **Free My TV - Spotify controls** and is protected by `BIND_NOTIFICATION_LISTENER_SERVICE`. Android grants broad notification access, but this implementation does not read notification contents, capture audio, store playback data, or upload playback data. All controls target Spotify's own media controller, so they cannot accidentally control YouTube or Netflix.

The TV owner explicitly approved this access on 2026-10-21. The grant added `io.freemytv/.SpotifyListener` without replacing the existing notification listeners. A later requested YouTube-recents feature reuses this component, whose current display label is **Free My TV - Media controls**; see `youtube-more-apps.md` for its separate local YouTube storage. Spotify data is still not stored. To remove access later, turn this service off in Android's Notification access settings; with an authorized ADB connection, the equivalent command is:

```powershell
adb -s TV_IP:5555 shell cmd notification disallow_listener io.freemytv/.SpotifyListener 0
```

Disabling access clears the controller and shows a setup card instead of playback information. Uninstalling the launcher also removes its listener service. Disabling debugging does not revoke an already granted notification listener.

The implementation uses Android framework APIs available before this project's minimum Android version (API 28), with no added libraries or network dependencies. Bars update at most every 80 ms, progress once per second, and home drawing stops when the activity is paused.

## Device validation

Built, signature-verified, and installed on the TCL Android 9 TV using the existing signing key and an in-place update. The original APK and complete app sources are saved in `../backups/before-spotify-widget-20261021-210429`.

- Verified live Spotify metadata, artwork, duration, position, and Playing/Paused states on the home screen.
- Verified Play/Pause using the widget with actual Spotify session transitions `3 -> 2 -> 3` (Playing, Paused, Playing).
- Verified focused button visibility and dock playback/pause indicators in screenshots.
- Compared successive home screenshots and confirmed that the playing bars change between frames.
- Initial Next/Previous checks on a single-song queue left that song paused at 0:00. After the owner started a playlist, Next changed to the next track and Previous returned to the earlier track, with playback continuing and metadata updating. Previous may first restart the current song, as in Spotify itself.
- No launcher crash was reported during these checks.
- A dock navigation check during music playback rendered 394 frames: median 8 ms, 95th percentile 12 ms, 6 janky frames (1.52%).

The still flower wallpaper is selected. This feature does not call Magnific or spend generation credits.

## Header island update, 2026-10-22

The island is centered on the screen's exact horizontal midpoint (x = 960 at 1920 × 1080). Available width is limited symmetrically using the date/time and the right-side buttons, so expanding it preserves that center without covering either. The header uses a compact date such as `Thu, Oct 22` and 12-hour time with AM/PM, such as `12:14 AM`. Its normal size is 600 × 84 in the 1920 × 1080 design space; selected size is up to 760 × 160. Native spring animation controls this expansion. Rounded artwork uses a cached BitmapShader; no web view, animation library, or additional permissions are used. Transient notices move below the island.

Verified compact and expanded layouts on the actual TCL TV with Spotify playing, simultaneous YouTube recents, live song/artwork changes, real pause/resume transitions (Playing -> Paused -> Playing), navigation to Live TV and back, and return to the dock. Final navigation/expansion check: 686 frames, 8 janky frames (1.17%), median 8 ms, 95th percentile 13 ms. No launcher exception appeared in the checked runtime log.

Installed APK SHA-256: `9620b67f0364fba358fa450ab6311d0a13ca715ad03592ee2dbf699d62f5f2ea`. Previous sources and APK: `../backups/before-spotify-island-20261021-235314`.

The subsequent exact-centering/short-date update is APK SHA-256 `c9b886f5591c5f3ee78f2a4212015f2861a0855e95cbac176da0d445d3a4b33a`, with backup `../backups/before-centered-island-short-date-20261022-001214`. Verified both island sizes on the TV, the displayed abbreviated date and AM time, and no overlap with the header text or buttons. Pixel measurements of the screenshots confirm both island centers at x = 960.
