# TS18 physical validation

Read [START-HERE.md](START-HERE.md) for the complete phased procedure. Normal capture is unattended. Source builds require a final immutable APK selection; unpinned identities cannot pass qualification.

Advanced optional capture reduction: put the single data line `CAPTURE_MODE=logs_only` in
`Download/TS18-Validation-v3.6.0/CAPTURE_MODE.conf` before the next run to omit video.
Delete that optional configuration file to restore validation recordings. It is parsed as data,
never sourced or executed. Do not use logs-only mode for a map-pixel or metadata-onset test.

Each compressed part is at most 20,000,000 bytes; up to five parts fit the 100 MB archive bound.
Three raw runs are bounded to 96 MB. Follow-up captures are exported separately.
