# TS18 theme installation tools

Use these helpers from Termux on the target TS18. Start with the read-only preflight or the interactive installer; do not jump directly to donor substitution.

```bash
bash scripts/termux/ts18-theme-preflight.sh --apk /path/to/TS18-Dashboard-Theme-vX.Y.Z.apk
bash scripts/termux/ts18-theme-install.sh --apk /path/to/TS18-Dashboard-Theme-vX.Y.Z.apk
```

The installer attempts, in order, normal PackageManager discovery, DoFun/4PDA local import preparation, then a guarded rooted RePlugin donor substitution. Donor substitution backs up the existing donor and `p.l`, leaves `p.l` unchanged, writes only the selected numeric donor JAR in place, verifies hashes and automatically attempts restoration if replacement verification or an interrupt occurs.

Standalone rollback:

```bash
bash scripts/termux/ts18-theme-rollback.sh
```

Exports and rollback sets are stored under `/storage/emulated/0/Download/TS18-theme-install/`; transient locks/state stay in private Termux storage. The scripts never clear DoFun application data, set SELinux permissive, write `/system` or `/vendor`, or modify the `com.dofun.variety` APK.

See `docs/INSTALL_TS18.md` in this bundle for the full installation and physical-validation procedure. The included `install-manifest.json` identifies the exact release APK hash and safe-area profile expected by the toolkit.
