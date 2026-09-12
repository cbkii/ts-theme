# Launcher icon sources

The standalone launcher uses one source family for fixed functional glyphs: **Material Symbols Rounded** from `google/material-design-icons`, licensed under Apache-2.0.

Pinned upstream revision: `40a7a292a79d9394157e1ea24f83d52d5e17c556`.

Source family: `symbols/android/<symbol>/materialsymbolsrounded/<symbol>_24px.xml`.

Vendored mappings:

- `ic_apps.xml` <- `apps`
- `ic_bluetooth.xml` <- `bluetooth`
- `ic_chevron_right.xml` <- `chevron_right`
- `ic_close.xml` <- `close`
- `ic_mic.xml` <- `mic`
- `ic_music.xml` <- `music_note`
- `ic_my_location.xml` <- `my_location`
- `ic_navigation.xml` <- `navigation`
- `ic_next.xml` <- `skip_next`
- `ic_pause.xml` <- `pause`
- `ic_phone.xml` <- `call`
- `ic_play.xml` <- `play_arrow`
- `ic_previous.xml` <- `skip_previous`
- `ic_radio.xml` <- `radio`
- `ic_search.xml` <- `search`
- `ic_settings.xml` <- `settings`
- `ic_star.xml` <- `star`
- `ic_utility.xml` <- `construction`
- `ic_shortcut.xml` <- `widgets`
- `ic_zoom_in.xml` <- `zoom_in`
- `ic_zoom_out.xml` <- `zoom_out`

The upstream vector path geometry is retained. Local copies remove the theme-level `android:tint` attribute because launcher state tinting is applied by `ImageView`/`ImageButton`, and set the intrinsic size to 36dp for this fixed in-vehicle UI. Those are local modifications to the upstream Apache-2.0 files.

The repository root `LICENSE` contains the Apache License 2.0 terms. The launcher also packages `assets/licenses/MATERIAL_SYMBOLS_NOTICE.txt` with the upstream revision and modification notice.

The application launcher badge (`ic_launcher.xml`) is project branding, not a functional UI glyph, and is intentionally not sourced from Material Symbols.
