# Top rail metadata and NavRadio+ frequency

Scope: standalone `launcher`, Android 10/API 29, Topway TS18 1280×720. Implemented
on PR #26 `136c62ead089c3a0158161e0956b05913b82d582`, following the metadata plan
against `main` `2cd04022231064deec627bd1593fc4cfde5d94ce`. The separate feature PR
preserves the startup/HOME work and does not qualify its physical gates.

## Presentation

`MediaMetadataView` uses 16 dp horizontal insets within the existing geometry.
Each row clips independently. `MetadataFadeRow` paints a fixed 28 dp gradient
into the resolved `ui_black` background only when that row overflows; its shader
is cached across animation frames. The primary five-second-hold marquee,
text sizes, weights and vertical alignment remain. Secondary text clips/fades
instead of ellipsising. A frequency-only change does not restart the primary
marquee. The overlays are drawing operations and cannot receive input/focus.

## Frequency authority and data flow

Only `com.navimods.radio`, with Radio selected, gets this frequency policy.
Music and other radio packages retain the existing artist/subtitle behaviour.

1. `Snapshot.radioFrequency` derives an optional canonical kHz/band pair from
   explicit, unit-bearing current source metadata (`FM 105.9`, `105.9 FM/MHz`,
   `1053 AM/kHz`). Both existing session producers, notification fallback and
   stability copies use the same snapshot constructor; title/artist changes
   already participate in snapshot equality. Contradictory explicit values
   are unresolved and cannot be replaced by a table guess.
2. There is **no verified private numeric NavRadio+ key/service/provider
   contract for the installed API29 version**. No vendor key, URI integer,
   RDS text, undocumented scaling, tuning command or private service is guessed.
   The optional fallback below is implemented for precisely this boundary.
3. The bundled `au-stations.tsv` provides state, locality, callsign, frequency,
   band and branding from the requested Wikipedia article. It contains 2,053
   rows across ACT, NSW, VIC, QLD, SA, WA, TAS and NT. Frequency-led lists are
   included because most states are represented as lists rather than tables.
   Non-frequency DAB tables, historical parenthesised frequencies, explicitly
   defunct rows and unparseable entries are excluded. This is a reference
   snapshot, not an ACMA licence register or proof of the currently tuned RF.
4. The fallback compares complete normalised callsigns/branding against current
   title/subtitle, ignoring case, spacing and punctuation. Valid callsign
   identity outranks branding. There is no substring or edit-distance match.
   A state containing the fresh location outranks states within 99 km of its
   boundary. Neighbour ties use ACT > NSW > VIC > QLD > SA > WA > TAS > NT.
   Without a usable location only ACT > NSW > VIC are searched, in that order.
5. Equal-ranked candidates with different frequencies produce an empty second
   line. For example, ACT lists ABC Classic at 102.3 and its Tuggeranong
   transmitter at 99.1; state-level location cannot identify the tuned
   transmitter. No nearest-transmitter or frequency inference is invented.
   `2CA` and `2XX` have unambiguous ACT matches (1053 AM and 98.3 FM).
6. Every render resolves the current snapshot rather than retaining a previous
   lookup. A NavRadio snapshot merge refuses enrichment with a different or
   missing station title. Existing station-associated notification checks
   remain. Switching session/package/station cannot carry a directory value.

The bounded existing local `MediaEventTrace` records changes as `observed-text`,
`directory:location-region:<state>`, `directory:default-region:<state>` or
`unresolved`. Reference values remain **inferred**, even when the identity match
is exact. Confidence labels describe evidence categories, not probabilities.

## Location, lifecycle and runtime cost

No additional permission, permission prompt, active GPS request, foreground
service, wake lock, root access, network request or polling loop is introduced.
Only visible HOME with selected NavRadio and no explicit frequency subscribes to Android's **PASSIVE**
provider using an already-granted fine-location permission. It can observe fixes
produced by an already-running navigation app. On entry, existing last-known
provider fixes are checked. A fix must be at most 15 minutes old using monotonic
time and have reported accuracy <=10 km. Otherwise use default-region ranking.
Location and listeners are cleared on stop/detach, source switching, or hiding
HOME. An expiring fix has a single lifecycle-bound expiry callback.

