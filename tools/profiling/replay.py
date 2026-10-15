"""Replay a fixed baseline schedule on Android's monotonic uptime clock."""
import json
import math


def shell_script(entries, hold_seconds):
    lines = ['#!/system/bin/sh', 'set -eu',
             'clock() { read up rest < /proc/uptime; now=$((${up%.*} * 100 + 1${up#*.} - 100)); }',
             'clock', 'start=$now']
    previous = -1
    for index, entry in enumerate(entries):
        seconds = entry['seconds']
        command = entry['input']
        expected_length = {'tap': 3, 'swipe': 6}.get(command[0])
        if (not isinstance(seconds, (int, float)) or not math.isfinite(seconds)
                or seconds < 0 or seconds < previous or seconds >= hold_seconds
                or len(command) != expected_length
                or any(type(value) is not int or value < 0 for value in command[1:])):
            raise ValueError('Invalid fixed replay schedule')
        previous = seconds
        tick = round(seconds * 100)
        lines += [f'target=$((start + {tick}))', 'clock', 'delay=$((target - now))',
                  'if [ "$delay" -gt 0 ]; then sleep "$(printf \'%d.%02d\' $((delay / 100)) $((delay % 100)))"; fi',
                  'clock', 'began=$((now - start))',
                  'input ' + ' '.join(map(str, command)), 'clock',
                  f'printf \'REPLAY {index} %s %s\\n\' "$began" "$((now - start))"']
    lines += [f'target=$((start + {round(hold_seconds * 100)}))', 'clock',
              'delay=$((target - now))',
              'if [ "$delay" -gt 0 ]; then sleep "$(printf \'%d.%02d\' $((delay / 100)) $((delay % 100)))"; fi', '']
    return '\n'.join(lines)


def parse_output(text, entries, phase):
    results = []
    for line in text.splitlines():
        if not line.startswith('REPLAY '):
            continue
        _, index, began, finished = line.split()
        if int(index) != len(results) or int(index) >= len(entries):
            raise ValueError('Missing, duplicated or reordered device action')
        entry = entries[int(index)]
        results.append(dict(entry, phase=phase, index=int(index),
                            actual_seconds=int(began) / 100,
                            finished_seconds=int(finished) / 100))
    return results


def validate_results(results, entries, duration):
    if len(results) != len(entries):
        raise ValueError(f'Incomplete replay: {len(results)}/{len(entries)} actions')
    previous_finish = 0
    for actual, planned in zip(results, entries):
        began, finished = actual['actual_seconds'], actual['finished_seconds']
        if (not all(isinstance(v, (int, float)) and math.isfinite(v) for v in (began, finished))
                or actual['action'] != planned['action'] or actual['input'] != planned['input']
                or began < planned['seconds'] - .011 or began < previous_finish
                or finished < began or (duration is not None and finished > duration)):
            raise ValueError('Replay incomplete, reordered, early or outside recording window: ' + json.dumps(actual))
        previous_finish = finished
