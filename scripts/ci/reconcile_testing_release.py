#!/usr/bin/env python3
"""Reconcile TESTING release notes from complete uploaded BUILD_INFO snapshots.

Trusted publisher/repair workflows only. Does not build, sign, upload or delete assets.
"""
import argparse
import hashlib
import html
import json
import os
import re
import subprocess
import sys
from urllib.parse import quote

MANAGED = '<!-- testing-apk-draft-managed -->'
BEGIN = '<!-- testing-apk-notes:begin -->'
END = '<!-- testing-apk-notes:end -->'
GROUP = r'(?:PR[1-9][0-9]*-[0-9a-f]{7}|SHA-[0-9a-f]{7})'
SHA = re.compile(r'[0-9a-f]{40}\Z')


def gh(path, *, patch=None):
    cmd = ['gh', 'api', path]
    if patch is not None:
        cmd += ['--method', 'PATCH', '--input', '-']
    result = subprocess.run(cmd, input=json.dumps(patch) if patch is not None else None,
                            capture_output=True, text=True, check=True)
    return json.loads(result.stdout)


def download(repository, asset):
    token = os.environ['GH_TOKEN']
    result = subprocess.run(
        ['curl', '--fail', '--silent', '--show-error', '--location',
         '--header', 'Accept: application/octet-stream',
         '--header', f'Authorization: Bearer {token}',
         '--header', 'X-GitHub-Api-Version: 2022-11-28',
         f'https://api.github.com/repos/{repository}/releases/assets/{asset["id"]}'],
        capture_output=True, check=True)
    return result.stdout


def metadata(raw):
    values = {}
    for line in raw.decode('utf-8').splitlines():
        if not line or '=' not in line:
            raise ValueError('Invalid BUILD_INFO line')
        key, value = line.split('=', 1)
        if not re.fullmatch(r'[a-z][a-z0-9_]*', key) or key in values:
            raise ValueError(f'Invalid or repeated BUILD_INFO key: {key!r}')
        values[key] = value
    return values


def clean(value):
    """Untrusted source text is displayed as text, not active Markdown/HTML."""
    return html.escape(str(value).replace('\r', ' ').replace('\n', ' '),
                       quote=True).replace('|', '&#124;').replace('`', '&#96;')[:240]


def link(sha, repository):
    if not SHA.fullmatch(sha):
        raise ValueError(f'Invalid commit SHA: {sha!r}')
    return f'[`{sha[:7]}`](https://github.com/{repository}/commit/{sha})'


def render(repository, snapshots):
    product = 'Organic Maps InCar' if repository.endswith('/organicmaps') else 'TS18 standalone launcher'
    out = [f'**TESTING ONLY.** {product} development snapshots for physical/engineering validation, not production releases.',
           '', '## Current TESTING builds', '',
           '| Snapshot | Install | Debug | Source | Built |',
           '| --- | --- | --- | --- | --- |']
    details = []
    for index, (group, info, assets, checksums) in enumerate(snapshots, 1):
        source, built = info['source_sha'], info['built_sha']
        pr = info.get('pr_number', '')
        src = f'[PR #{pr}](https://github.com/{repository}/pull/{pr}) at {link(source, repository)}' if pr.isdecimal() and int(pr) > 0 else link(source, repository)
        by_name = {a['name']: a for a in assets}
        def url(key):
            return by_name[info[key]]['browser_download_url']
        out.append(f'| `{group}` | [TESTING APK]({url("install_asset")}) | [DEBUG APK]({url("debug_asset")}) | {src} | {link(built, repository)} |')
        detail = [f'### {index}. {clean(group)}', '',
                  f'- **Install:** [{clean(info["install_asset"])}]({url("install_asset")})',
                  f'- **Debug:** [{clean(info["debug_asset"])}]({url("debug_asset")})',
                  f'- **Snapshot source:** {link(source, repository)}',
                  f'- **Built SHA:** {link(built, repository)}',
                  f'- **Version:** `{clean(info.get("testing_version_name", "unknown"))}` / code `{clean(info.get("testing_version_code", "unknown"))}`']
        if pr.isdecimal() and int(pr) > 0:
            detail.append(f'- **PR at build request:** [#{pr}](https://github.com/{repository}/pull/{pr})')
            if SHA.fullmatch(info.get('pr_base_sha', '')):
                detail.append(f'- **Base at request:** {link(info["pr_base_sha"], repository)}')
        if info.get('build_run_url', '').startswith(f'https://github.com/{repository}/actions/runs/'):
            detail.append(f'- **Build workflow:** [run {clean(info.get("build_run_number") or info.get("build_run_id") or "link")}]({info["build_run_url"]})')
        if info.get('published_at'):
            detail.append(f'- **Published:** `{clean(info["published_at"])}`')
        detail.append(f'- **Metadata:** [BUILD_INFO]({url("metadata_asset")}) · [SHA256SUMS]({url("checksums_asset")}) · [SIGNER]({url("signer_asset")})')
        detail += ['', '**Changes at this snapshot:**']
        change_keys = sorted((k for k in info if re.fullmatch(r'change_[1-6]', k)),
                             key=lambda k: int(k.split('_')[1]))
        if change_keys:
            detail += [f'- {clean(info[k])}' for k in change_keys if info[k].strip()]
        else:
            detail.append('- No change summary recorded in this older BUILD_INFO asset; refer to the immutable source commit/PR.')
        detail += ['', '**SHA-256 asset manifest:**']
        for filename, digest in checksums:
            detail.append(f'- `{clean(filename)}` — `{digest}`')
        details.extend([''] + detail)
    out += ['', 'Each row is a separate, immutable build snapshot; newer PR commits are not necessarily included.', ''] + details
    source_run = snapshots[0][1].get('publish_run_url', '')
    if source_run.startswith(f'https://github.com/{repository}/actions/runs/'):
        out += ['', '---', f'Managed by **Refresh Testing APK Draft**. Publisher run: {source_run}']
    return '\n'.join(out).rstrip() + '\n'


