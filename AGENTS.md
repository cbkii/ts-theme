# Repository working rules

- Target CB's TS18: Android 10/API 29, 1280 x 720, DoFun `com.dofun.variety`.
- Treat current physical evidence and the exact installed host APK as controlling.
- Keep the 1154 x 583 map rectangle entirely unobstructed.
- Preserve radio/music/date order in one continuous 64 px top strip.
- Use black/white plus warm pink/red/orange/brown accents; do not add cool cyan/blue accents.
- Do not add album art, a visualiser or decorative icon backgrounds.
- Keep declarative theme work separate from executable media integration.
- A broad adapter may consume several evidence-backed paths, but each press must reach one selected
  playback authority exactly once.
- Do not create another player, queue, playback service, MediaSession, notification or focus owner.
- Do not replace, delete, disable or re-sign protected Topway/DoFun packages.
- Do not claim ticker, app launch, radio presets or generic control until physically validated.
- Do not commit vendor APKs/assets, decrypted vendor code, signing keys, device identifiers or logs.
- Keep Android 10/API 29 compatibility and gate newer APIs.
- Before every commit run `python3 tools/ts18_theme.py validate` and
  `python3 -m unittest discover -s tests -v`.
