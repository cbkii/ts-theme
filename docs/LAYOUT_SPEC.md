# Layout specification

## Design intent

The map is the primary surface. A single thin information strip sits above it; no theme element
overlays or obscures the map.

Reference coordinate space: **1280 x 720**. The working assumptions are a 55 px system status region,
an 18 px right system strip and a 96 px launcher hotseat.

## Prototype geometry

| Surface | X | Y | Width | Height | Content |
| --- | ---: | ---: | ---: | ---: | --- |
| Vertical hotseat | 0 | 55 | 81 | 647 | Five plain white generic icons |
| Radio | 93 | 55 | 286 | 71 | `< 89.06 FM >` |
| Music | 379 | 55 | 690 | 71 | Ticker on the left, previous/play-next controls on the right |
| Date | 1069 | 55 | 178 | 71 | `22 AUG` |
| Navigation window | 93 | 126 | 1154 | 576 | Fully visible and interactive |

The three top widgets cover x=93..1247 and y=55..126. The map begins at y=126; their
rectangles have no intersection. Compared with the previous 96 px hotseat and 18 px right reserve,
this conservative layout frees 15 px on the left and leaves a 33 px right-side reserve to better
tolerate stock right-edge overlays such as virtual buttons.

FYD's map was 580 x 327 (189,660 px). The new unobstructed map is 1154 x 576 (664,704 px), about
**3.50 times the visible area**.

## Colour tokens

| Token | Value | Use |
| --- | --- | --- |
| Background | `#000000` | Page and strip |
| Primary | `#FFFFFF` | Icons and text |
| Accent coral | `#FF6B57` | Active/pressed state and FM marker |
| Accent orange | `#FF9F43` | Optional secondary active state |
| Accent brown | `#8A4F3D` | Fine separators/borders |
| Warm muted | `#D7B7AA` | Optional inactive/supporting text |

No cool cyan/blue accent or coloured icon background is permitted.

## Radio

- Previous and next retain at least 54 x 54 px hit targets in the taller 71 px strip.
- The frequency is primary white text.
- FM is a small warm-colour superscript.
- MHz, source heading, artwork and play/pause are hidden.
- The information region between the buttons opens the selected/default radio app.

The exact result of previous/next remains a physical validation item.

## Music

- Previous, play/pause and next move to the right side of the 690 px strip with 56 x 56 px hit targets and extra inter-button spacing.
- The remaining left-side field renders `Artist - Track Name`.
- Text is static when it fits and horizontally scrolls when it does not.
- No artwork, source subtitle or visualiser is shown.
- Tapping ticker/unused media space opens the selected/default music app.

The supplied JSON controls the wide text field, but does not expose a proven marquee or click-routing
property. Those behaviours may be supplied by the host or the broad media adapter.

## Date

`tv_time_day` is the only visible field and uses `dd MMM`. Weekday, year, time and AM/PM are
hidden.

The checked-in SVG/JPEG is a visual specification, not proof of DoFun runtime rendering.
