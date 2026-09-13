#!/usr/bin/env python3
from __future__ import annotations
import argparse,sys,zipfile
from pathlib import Path
MAX_APK_BYTES=2_500_000
FORBIDDEN_DEX_MARKERS=(b'Lkotlin/',b'Lkotlinx/',b'Landroidx/',b'Lcom/qihoo360/')
REQUIRED_DEX_MARKERS=(b'Lcom/cbkii/ts18launcher/LauncherActivity;',b'Lcom/cbkii/ts18launcher/AppDrawerActivity;',b'Lcom/cbkii/ts18launcher/SettingsActivity;',b'Lcom/cbkii/ts18launcher/MediaListenerService;',b'Lcom/cbkii/ts18launcher/HomeNavigationSurfacePolicy;',b'Lcom/cbkii/ts18launcher/RawFreeformTaskBackend;',b'Lcom/cbkii/ts18launcher/AndroidPipBackend;',b'Lcom/cbkii/ts18launcher/platform/TopwayDesktopWindowMarkerService;',b'Lcom/cbkii/ts18launcher/platform/TopwayDesktopWindowProvider;')
REQUIRED_FILES={'AndroidManifest.xml','classes.dex','assets/map/map.html','assets/map/vendor/leaflet.js','assets/map/vendor/leaflet.css','assets/map/vendor/LEAFLET-LICENSE.txt','assets/licenses/MATERIAL_SYMBOLS_NOTICE.txt','assets/nav/nav-window.sh'}
def fail(m):raise SystemExit(f'launcher envelope: FAIL: {m}')
def inspect(apk:Path):
 if not apk.is_file():fail(f'APK not found: {apk}')
 size=apk.stat().st_size
 if size<=0:fail('APK is empty')
 if size>MAX_APK_BYTES:fail(f'APK grew beyond {MAX_APK_BYTES} bytes: {size}')
 with zipfile.ZipFile(apk) as a:
  names=a.namelist();missing=REQUIRED_FILES-set(names)
  if missing:fail('missing required files: '+', '.join(sorted(missing)))
  dex_files=sorted(n for n in names if n.startswith('classes') and n.endswith('.dex'))
  if dex_files!=['classes.dex']:fail(f'expected exactly one primary DEX, found: {dex_files}')
  native=sorted(n for n in names if n.startswith('lib/') and n.endswith('.so'))
  if native:fail(f'native libraries are not allowed: {native[:8]}')
  dex=a.read('classes.dex')
  for marker in FORBIDDEN_DEX_MARKERS:
   if marker in dex:fail(f'forbidden runtime marker present in DEX: {marker!r}')
  for marker in REQUIRED_DEX_MARKERS:
   if marker not in dex:fail(f'required runtime marker missing: {marker!r}')
 print(f'launcher envelope: PASS apk_bytes={size} dex=1 native=0 kotlin=0 androidx=0 replugin=0 leaflet_map_assets=present navigation_task_helper=present navigation_surface_experiments=present topway_home_compat=present material_symbols_notice=present')
def main():p=argparse.ArgumentParser();p.add_argument('apk',type=Path);inspect(p.parse_args().apk);return 0
if __name__=='__main__':sys.exit(main())
