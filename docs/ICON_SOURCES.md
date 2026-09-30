# Launcher icon sources

The standalone launcher uses one source family for fixed functional and user-selectable monochrome glyphs: **Material Symbols Rounded** from `google/material-design-icons`, licensed under Apache-2.0.

Pinned upstream revision: `40a7a292a79d9394157e1ea24f83d52d5e17c556`.

Source family: `symbols/android/<symbol>/materialsymbolsrounded/<symbol>_24px.xml`.

There is no runtime icon-library dependency. Only the glyphs used by the launcher or exposed in the deliberately bounded quick-slot appearance picker are vendored.

## Core functional mappings

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
- `ic_home.xml` <- `home`
- `ic_work.xml` <- `work`
- `ic_camera.xml` <- `photo_camera`
- `ic_video.xml` <- `movie`
- `ic_weather.xml` <- `partly_cloudy_day`

## Curated quick-slot appearance mappings

The `ICON` half of each HOME/drawer quick-slot editor additionally exposes a bounded automotive/general-purpose appearance set. These selections are **visual only**; they never change the package or semantic role launched by the slot.

- `ic_car.xml` <- `directions_car`
- `ic_map.xml` <- `map`
- `ic_route.xml` <- `route`
- `ic_equalizer.xml` <- `equalizer`
- `ic_podcast.xml` <- `podcasts`
- `ic_usb.xml` <- `usb`
- `ic_folder.xml` <- `folder`
- `ic_wifi.xml` <- `wifi`
- `ic_download.xml` <- `download`
- `ic_volume.xml` <- `volume_up`
- `ic_power.xml` <- `power_settings_new`
- `ic_dashboard.xml` <- `dashboard`
- `ic_lightbulb.xml` <- `lightbulb`
- `ic_notifications.xml` <- `notifications`
- `ic_palette.xml` <- `palette`

The picker also reuses the core Navigation, Radio, Music, Bluetooth, Phone, Favourite, Home, Work, Search, Camera, Settings, Utility, Media/video, Weather and generic-app glyphs. `Auto` is not a glyph: it means the slot displays the installed app icon for an explicit app shortcut or the semantic Material Symbol for a role shortcut.

## Local Android-vector adaptation

The upstream vector path geometry is retained. Local copies remove the theme-level `android:tint` attribute because launcher state tinting is applied by `ImageView`/`ImageButton`, and set the intrinsic size to 36dp for the in-vehicle UI. Those are local modifications to the upstream Apache-2.0 files.

The repository root `LICENSE` contains the Apache License 2.0 terms. The launcher also packages `assets/licenses/MATERIAL_SYMBOLS_NOTICE.txt` with the upstream revision and modification notice.

The application launcher badge (`ic_launcher.xml`) is project branding, not a functional UI glyph, and is intentionally not sourced from Material Symbols.
