# Installing on the TS18

This procedure is for the project APK on CB's exact Topway TS18. It keeps `com.dofun.variety` as the Android HOME launcher and installs/substitutes only the theme plug-in payload.

The helper bundle shipped with a release contains `scripts/termux/` plus an immutable install manifest. Work/checkpoint state stays in private Termux storage; exported preflight reports and rollback sets go to:

```text
/storage/emulated/0/Download/TS18-theme-install/
```

## Safety / rollback first

The theme is independently signed. Static evidence cannot prove current DoFun accepts that signer directly. The original 4PDA modified-theme precedent reports that direct USB import could fail with `damaged file`, while replacing an already imported theme's RePlugin JAR worked and `pkgname` was not checked in that path:

<https://4pda.to/forum/index.php?showtopic=1015856&st=29360>

The normal donor fallback in this project therefore leaves `p.l` unchanged and replaces only the selected donor file's contents after a verified backup.

Do not proceed with rooted donor substitution if:

- current DoFun private storage is not successfully inspected under root;
- `app_p_a` or `p.l` is absent/changed in a way the helper cannot understand;
- the selected donor resolves outside DoFun's `app_p_a` or is a symlink;
- backup/hash verification fails;
- current display/host identity materially differs and you cannot explain the difference.

The helper must never automatically clear DoFun application data, change SELinux mode, use broad chmod/chown, edit `/system` or `/vendor`, or modify/re-sign the DoFun APK.

## 1. Verify release files

Keep the APK, checksum, metadata and `install-tools.zip` from the same GitHub Release. The release APK identity is:

```text
launcher.variety.theme.plugin.sfp_cbk_black
plug-in id: sfp_cbk_black
```

Use the included helper to locate the APK and verify its SHA-256 against adjacent release data when available.

## 2. Run read-only preflight

From Termux:

```bash
bash scripts/termux/ts18-theme-preflight.sh --apk /path/to/TS18-Dashboard-Theme-vX.Y.Z.apk
```

or launch the interactive wizard:

```bash
bash scripts/termux/ts18-theme-install.sh --apk /path/to/TS18-Dashboard-Theme-vX.Y.Z.apk
```

Preflight reports Android/Topway build, display size/density, current HOME, DoFun package/version, root availability and read-only RePlugin storage state. It warns if current usable bounds no longer match the release's 55 px right-system-inset profile.

## 3. Method 1 — ordinary Android package discovery

Use the wizard's direct-install option first. It installs/reinstalls the exact APK through PackageManager when available, verifies the resulting package and optionally restarts DoFun.

Then open DoFun/Theme and inspect downloaded/local themes. If the theme appears, apply it and proceed to physical validation.

Do not disable or replace `com.dofun.variety`; it remains the HOME launcher.

## 4. Method 2 — DoFun/4PDA local or U-disk import

If PackageManager installation succeeds but DoFun does not discover the theme, use the helper's U-disk preparation option. It copies the verified APK to:

```text
/storage/emulated/0/theme/
```

with a post-copy hash check.

On the touchscreen, use the DoFun Theme downloaded/local area, enter the hidden/profile/circular-arrow area as applicable to the installed version, long-press the gear until the QR window appears, select **U disk import**, choose the APK and apply it.

Historical 4PDA guidance recommends performing the import with Internet connectivity off. The helper only tells you to do this; it does not silently alter networking.

Never clear DoFun data merely to make the helper proceed. If current DoFun explicitly requires a data reset for its own custom-theme slot, make and understand a separate launcher-state backup first.

## 5. Method 3 — rooted RePlugin donor-slot fallback

Use only for the known signature/import-rejection case after current root preflight succeeds.

The helper reads `p.l`, lists imported `sfp_*` candidates and asks you to select a donor. `sfp_fyd18` is preferred when available because its audited window/OEM contract is close to this project; `sfp_ts10s` is documented as the donor used in the original 4PDA modified-theme example. A donor is never assumed to exist.

Before mutation the helper must:

1. resolve and validate the donor path inside DoFun's `app_p_a`;
2. reject symlinks/unexpected paths;
3. show donor package/path/hash;
4. export donor bytes, `p.l`, hashes, stat/SELinux metadata, DoFun version and release metadata to a timestamped rollback directory;
5. verify the backup before continuing.

It then force-stops DoFun, overwrites the **existing donor file in place**, syncs and verifies that its SHA-256 exactly equals the supplied APK. This preserves the existing donor inode/ownership/mode/SELinux labelling and leaves `p.l` unchanged.

If replacement verification fails or the operation is interrupted, the helper attempts verified restoration immediately and reports whether recovery succeeded.

The donor's original tile/name/preview may remain visible in DoFun even though the substituted payload is loaded. Select that donor tile when testing.

## 6. Rollback

Rollback is available from the installer menu and independently:

```bash
bash scripts/termux/ts18-theme-rollback.sh
```

Select a recorded backup. The rollback helper verifies the saved target path is still within DoFun's `app_p_a`, shows expected hashes, force-stops DoFun, restores the original donor bytes in place and verifies the restored hash. It does not depend on DoFun automatically resetting itself.

If you used only normal PackageManager installation, uninstall the project package rather than using donor rollback.

## 7. Physical validation

After apply, validate at minimum:

- full right Topway/SystemUI area visible and responsive;
- date/map right edge unobstructed;
- map pan/zoom/touch through the whole safe map window;
- radio previous/next observed behaviour;
- media previous/play-next causes exactly one action;
- launcher restart;
- Android reboot;
- cold boot/full power removal;
- ACC sleep/wake;
- Internet reconnect if the import was initially performed offline.

The helper can record these observations but must not mark them passed automatically. See [TS18 physical validation](TS18_VALIDATION.md).
