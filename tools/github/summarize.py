#!/usr/bin/env python3
"""Read existing gate evidence and publish a Chinese acceptance summary (no gate changes)."""
import argparse
import html
import json
import os
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
JOBS = {'build': '构建与代码检查', 'ui-regression': '截图回归',
        'performance-build': 'JMH 性能门禁', 'simpleperf': '设备性能门禁'}
SCENES = {'home_demo', 'map_fullscreen', 'media_queue', 'home_connected',
          'vehicle_connected', 'vehicle_disconnected'}
STATES = {'passed': '✅ 通过', 'failed': '❌ 失败', 'unknown': '⚠️ 证据缺失',
          'skipped': '⏭️ 未执行', 'cancelled': '⏹️ 已取消'}


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def cell(value):
    return html.escape(str(value)).replace('|', '&#124;').replace('\n', ' ').replace('`', '&#96;')


def collect(kind, root=ROOT):
    result = {'state': 'unknown', 'brief': '未找到完整报告', 'checks': [],
              'links': {label: os.getenv(key, '') for label, key in
                        [('报告下载', 'REPORT_URL'), ('APK 下载', 'APK_URL'), ('AAR 下载', 'AAR_URL')]}}
    try:
        if kind == 'ui':
            runs = list((root / 'build/ui-regression/ci').glob('*/run.json'))
            runs = [p for p in runs if read(p).get('commit') == os.environ['GITHUB_SHA']]
            if len(runs) != 1:
                raise ValueError('未找到唯一的本次提交截图报告')
            run = read(runs[0])
            pixels = read(runs[0].with_name('pixel-results.json'))
            scenes = pixels['scenes']
            complete = len(scenes) == 6 and {s['scene'] for s in scenes} == SCENES
            passed = (complete and run.get('capture_exit') == 0 and run.get('verify_exit') == 0
                      and pixels['changed_scenes'] == 0 and pixels['incomplete_scenes'] == 0
                      and all(s['changed_pixels'] == 0 for s in scenes))
            result['state'] = 'passed' if passed else 'failed'
            result['brief'] = (f"{len(scenes)}/6 场景；变化 {pixels['changed_scenes']}，"
                               f"缺失 {pixels['incomplete_scenes']}；零像素容差")
            result['checks'] = [[s['title'], 'passed' if s['changed_pixels'] == 0 else 'failed',
                                 f"变化像素：{s['changed_pixels']}"] for s in scenes]
        else:
            directory = {'build': 'code-review', 'jmh': 'jvm-performance',
                         'simpleperf': 'simpleperf/gitlab'}[kind]
            data = read(root / 'build/reports' / directory / 'summary.json')
            if data.get('commit') != os.environ['GITHUB_SHA']:
                raise ValueError('报告提交与本次运行不一致')
            checks = data['checks']
            if not checks or any(len(c) != 3 for c in checks):
                raise ValueError('报告没有有效检查项')
            result['checks'] = checks
            result['state'] = ('failed' if any(c[1] == 'failed' for c in checks) else
                               'passed' if all(c[1] in ('passed', 'skipped') for c in checks)
                               and any(c[1] == 'passed' for c in checks) else 'unknown')
            result['brief'] = f"{sum(c[1] == 'passed' for c in checks)}/{len(checks)} 项通过"
            if kind != 'build' and any(c[1] != 'passed' for c in checks) and result['state'] != 'failed':
                result['state'] = 'unknown'
            if kind == 'build':
                for module in ('app', 'someip-android'):
                    files = list((root / module / 'build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
                    if not files:
                        raise ValueError(f'{module} 的 JVM 测试报告缺失')
                    totals = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
                    for path in files:
                        tree = ET.parse(path).getroot()
                        for suite in [tree] if tree.tag == 'testsuite' else tree.findall('testsuite'):
                            for key in totals:
                                totals[key] += int(suite.get(key, 0))
                    if totals['failures'] or totals['errors']:
                        result['state'] = 'failed'
                    result['brief'] += (f"；{module}：{totals['tests']} 测试 / "
                                        f"{totals['failures'] + totals['errors']} 失败 / {totals['skipped']} 跳过")
            elif kind == 'simpleperf':
                cpu = next((c[2] for c in checks if c[0] == 'scene_cpu_regression'), '')
                if not cpu:
                    result['state'] = 'failed' if result['state'] == 'failed' else 'unknown'
                else:
                    metrics = dict(re.findall(r'(baseline|current|limit|delta)=([^;]+)', cpu))
                    result['brief'] += (f"；CPU {metrics.get('current', '未知').split(' ')[0]} 秒"
                                        f" / 上限 {metrics.get('limit', '未知').split(' ')[0]} 秒"
                                        f"；较基准 {metrics.get('delta', '未知')}")
            elif not any(c[0] == 'measurements_present' for c in checks):
                result['state'] = 'failed' if result['state'] == 'failed' else 'unknown'
    except (OSError, ValueError, KeyError, TypeError, ET.ParseError) as error:
        result['state'] = 'unknown'
        result['brief'] = '证据缺失或不可读：' + str(error)
    if os.getenv('JOB_STATUS') in ('failure', 'cancelled'):
        result['state'] = 'failed' if os.environ['JOB_STATUS'] == 'failure' else 'cancelled'
        result['brief'] = '任务未成功完成；已有报告：' + result['brief']
    return result


def render(needs):
    rows, details, states = [], [], []
    for job, title in JOBS.items():
        source = needs.get(job, {})
        try:
            data = json.loads(source.get('outputs', {}).get('acceptance', '{}'))
            if not isinstance(data, dict):
                data = {}
        except (TypeError, ValueError):
            data = {}
        status = source.get('result')
        state = {'failure': 'failed', 'cancelled': 'cancelled', 'skipped': 'skipped'}.get(status,
                    data.get('state', 'unknown') if status == 'success' else 'unknown')
        states.append(state)
        brief = data.get('brief', '没有可用报告；请查看该任务的首个失败步骤')
        if state == 'skipped':
            brief = '本次运行条件不适用或上游未通过；未完成此项验收'
        links = []
        for label, url in data.get('links', {}).items():
            if url.startswith('https://github.com/') and not any(c in url for c in '\n\r()< >'):
                links.append(f'[{cell(label)}]({url})')
        rows.append(f"| {title} | {STATES.get(state, STATES['unknown'])} | {cell(brief)} | "
                    + (' · '.join(links) or '未上传或不可用') + ' |')
        checks = data.get('checks', [])
        if checks:
            details += [f'<details><summary>{title}：展开检查明细</summary>', '',
                        '| 检查 | 结果 | 说明 |', '| --- | --- | --- |']
            details += ['| ' + ' | '.join([cell(c[0]), STATES.get(c[1], cell(c[1])), cell(c[2])]) + ' |'
                        for c in checks]
            details += ['', '</details>', '']
    conclusion = ('❌ 验收未通过' if 'failed' in states else '✅ 四项验收通过' if
                  all(s == 'passed' for s in states) else '⚠️ 验收不完整：存在未执行、取消或证据缺失')
    return '\n'.join(['## 中文验收摘要', '', f'**{conclusion}**', '',
        f"提交 `{cell(os.getenv('GITHUB_SHA', '')[:12])}` · 第 {cell(os.getenv('GITHUB_RUN_NUMBER', ''))} 次运行"
        f" · 尝试 {cell(os.getenv('GITHUB_RUN_ATTEMPT', ''))}", '',
        '| 验收项目 | 结果 | 关键结论 | 下载入口 |', '| --- | --- | --- | --- |', *rows, '',
        '下载后解压：截图打开 `summary-zh.html`；性能打开 `index.html`，设备火焰图打开 `raw/flamegraph.html`。',
        '下载需要登录并具备仓库访问权限；产物保留 14 天。未上传时不会提供虚假下载链接。', '',
        '构建成功包含 APK/AAR、来源校验及代码检查；覆盖率报告生成不代表执行了 90%/85% 阈值校验。',
        '截图通过仅代表六个固定场景的视觉回归；性能通过仅对应本次基准宿主、设备与场景。基准和阈值未修改。', '',
        *details])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--kind', choices=('build', 'ui', 'jmh', 'simpleperf'))
    parser.add_argument('--aggregate', action='store_true')
    args = parser.parse_args()
    if args.aggregate:
        document = render(json.loads(os.environ['NEEDS_JSON']))
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as stream:
            stream.write(document + '\n')
    elif args.kind:
        with Path(os.environ['GITHUB_OUTPUT']).open('a', encoding='utf-8') as stream:
            stream.write('acceptance=' + json.dumps(collect(args.kind), ensure_ascii=True) + '\n')
    else:
        parser.error('指定 --kind 或 --aggregate')


if __name__ == '__main__':
    main()