def anchored_body(current, managed):
    if current.count(MANAGED) != 1:
        raise ValueError('Not the uniquely marked managed TESTING draft')
    if current.count(BEGIN) == 1 and current.count(END) == 1:
        before, remainder = current.split(BEGIN, 1)
        _, after = remainder.split(END, 1)
        return before + BEGIN + '\n\n' + managed + '\n' + END + after
    if BEGIN in current or END in current:
        raise ValueError('Incomplete or duplicated note anchors; manual repair required')
    if 'Managed by **Refresh Testing APK Draft**' not in current and 'Do not publish this draft' not in current:
        raise ValueError('Unanchored body has unrecognised content; refusing to overwrite it')
    return f'{MANAGED}\n\n{BEGIN}\n\n{managed}\n{END}\n'


def reconcile(repository, release_id, expected_group=''):
    release_path = f'repos/{repository}/releases/{release_id}'
    release = gh(release_path)
    if release.get('draft') is not True or release.get('name') != '000 Testing Only Version':
        raise ValueError('Release is not the expected TESTING-only draft')
    body = release.get('body') or ''
    if MANAGED not in body:
        raise ValueError('Release is not workflow-managed')
    tag = release.get('tag_name', '')
    if tag != '000-testing-only' and not re.fullmatch(r'untagged-[0-9a-f]+', tag):
        raise ValueError(f'Unexpected TESTING draft tag: {tag!r}')
    assets = gh(f'{release_path}/assets?per_page=100')
    by_name = {}
    for asset in assets:
        name = asset['name']
        if name in by_name:
            raise ValueError(f'Duplicate release asset name {name}')
        by_name[name] = asset
    infos = sorted((a for a in assets if a['name'] == 'BUILD_INFO.txt' or
                    re.fullmatch(rf'BUILD_INFO-{GROUP}\.txt', a['name'])),
                   key=lambda a: (a['created_at'], a['name']), reverse=True)
    if not 1 <= len(infos) <= 2:
        raise ValueError(f'Expected 1–2 retained metadata assets; found {len(infos)}')
    snapshots, allowed = [], set()
    for asset in infos:
        info_name = asset['name']
        group = 'LEGACY' if info_name == 'BUILD_INFO.txt' else info_name[len('BUILD_INFO-'):-4]
        info_raw = download(repository, asset)
        info = metadata(info_raw)
        if info.get('repository') and info['repository'] != repository:
            raise ValueError(f'BUILD_INFO belongs to another repository: {info["repository"]}')
        for key in ('source_sha', 'built_sha'):
            if not SHA.fullmatch(info.get(key, '')):
                raise ValueError(f'{group}: invalid {key}')
        pr = info.get('pr_number', '')
        derived = f'PR{pr}-{info["source_sha"][:7]}' if pr.isdecimal() and int(pr) > 0 else f'SHA-{info["built_sha"][:7]}'
        if group != 'LEGACY' and group != derived:
            raise ValueError(f'Metadata/source mismatch: {group} != {derived}')
        if info.get('group_id', group) != group:
            raise ValueError(f'BUILD_INFO group_id mismatch: {group}')
        prefix = 'OrganicMaps-InCar' if repository.endswith('/organicmaps') else 'TS18-Standalone-Launcher'
        suffix = ('-' + group) if group != 'LEGACY' else ''
        defaults = {'install_asset': f'{prefix}-TESTING{suffix}.apk',
                    'debug_asset': f'{prefix}-DEBUG{suffix}.apk',
                    'signer_asset': f'SIGNER{suffix}.txt',
                    'checksums_asset': f'SHA256SUMS{suffix}.txt',
                    'metadata_asset': info_name}
        for key, default in defaults.items():
            info.setdefault(key, default)
            if info[key] != default:
                raise ValueError(f'{group}: unexpected {key}: {info[key]}')
            a = by_name.get(default)
            if not a or a.get('state') != 'uploaded' or a.get('size', 0) <= 0:
                raise ValueError(f'{group}: missing/unready release asset {default}')
            url = a.get('browser_download_url', '')
            if not url.startswith(f'https://github.com/{repository}/releases/download/') or not url.endswith('/' + quote(default, safe='')):
                raise ValueError(f'{group}: unexpected/missing URL for {default}')
            allowed.add(default)
        sum_raw = download(repository, by_name[info['checksums_asset']])
        checksums = []
        for line in sum_raw.decode('utf-8').splitlines():
            match = re.fullmatch(r'([0-9a-f]{64})  ([^/\r\n]+)', line)
            if not match:
                raise ValueError(f'{group}: malformed SHA256SUMS record')
            checksums.append((match.group(2), match.group(1)))
        required = {info[k] for k in ('install_asset', 'debug_asset', 'metadata_asset', 'signer_asset')}
        if len(checksums) != 4 or {name for name, _ in checksums} != required:
            raise ValueError(f'{group}: checksums do not match snapshot contents')
        manifest = dict(checksums)
        if hashlib.sha256(info_raw).hexdigest() != manifest[info_name]:
            raise ValueError(f'{group}: BUILD_INFO checksum mismatch')
        signer_raw = download(repository, by_name[info['signer_asset']])
        if hashlib.sha256(signer_raw).hexdigest() != manifest[info['signer_asset']]:
            raise ValueError(f'{group}: SIGNER checksum mismatch')
        for k in ('install_asset', 'debug_asset'):
            digest = by_name[info[k]].get('digest')
            if digest and digest != 'sha256:' + manifest[info[k]]:
                raise ValueError(f'{group}: APK digest disagrees with SHA256SUMS')
        snapshots.append((group, info, assets, checksums))
    managed_names = re.compile(rf'(?:BUILD_INFO|SHA256SUMS|SIGNER)-(?:{GROUP})\.txt|(?:OrganicMaps-InCar|TS18-Standalone-Launcher)-(?:TESTING|DEBUG)-(?:{GROUP})\.apk|(?:BUILD_INFO|SHA256SUMS|SIGNER)\.txt|(?:OrganicMaps-InCar|TS18-Standalone-Launcher)-(?:TESTING|DEBUG)\.apk')
    leftovers = [name for name in by_name if managed_names.fullmatch(name) and name not in allowed]
    if leftovers:
        raise ValueError(f'Orphaned managed assets require cleanup: {leftovers}')
    if expected_group and expected_group not in {item[0] for item in snapshots}:
        raise ValueError(f'Just-published snapshot {expected_group} not retained')
    desired = anchored_body(body, render(repository, snapshots))
    if desired != body:
        gh(release_path, patch={'name': '000 Testing Only Version', 'body': desired,
                                'draft': True, 'prerelease': False, 'make_latest': 'false'})
    verified = gh(release_path)
    if verified.get('body') != desired or verified.get('draft') is not True:
        raise ValueError('Release PATCH/read-back mismatch')
    print(f'Reconciled {repository} release {release_id}: {len(snapshots)} complete snapshot(s), verified read-back; changed={body != desired}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release-id', required=True, type=int)
    parser.add_argument('--expected-group', default='')
    args = parser.parse_args()
    repo = os.environ['GITHUB_REPOSITORY']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo):
        raise ValueError('Invalid GITHUB_REPOSITORY')
    reconcile(repo, args.release_id, args.expected_group)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, subprocess.CalledProcessError, OSError, UnicodeError) as exc:
        print(f'::error::TESTING note reconciliation failed: {exc}', file=sys.stderr)
        sys.exit(1)
