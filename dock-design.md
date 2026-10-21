The optional Compact dock uses the existing native Canvas glass renderer, artwork, and app assets. Enable it in Home Settings > Dock style; Standard restores the previous shelf. Compact applies while Glass style is Liquid Glass.

The behavior is inspired by Motion Primitives' Dock:
https://motion-primitives.com/docs/dock
https://github.com/ibelick/motion-primitives/blob/main/components/core/dock.tsx

The reference uses pointer distance, spring magnification, adjacent layout movement, and hover/focus labels. This Android implementation uses remote focus, the existing Spring class, a compact centered panel, proportionate neighboring tile growth, and an app label above the selected tile. No React or browser dependencies are added; no third-party source is copied.

Full-size tile geometry remains stable for cached glass samples. Animated bounds control drawing, accessibility bounds, and app launch positioning. The panel reserves room for magnification across up to eight shelf slots. Standard rendering remains available with the current wallpaper.

JioCinema is removed through the Shelf editor, preserving the app installation and the order of the other six apps.

The Compact dock is currently scaled to 1.3225 times its initial size (two successive 15% increases). Time and date share the top-left header row with the action buttons at the right.

Selected app tiles fade into brand fills: YouTube red, Prime Video blue, Netflix black, Spotify black, JioHotstar deep blue, and MagiConnect red. Unselected tiles retain their glass body. The focused texture is baked once with the brand body and existing glass rim, then crossfaded using the focus spring; the color change adds no per-frame rasterization. Other glass controls continue using the existing appearance.

A fixed More Apps action follows the saved apps, currently after MagiConnect. It uses the same glass body and magnification, with an apps-grid symbol and a blue selected fill. It opens the centered, scrolling four-column All Apps grid; app reordering cannot move this action into the saved app order. YouTube now shows three local recent-video cards when its dock tile is selected; see `youtube-more-apps.md` for behavior, limits, and validation.
