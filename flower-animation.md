The preferred flower background is a still illustration. Wallpaper motion defaults off; select **Flowers & sky (still)** in Free My TV > Home Settings > Glass background.

An optional silent five-second loop is bundled for experimentation. The still option avoids video-decoding and composition overhead on older TV hardware. On the tested TCL Android 9 TV, the video decoder dropped no frames, but launcher navigation stutter increased while video was active; the still background is recommended.

The optional loop was generated with Kling 2.6 using the same landscape asset as the start and end image. Playing the bundled file does not call a generation service or consume credits.

The model returned 1916 x 1080, 122 frames at 24 fps (5.083 seconds), silent H.264. A local FFmpeg pass scales it to 1920 x 1080, fits the motion to 120 frames, blends the final eight frames towards the starting frame and appends that exact starting frame. Encoding uses matching fixed-QP intra frames at the two ends. The decoded first and last frames were compared in RGB: all pixels are identical (maximum difference 0). Final format: five seconds, 24 fps, H.264 Main level 4.0, yuv420p, no audio, 5,609,193 bytes.

`AnimatedWallpaper` uses Android's MediaPlayer with a TextureView beneath the existing Canvas layers. It loops silently, center-crops without stretching, releases the decoder when Home pauses, and only reveals the video after the first rendered frame. Errors or a preparation timeout retain the original still. The glass surfaces continue sampling the cached still artwork to keep navigation inexpensive. The MP4 is stored uncompressed in the APK so playback can use the asset descriptor directly.

On the TV: Free My TV > Home Settings > Glass background cycles between Default, Flowers & sky (still), and Animated flowers. The original still asset remains bundled. Wallpaper motion defaults off.

Local generation job records, temporary upload/download URLs, and build output are excluded from Git.
