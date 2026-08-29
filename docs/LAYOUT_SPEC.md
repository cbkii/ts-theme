# Layout specification

## Controlling evidence

The physical panel is **1280 x 720**. Last exact-device evidence showed the application-visible right boundary at **x=1225**, leaving a 55 px right Topway/SystemUI/navigation region. The top system region is approximately 55 px high. These direct device bounds supersede the earlier 18 px right-reserve design assumption.

The declarative DoFun theme has no Activity and cannot safely introduce Android `WindowInsets` code merely to emulate responsiveness. Instead, layout is generated/validated from an explicit profile and the installer re-checks current display evidence before any rooted donor mutation.

## Exact TS18 layout profile

```text
physical width       1280
physical height       720
top system inset       55
right system inset     55
safe right            1225
hotseat width           81
strip height            64
content left            81
strip/map split y      119
safe bottom            702
```

Every visible or interactive theme-controlled rectangle must finish at or before x=1225 for this profile. No theme element may occupy the 55 px right SystemUI region.

## Hardened geometry

| Surface | X | Y | Width | Height | Right/Bottom | Content |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| Vertical hotseat | 0 | 55 | 81 | 647 | 81 / 702 | Five plain white generic icons |
| Radio | 81 | 55 | 286 | 64 | 367 / 119 | `< 89.06 FM >` |
| Music | 367 | 55 | 680 | 64 | 1047 / 119 | Title field plus previous/play-next |
| Date | 1047 | 55 | 178 | 64 | 1225 / 119 | `22 AUG` |
| Navigation window | 81 | 119 | 1144 | 583 | 1225 / 702 | Fully visible and interactive |

Radio/music/date exactly tile x=81..1225 with no gaps or overlap. The map occupies the same full safe width below the strip. The map area is **666,952 px²**, still about 3.52 times FYD's recorded 580 x 327 map area while remaining completely outside the observed right SystemUI.

The previous x=93..1247 geometry is historical and unsafe for the exact-device 55 px right inset because its final 22 px could fall beneath SystemUI.

## Responsiveness policy

- `tools/ts18_theme.py` owns/validates the safe-area profile and derived geometry.
- The right-system-inset must be centralised so a later device profile can be generated without editing several unrelated JSON files.
- Use only DoFun gravity/anchoring forms actually observed in audited working themes. Do not invent responsive JSON keys.
- If present-state preflight shows materially different usable bounds, report the mismatch. Do not silently mutate DoFun's private plug-in storage under an incompatible profile.
- Do not add executable window/inset code to the declarative theme solely for layout responsiveness.

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

- Previous and next retain at least 54 x 54 px hit targets in the 64 px strip.
- The frequency is primary white text.
- FM is a small warm-colour superscript.
- MHz, source heading, artwork and play/pause are hidden.
- The information region between the buttons should open the selected/default radio app if the host supports that action.

The exact previous/next semantics and surrounding click routing remain physical validation items.

## Music

- Previous, play/pause and next retain at least 56 x 56 px hit targets.
- The remaining title field renders `Artist - Track Name`.
- No artwork, source subtitle or visualiser is shown.
- A true marquee and information-region app launch remain host/runtime behaviours unless a later separate compatibility adapter is proven necessary.

## Date

`tv_time_day` is the only visible field and uses `dd MMM`. Weekday, year, time and AM/PM are hidden.

The checked-in SVG/JPEG is a visual specification, not proof of DoFun runtime rendering.
