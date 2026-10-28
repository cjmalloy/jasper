#!/usr/bin/env python3
"""
Extract per-request statistics from Gatling HTML reports and compare them to a baseline.

  report-stats.py extract REPORT_DIR > stats.json
  report-stats.py markdown stats.json [baseline.json] > summary.md
  report-stats.py gate stats.json   # exit 1 unless every gating simulation recorded results and passed

REPORT_DIR holds one <name>simulation-<timestamp>/ directory per run and a <Name>.status
file with the Gatling exit code, both written by run.sh.
"""
import html
import json
import os
import re
import sys

# Assertion pass/fail fails the CI job for these simulations
GATING = ['SmokeTest', 'Regression']
# Not part of the aggregate: saturation and failures are expected
INFORMATIONAL = ['Capacity', 'Inferno', 'RateLimit']
ORDER = ['SmokeTest', 'Regression', 'ReadOnly', 'Capacity', 'Inferno', 'RateLimit']

# Table columns in the Gatling 3.x index.html statistics table
COLUMNS = {
    'total': 2, 'ok': 3, 'ko': 4, 'rps': 6, 'min': 7,
    'p50': 8, 'p75': 9, 'p95': 10, 'p99': 11, 'max': 12, 'mean': 13,
}

# Flag a request when a percentile grows by more than this fraction and this many milliseconds
SLOWER_RATIO = 0.25
SLOWER_MS = 5
# Flag a request when its error rate grows by more than this many percentage points
ERROR_POINTS = 0.5
# Percentiles of rarely sent requests are too noisy to flag
MIN_FLAG_COUNT = 30


def number(text):
    text = re.sub(r'<[^>]*>', '', text).replace(',', '').strip()
    try:
        return float(text) if '.' in text else int(text)
    except ValueError:
        return 0


PEAK_SECONDS = 10


def peak_ok_rps(content):
    """Mean successful responses per second over the busiest PEAK_SECONDS seconds (consecutive) of the run."""
    chart = content.find('Number of responses')
    match = re.search(r"name: 'OK',\s*data: (\[\[.*?\]\])", content[chart:], re.DOTALL) if chart >= 0 else None
    if not match:
        return None
    try:
        values = [v for _, v in json.loads(match.group(1))]
    except ValueError:
        return None
    if not values:
        return None
    window = min(PEAK_SECONDS, len(values))
    return round(max(sum(values[i:i + window]) for i in range(len(values) - window + 1)) / window, 1)


def parse_report(index):
    with open(index, encoding='utf-8') as f:
        content = f.read()
    rows = {}
    parents = {}
    for match in re.finditer(r'<tr id="([^"]+)"(?:\s+data-parent="([^"]*)")?[^>]*>(.*?)</tr>', content, re.DOTALL):
        row_id, parent, row = match.groups()
        name = re.search(r'class="ellipsed-name">(.*?)</span>', row, re.DOTALL)
        stats = {}
        for key, col in COLUMNS.items():
            cell = re.search(rf'<td class="[^"]*\bcol-{col}\b[^"]*">(.*?)</td>', row, re.DOTALL)
            stats[key] = number(cell.group(1)) if cell else 0
        rows[row_id] = (html.unescape(name.group(1).strip()) if name else row_id, stats)
        parents[row_id] = parent

    def path(row_id):
        names = []
        while row_id and row_id != 'ROOT' and row_id in rows:
            names.append(rows[row_id][0])
            row_id = parents.get(row_id)
        return ' / '.join(reversed(names))

    result = {'global': rows['ROOT'][1] if 'ROOT' in rows else None, 'requests': {}, 'peakOkRps': peak_ok_rps(content)}
    for row_id, (_, stats) in rows.items():
        if row_id != 'ROOT':
            result['requests'][path(row_id)] = stats
    return result


