# YouTube recents and More Apps

Installed on the TCL Android 9 TV on 2026-10-21. The previous app sources and signed APK are in `../backups/before-youtube-more-apps-20261021-213522`.

## Using the home screen

- Selecting the YouTube dock tile shows three centered video cards. Up selects a card, Left/Right move between saved videos, OK opens its YouTube action, and Down returns to the dock.
- The list contains up to three distinct videos observed playing on this TV since the update, newest first. Watching one again moves it to the front. Empty slots explain that another video must be watched.
- The Spotify player now occupies a separate Dynamic Island in the header, so YouTube recents and Spotify playback controls can appear together. The Spotify dock indicator continues to follow playback.
- More Apps is fixed after the saved dock apps, currently after MagiConnect. It uses the existing glass appearance and opens All Apps in a centered four-column grid. Up/Down scroll through additional rows, OK launches an app, and Back returns to the dock. The list includes dock apps as well as other launchable TV apps, retaining the existing exclusions for hidden system entries.
- More Apps cannot be reordered into the saved package list. App reordering stops before this fixed final action.

## YouTube limits and privacy

YouTube's [official API documentation](https://developers.google.com/youtube/data/reference/rest/v3/playlistItems/list) explicitly reports `watchHistoryNotAccessible` for account watch-history requests. The TV's public Watch Next provider was also empty. This feature therefore observes YouTube's Android media session instead of trying to import account history, read its private files, or use Google sign-in cookies.

YouTube 5.30.340 on this TV provides title, channel, duration, and thumbnail artwork, but no video ID or direct video link. Cards honestly show **OK · Search in YouTube** and open YouTube's own title search. The exact video appeared first in the verified search result. If another YouTube version supplies a video ID or video thumbnail URL, the same code can open that video directly. Direct playback through a recent card is not claimed for the current TV version.

The existing, explicitly approved notification-listener component remains `io.freemytv/.SpotifyListener`; its display label is now **Free My TV - Media controls**, because it supports both Spotify controls and YouTube recents. No additional notification-access grant was made. The service does not implement notification-content callbacks. Spotify metadata is still not saved.

YouTube recents are stored only in the launcher's private app data: a three-entry metadata list and small JPEG thumbnails, at most 480 × 270. Old thumbnails are removed as entries expire, and the drawing cache discards obsolete bitmaps. No history is uploaded, and no network requests were added to the launcher. An explicit card activation hands the chosen title or supplied video ID to the installed YouTube app.

Revoking **Free My TV - Media controls** in Android Notification access stops observation and Spotify controls. Already saved YouTube cards remain local until the launcher's app data is removed or it is uninstalled. Do not clear app data or uninstall while relying on Free My TV mode without first restoring Original mode, since doing so also removes the existing launcher configuration and recovery state.

## Verification

- Build, APK alignment, API 28 minimum, ZIP contents, and the existing signing certificate were verified.
- Installed APK SHA-256: `257f24bc3d732de82705c8b6eb703288502e027c0472eb8b01d3f9fa5453da58`.
- Played Big Buck Bunny, Sintel, Elephants Dream, and Tears of Steel as test clips. Thumbnails and titles populated the cards; the fourth distinct clip replaced the oldest. These test clips are real playback entries and will be replaced by the owner's subsequent viewing.
- All three entries survived an in-place APK update and a new launcher process.
- Replaying Elephants Dream moved its existing card to the front without adding a duplicate, leaving exactly three cards.
- Selected a recent card with the remote and verified the corresponding YouTube title-search results.
- Verified More Apps placement after MagiConnect, the four-column layout, the first and final grid pages, all 23 entries being reachable, and successful launch of Free My TV settings from the final grid item.
- Held OK on MagiConnect, attempted to move right into More Apps, and verified the fixed action stayed last. Canceled reorder mode without saving a change to the app order.
- Fixed clipping so neighboring off-screen rows do not leave fragments under the fixed title or footer.
- Final dock/card navigation check: 425 rendered frames, zero janky frames, median 8 ms, 95th percentile 11 ms. No launcher crash was reported during these checks.
- Temporary ADB metadata-probe files were removed from the TV; the local diagnostic source was archived with the backup.

The still flower wallpaper is selected; YouTube recents do not use generation services.

## Follow-up correction: installation samples and duplicate cards

The owner reported that the first recent card updated but the remaining cards still showed installation test clips. Added a one-time migration that removes only the four known Blender installation samples, preserving other saved videos. After that migration, viewing those films again is allowed normally.

During this check, real playback also exposed duplicate cards for one video. The old fallback identity included duration, which can vary between YouTube metadata updates. The fallback now uses normalized title and channel when no video ID is supplied. Existing entries are migrated to this identity and duplicates are removed while keeping the newest entry and its thumbnail. With fewer than three distinct recorded videos, remaining slots show placeholders instead of test videos.

Corrected APK SHA-256: `616c399577fecb7ff8464abd1fbfebc9bf96662af59ffba986bab58e49e5adba`. Previous sources and APK are saved in `../backups/before-youtube-test-cleanup-20261021-234421`.

Verified on the TV after installing this correction: two real playback entries each occupy one card; the duplicate entry and all remaining Blender samples are gone. The third slot correctly shows a placeholder. Existing viewer entries and artwork were preserved, and no new launcher exception appeared in the checked runtime log.

## Smaller cards, 2026-10-22

The recent-video cards now measure 360 × 280 instead of 480 × 370 in the 1920 × 1080 design space, arranged as a centered row with 32 px gaps. The “YouTube · your last 3 videos” subtitle is removed. The main heading, video titles, channel names, and focused-card action remain, with text and corner sizes adjusted for the smaller cards. Accessibility bounds use the new card rectangles.

Built, signature-verified, installed, and visually checked on the TV. The compact card row and removed subtitle are visible in `../youtube-recents-smaller-cards.png`; the checked runtime log contains no new launcher exception. APK SHA-256: `1d74c306f894b0bb6d58ea3396e5d8b863257f5f0ffee9e02fb82a331ae5a5db`. Backup: `../backups/before-smaller-youtube-cards-20261022-002036`.