The immutable reference is loaded once per process on a serial worker. Border
classification also runs on that worker, with generation checks before the UI
uses a result. No raw location is written to logs or persisted. The only Android
location behaviour change authorised by this request is passive metadata
ranking; the existing map permission policy remains the authority for prompts.

The ABS polygons retain holes and disconnected rings. Distances use a local
longitude-scaled kilometre projection and a 99 km inclusion threshold. Source
geometry is simplified (0.005 degrees at capture, then 0.01 degrees at import)
and coordinates rounded to four decimals. This is an approximate fuzzy border,
with roughly kilometre-scale geometry uncertainty and projection error; it is
not an exact geodesic or legal boundary. The 177 KiB gzip border asset and 82 KiB
station asset require no Android runtime dependency. Radio coverage and terrain
are not modelled.

## Data provenance, licences and regeneration

Station source: [List of radio stations in Australia, revision 1378942509](https://en.wikipedia.org/w/index.php?title=List_of_radio_stations_in_Australia&oldid=1378942509),
retrieved 19 October 2026. Article [history and contributors](https://en.wikipedia.org/w/index.php?title=List_of_radio_stations_in_Australia&action=history).
The derived station data is attributed to Wikipedia contributors under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), separately from
repository code. Changes: structured field extraction, markup/citation removal,
unit conversion to kHz, state/locality association, sorting and deduplication.
HTML capture SHA-256: `c08cfa7be67178439ec9176c84903d617c75af8de2dfb6dbf234f35ec87ce773`.

Boundary source: Australian Bureau of Statistics,
[ASGS Edition 4 (2026), State and Territory digital boundaries](https://www.abs.gov.au/statistics/standards/australian-statistical-geography-standard-asgs/edition-4-july-2026-june-2031/access-and-downloads/digital-boundary-files),
[STE MapServer layer 0](https://geo.abs.gov.au/arcgis/rest/services/ASGS2026/STE/MapServer/0),
[ABS attribution/licensing](https://www.abs.gov.au/website-privacy-copyright-and-disclaimer),
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Derived/simplified,
not an official ABS product. Capture query: `where=1%3D1&outFields=*&outSR=4326&f=geojson&maxAllowableOffset=0.005&geometryPrecision=4`.
GeoJSON SHA-256: `5130441ef0de7b623b898310135753ca50713a299065269d8984e5fe08eee9e0`.

Developer-only importer dependencies: Python, pandas and lxml. Download/review
source captures separately; the importer deliberately performs no network I/O:

```sh
python3 tools/update_radio_directory.py --stations-html stations.html --boundaries-geojson boundaries.geojson
python3 -m unittest discover -s tests -p test_radio_directory.py -v
```

An intentional refresh needs new provenance, source hashes and reviewed asset
diffs. Gzip uses a fixed timestamp for reproducibility. No runtime scraping.

## Validation and physical acceptance

Deterministic tests exercise the actual parser, bundled corpus, all eight
regions, default priority, border overlap, unknown/ambiguous identities, music
isolation and ticker updates. Existing metadata stability and effective-snapshot
tests remain. Source validation and the complete Python suite are required,
followed by the existing CI Android lint/JVM/debug/minified release lanes.

Physical tests are **NOT_RUN** until the signed exact-head TESTING APK is used:

- Check short and overflowing primary/secondary text, day/night appearances,
  actual TS18 density and safe-right bounds; no fade over short text or adjacent
  transport/date sections. Check long text at both marquee holds.
- Tune at least two NavRadio stations with known different frequencies. Record
  raw source title/subtitle and the local frequency provenance trace. Check
  station changes, delayed notifications and temporary empty metadata.
- Check 2CA/2XX lookup with no location; unknown and conflicting transmitter
  identities remain empty. Test Canberra, NSW and the NSW/VIC border with fresh
  passive fixes, stale fixes, GPS disabled and permission absent/revoked.
- Switch radio/music/app/session, stop/start HOME, kill/recreate the process;
  verify no stale frequency, marquee resets, extra permission prompt or GPS
  activation. Verify two foreground station changes without opening HOME again.
- Preserve all PR #26 cold-start/readiness, actual audible output and navigation
  acceptance gates; this metadata feature does not satisfy those gates.
