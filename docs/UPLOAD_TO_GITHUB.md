# Upload to GitHub

Recommended repository name: `ts18-dashboard-theme`.

1. Extract the delivered ZIP.
2. Create an empty GitHub repository without an auto-generated README/licence.
3. In the extracted directory run:

```bash
git init -b main
git add .
git commit -m "Initial TS18 dashboard theme research baseline"
git remote add origin https://github.com/cbkii/ts18-dashboard-theme.git
git push -u origin main
```

Before committing, confirm these stay absent:

```bash
git status --short
if git ls-files | grep -Eq '\.(apk|aab|jks|keystore|pem|p12)$'; then
  echo "STOP: binary or signing material is tracked" >&2
  exit 1
fi
```

The `.local/`, `build/` and `dist/` directories are ignored. Keep the reference APK and development signing key only in those local/private locations.
