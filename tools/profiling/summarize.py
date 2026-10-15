#!/usr/bin/env python3
"""Export period-weighted simpleperf self/inclusive costs as JSON and offline HTML."""

import argparse
from collections import Counter
import csv
import html
import json
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("recording", type=Path)
    args = parser.parse_args()
    directory = args.recording.resolve()
    sys.path.insert(0, str(args.ndk.resolve() / "simpleperf"))
    from simpleperf_report_lib import GetReportLib

    lib = GetReportLib(str(directory / "perf.data"))
    lib.SetSymfs(str(directory / "binary_cache"))
    own, inclusive, threads, libraries = Counter(), Counter(), Counter(), Counter()
    samples, total, unknown, no_callchain = 0, 0, 0, 0
    first_time, last_time = None, None
    events = set()
    metadata = lib.MetaInfo()
    record_command = lib.GetRecordCmd()
    try:
        while True:
            sample = lib.GetNextSample()
            if sample is None:
                break
            event = lib.GetEventOfCurrentSample()
            events.add(event.name)
            symbol = lib.GetSymbolOfCurrentSample()
            chain = lib.GetCallChainOfCurrentSample()
            period = sample.period
            samples += 1
            total += period
            first_time = sample.time if first_time is None else min(first_time, sample.time)
            last_time = sample.time if last_time is None else max(last_time, sample.time)
            name = symbol.symbol_name
            own[name] += period
            libraries[symbol.dso_name] += period
            threads[(sample.tid, sample.thread_comm)] += period
            if name in ("[unknown]", "unknown", "") or name.startswith("0x"):
                unknown += period
            if chain.nr == 0:
                no_callchain += period
            # Count each function once per sample, even in recursive stacks.
            names = {name} | {chain.entries[i].symbol.symbol_name for i in range(chain.nr)}
            for name in names:
                inclusive[name] += period
    finally:
        lib.Close()
    if not total or len(events) != 1:
        raise RuntimeError(f"Expected one nonempty task-clock event; got {events}, total={total}")
    event = next(iter(events))
    if not event.startswith("task-clock"):
        raise RuntimeError(f"Time conversion requires task-clock, got {event}")

    def pct(value):
        return round(value * 100 / total, 4)

    def rows(counter):
        return [{"function": name, "self_percent": pct(own[name]),
                 "inclusive_percent": pct(inclusive[name]),
                 "self_ms": round(own[name] / 1e6, 3),
                 "inclusive_ms": round(inclusive[name] / 1e6, 3)}
                for name, _ in counter.most_common()]

    functions = rows(inclusive)
    app_functions = [row for row in functions if "com.example." in row["function"]]
    summary = {
        "event": event, "samples": samples, "sampled_cpu_seconds": total / 1e9,
        "sample_time_span_seconds": (last_time - first_time) / 1e9,
        "unknown_leaf_percent": pct(unknown), "no_callchain_percent": pct(no_callchain),
        "record_command": record_command, "simpleperf_metadata": metadata,
        "record_stats": dict(item.split("=", 1) for item in metadata.get("record_stat", "").split(",") if "=" in item),
        "threads": [{"tid": tid, "name": name, "cpu_percent": pct(period),
                     "cpu_seconds": period / 1e9} for (tid, name), period in threads.most_common()],
        "libraries": [{"library": name, "self_percent": pct(period)}
                      for name, period in libraries.most_common()],
        "top_self": rows(own)[:40], "app_functions": app_functions,
    }
    (directory / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    for filename, data in (("functions.csv", functions), ("app-functions.csv", app_functions)):
        with (directory / filename).open("w", newline="", encoding="utf-8-sig") as stream:
            writer = csv.DictWriter(stream, fieldnames=["function", "self_percent", "inclusive_percent", "self_ms", "inclusive_ms"])
            writer.writeheader()
            writer.writerows(data)

    def table(data):
        if not data:
            return "<p>No samples.</p>"
        keys = list(data[0])
        return "<table><thead><tr>" + "".join(f"<th>{html.escape(k)}</th>" for k in keys) + "</tr></thead><tbody>" + "".join(
            "<tr>" + "".join(f"<td>{html.escape(str(row[k]))}</td>" for k in keys) + "</tr>" for row in data
        ) + "</tbody></table>"

    document = """<!doctype html><html lang="en"><meta charset="utf-8">
<title>CarLauncher CPU sample summary</title><style>
body{font:16px system-ui;margin:32px;color:#202938}table{border-collapse:collapse;width:100%;font-size:13px}
td,th{border:1px solid #ddd;padding:8px;text-align:left;overflow-wrap:anywhere}th{background:#e9eef5}
tr:nth-child(even){background:#f6f8fb}h2{margin-top:32px}code{overflow-wrap:anywhere}</style>
<h1>CarLauncher dashboard CPU sample</h1>
<p>All percentages use the entire sampled app process as denominator, weighted by event period.
Self = executing this function; inclusive = this function or descendants. Inclusive rows overlap and must not be added.
task-clock:u measures user-space CPU, not wall time or GPU time. Emulator results are diagnostic, not a device performance benchmark.</p>"""
    document += f"<p>Samples: {samples:,}; sampled CPU: {total / 1e9:.3f}s; sample span: {(last_time-first_time)/1e9:.3f}s; unknown leaf: {pct(unknown):.3f}%.</p>"
    document += "<p>Recorder statistics: <code>" + html.escape(metadata.get("record_stat", "Unavailable")) + "</code></p>"
    document += "<p><a href='report.html'>Official simpleperf call graphs (requires CDN access)</a> | <a href='metadata.json'>Run metadata</a> | <a href='record.log'>Recorder log</a> | <a href='actions.jsonl'>Actions</a></p>"
    document += "<h2>Threads</h2>" + table(summary["threads"])
    document += "<h2>Functions sorted by self cost</h2>" + table(summary["top_self"])
    document += "<h2>Application functions sorted by inclusive cost</h2>" + table(app_functions[:50])
    document += "<h2>Libraries (self cost)</h2>" + table(summary["libraries"][:30])
    (directory / "summary.html").write_text(document + "</html>", encoding="utf-8")
    print(json.dumps({k: summary[k] for k in ("event", "samples", "sampled_cpu_seconds", "sample_time_span_seconds", "unknown_leaf_percent", "no_callchain_percent", "threads", "top_self")}, indent=2))


if __name__ == "__main__":
    main()
