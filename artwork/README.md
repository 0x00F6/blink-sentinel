# Blink Sentinel artwork

## Terminal b application icon

`terminal-b.png` is the new transparent 1254 × 1254 RGBA artwork, inspired by the supplied lowercase b reference and the application's terminal palette. It was created with the built-in image-generation tool, visually inspected, and copied unchanged into `app/src/main/res/drawable-nodpi/terminal_b.png`.

The manifest references `@mipmap/ic_launcher_terminal` for both normal and round icons. Its adaptive layers use a near-black background, the neon green/cyan bitmap with 22.22% proportional foreground margins to fit adaptive masks at every rendered size, and a separate locally authored monochrome b vector for Android themed icons. Android applies the launcher's icon mask. The packaged icon can be viewed in [launcher-preview.png](launcher-preview.png). The same bitmap appears on a black tile in both interface headers. Original house/shield artwork, earlier adaptive and monochrome resources, and the notification icon are retained.

Final built-in generation prompt:

> Use case: logo-brand. Asset type: Android adaptive launcher icon foreground for Blink Sentinel. Create one new emblem inspired by the supplied example: a single bold, rounded lowercase letter b with a tall straight left stem and one large rounded bowl, easily recognizable at 48 pixels. Reinterpret it in the dark terminal hacker visual style of this app. The letter is mostly solid neon green #00FF88 with a crisp cyan #00D1FF inner edge, restrained luminous edge glow, and just two or three clean angular circuit cuts; retain the simple strong b silhouette. Center the mark with generous transparent margins: the entire mark fits inside the central 60% of a square 1024x1024 canvas. Genuinely transparent background, including the counter of the b; it will be composited on #0A0A0A by Android. Flat clean 2D geometry with a subtle glow only, no background tile, border, scene, extra letters, binary digits, text, shield, skull, padlock, slogan, watermark or mockup.

The tool returned a 1254 × 1254 image; it was not resized or recolored. The generated alpha channel is preserved.

## Earlier house and shield logo

`logo.png` is the original transparent artwork created for this project using the integrated image-generation tool. It combines a house, shield, Wi-Fi arcs, and camera lens in navy, teal, and white.

The same artwork is packaged in `app/src/main/res/drawable-nodpi/logo.png`. `ic_launcher_foreground.xml` adds safe margins for adaptive icon masks; `mipmap-anydpi/ic_launcher.xml` defines the launcher with a monochrome layer for Android 13+. Older versions ignore that layer. The notification icon is a white vector drawable.

The light interface uses the logo's navy, teal, and white palette. The dark interface uses a near-black, neon green, and cyan terminal palette. The light launcher background is pale mint (`#EAF8F5`); night-qualified resources provide a black background and neon foreground for dark mode. Local vector resources provide consistent outline icons for Home, Network, Blink, Event log, and Bluetooth; they inherit the current interface colors. The original logo image and notification/monochrome resources are retained.

`app/src/main/res/drawable/ic_terminal_logo.xml` is a small, locally authored line-art interpretation of the house, shield, Wi-Fi, and camera emblem, with green and cyan strokes to match the visual reference. It remains available as an earlier dark emblem and night-qualified legacy launcher foreground; the current header and launcher use the neon b. The original bitmap remains in the light interface and default adaptive launcher. Launcher caching and themed-icon presentation depend on the Android launcher. The dark interface's grid is drawn with cached, static Compose paths rather than a repeating animation or bitmap wallpaper.

Original generation prompt:

> One minimal, professional, flat geometric emblem on a genuinely transparent background. Combine a simple house silhouette with a protective shield and three Wi-Fi arcs; a small camera lens suggested by a central circle. Modern clean rounded geometry, small-size legibility, balanced centered composition, deep navy and bright teal with a small white accent. Generous transparent margins for Android adaptive icon masks. No lettering, words, watermark, mockup, surrounding rounded square, shadows or scene. One standalone icon.