def extract(report_dir):
    simulations = {}
    entries = sorted(os.listdir(report_dir)) if os.path.isdir(report_dir) else []
    for entry in entries:
        if entry.endswith('.status'):
            name = entry[:-len('.status')]
            with open(os.path.join(report_dir, entry)) as f:
                code = f.read().strip()
            simulations.setdefault(name, {})['exitCode'] = int(code) if code.lstrip('-').isdigit() else 1
        elif entry.endswith('.capacity.json'):
            name = entry[:-len('.capacity.json')]
            try:
                with open(os.path.join(report_dir, entry)) as f:
                    simulations.setdefault(name, {})['capacity'] = json.load(f)
            except (OSError, ValueError):
                pass
    for entry in entries:
        index = os.path.join(report_dir, entry, 'index.html')
        match = re.match(r'(.+)simulation-\d+$', entry)
        if not match or not os.path.isfile(index):
            continue
        name = next((n for n in simulations if n.lower() == match.group(1)), match.group(1))
        sim = simulations.setdefault(name, {})
        # Keep the latest report when a simulation ran more than once
        sim.update(parse_report(index))
        sim['report'] = entry
    for sim in simulations.values():
        sim.setdefault('exitCode', None)
        sim.setdefault('global', None)
        sim.setdefault('requests', {})
    json.dump({'simulations': simulations}, sys.stdout, indent=2, sort_keys=True)
    print()


def error_rate(stats):
    return 100.0 * stats['ko'] / stats['total'] if stats and stats['total'] else 0.0


def status(sim):
    code = sim.get('exitCode')
    if code is None:
        return '⚪ not run' if not sim.get('global') else '⚪ unknown'
    return '✅ passed' if code == 0 else '❌ failed'


def ordered(simulations):
    return sorted(simulations, key=lambda n: (ORDER.index(n) if n in ORDER else len(ORDER), n))


def ok_rps(stats):
    """Gatling's throughput counts failed requests too: this is the successful share."""
    return round(stats['rps'] * stats['ok'] / stats['total'], 1) if stats and stats['total'] else 0.0


def delta(current, base, key):
    if key == 'okRps':
        current = {**current, 'okRps': ok_rps(current)}
        base = base and {**base, 'okRps': ok_rps(base)}
    if base is None:
        return f'{current[key]}'
    diff = current[key] - base[key]
    text = f'{current[key]} ({diff:+d})' if isinstance(diff, int) else f'{current[key]} ({diff:+.1f})'
    if current['total'] >= MIN_FLAG_COUNT and key.startswith('p') and base.get(key) is not None and diff > SLOWER_MS and diff > base[key] * SLOWER_RATIO:
        text = f'**{text}** 🔺'
    return text


def error_delta(current, base):
    rate = error_rate(current)
    if base is None:
        return f'{rate:.2f}%'
    diff = rate - error_rate(base)
    text = f'{rate:.2f}% ({diff:+.2f})'
    if current['total'] >= MIN_FLAG_COUNT and diff > ERROR_POINTS:
        text = f'**{text}** 🔺'
    return text


def gating(current):
    """Gating simulations that failed, and those missing a result or an exit code."""
    failed = [n for n in GATING if n in current and current[n].get('exitCode') not in (0, None)]
    incomplete = [n for n in GATING if n not in current or current[n].get('exitCode') is None or not current[n].get('global')]
    return failed, [n for n in incomplete if n not in failed]


def gate(stats_file):
    """Exit status 0 only when every gating simulation recorded results and passed."""
    with open(stats_file) as f:
        failed, incomplete = gating(json.load(f)['simulations'])
    for name in failed:
        print(f'{name} failed its assertions', file=sys.stderr)
    for name in incomplete:
        print(f'{name} has no recorded result', file=sys.stderr)
    return 1 if failed or incomplete else 0


def markdown(stats_file, baseline_file=None):
    with open(stats_file) as f:
        current = json.load(f)['simulations']
    baseline = {}
    if baseline_file and os.path.isfile(baseline_file):
        with open(baseline_file) as f:
            baseline = json.load(f).get('simulations', {})

    out = []
    gating_failed, gating_incomplete = gating(current)
    aggregate = {'total': 0, 'ok': 0, 'ko': 0}
    for name, sim in current.items():
        if name not in INFORMATIONAL and sim.get('global'):
            for key in aggregate:
                aggregate[key] += sim['global'][key]
    if gating_failed:
        out.append(f'**❌ Assertions failed: {", ".join(gating_failed)}**')
    if gating_incomplete:
        out.append(f'**⚪ Incomplete, no results recorded: {", ".join(gating_incomplete)}**')
    if not gating_failed and not gating_incomplete:
        out.append('**✅ All gating simulations passed their assertions**')
    out.append('')
    out.append(f'Aggregate of {", ".join(n for n in ordered(current) if n not in INFORMATIONAL)}: '
               f'{aggregate["total"]} requests, {aggregate["ko"]} failed ({error_rate(aggregate):.2f}%). '
               f'{", ".join(INFORMATIONAL[:-1])} and {INFORMATIONAL[-1]} are excluded because saturation is expected.')
    out.append('')
    if not baseline:
        out.append('_No master baseline found, showing absolute values only._')
        out.append('')
    else:
        out.append(f'Changes are relative to the latest master run. 🔺 marks percentiles more than '
                   f'{int(SLOWER_RATIO * 100)}% and {SLOWER_MS} ms slower, or error rates more than {ERROR_POINTS} points higher, '
                   f'for requests sent at least {MIN_FLAG_COUNT} times.')
        out.append('')

    out.append('| Simulation | Status | Requests | Errors | p50 (ms) | p95 (ms) | p99 (ms) | Throughput (rps) | OK (rps) |')
    out.append('|---|---|---|---|---|---|---|---|---|')
    for name in ordered(current):
        sim = current[name]
        g = sim.get('global')
        base = (baseline.get(name) or {}).get('global')
        if not g:
            out.append(f'| {name} | {status(sim)} | - | - | - | - | - | - | - |')
            continue
        out.append(f'| {name} | {status(sim)} | {g["total"]} | {error_delta(g, base)} | {delta(g, base, "p50")} | '
                   f'{delta(g, base, "p95")} | {delta(g, base, "p99")} | {delta(g, base, "rps")} | {delta(g, base, "okRps")} |')
    out.append('')

    for name in ordered(current):
        capacity = current[name].get('capacity')
        if not capacity:
            continue
        base = (baseline.get(name) or {}).get('capacity')
        text = f'{capacity["sessionsPerSec"]:g} sessions/s'
        if base:
            text += f' ({capacity["sessionsPerSec"] - base["sessionsPerSec"]:+g} vs master)'
        if capacity.get('stopped'):
            detail = f'{capacity["levelsCompleted"]} of {capacity["levels"]} steps completed before the error stop'
        else:
            detail = f'all {capacity["levels"]} steps completed, the server was not saturated'
        out.append(f'**{name}:** {text}, {detail}. Mean throughput depends on where the run stopped; compare this instead.')
        out.append('')

    for name in ordered(current):
        peak = current[name].get('peakOkRps')
        if name not in INFORMATIONAL or name == 'Capacity' or peak is None:
            continue
        base = (baseline.get(name) or {}).get('peakOkRps')
        text = f'{peak:g} successful rps' + (f' ({peak - base:+.1f} vs master)' if base is not None else '')
        out.append(f'**{name}:** {text} over the busiest {PEAK_SECONDS}s. Overload runs are mostly idle trickle, so compare this instead of the mean.')
        out.append('')

    for name in ordered(current):
        sim = current[name]
        if not sim.get('requests'):
            continue
        base_requests = (baseline.get(name) or {}).get('requests', {})
        out.append(f'<details><summary>{name} per request</summary>')
        out.append('')
        out.append('| Request | Count | Errors | p50 (ms) | p95 (ms) | p99 (ms) |')
        out.append('|---|---|---|---|---|---|')
        for request, stats in sorted(sim['requests'].items(), key=lambda r: -r[1]['total']):
            base = base_requests.get(request)
            out.append(f'| {request} | {stats["total"]} | {error_delta(stats, base)} | {delta(stats, base, "p50")} | '
                       f'{delta(stats, base, "p95")} | {delta(stats, base, "p99")} |')
        removed = sorted(set(base_requests) - set(sim['requests']))
        if removed:
            out.append('')
            out.append(f'Not run in this build: {", ".join(removed)}')
        out.append('')
        out.append('</details>')
        out.append('')
    print('\n'.join(out))


def main(args):
    if len(args) >= 2 and args[0] == 'extract':
        extract(args[1])
    elif len(args) >= 2 and args[0] == 'gate':
        return gate(args[1])
    elif len(args) >= 2 and args[0] == 'markdown':
        markdown(args[1], args[2] if len(args) > 2 else None)
    else:
        print(__doc__, file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
