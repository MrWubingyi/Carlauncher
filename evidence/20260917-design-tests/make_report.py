"""Archive this design-test run and produce a unified Chinese evidence report."""
from pathlib import Path
import collections
import datetime
import hashlib
import html
import importlib.util
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/design-tests'
OUT = ROOT / 'evidence/20260917-design-tests'
OUT.mkdir(parents=True, exist_ok=True)
gate_log = WORK/'merged-coverage.log'
assert gate_log.exists(), 'Coverage gate has not run yet'
assert 'Rule violated' in gate_log.read_text(encoding='utf-8-sig'), 'Reconcile gate outcome before reporting'
assert (WORK/'current-device.ec').stat().st_size > 16, 'Missing current device execution data'
spec = importlib.util.spec_from_file_location('pipeline', Path.home() / '.codex/skills/android-unit-test-workflow/scripts/android_test_pipeline.py')
pipeline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pipeline)

def archive(src, dest):
    src, dest = Path(src), OUT / dest
    if not src.exists():
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dest)

for layer, source in [('unit', ROOT/'app/build/test-results/testDebugUnitTest'),
                      ('instrumentation', ROOT/'app/build/outputs/androidTest-results/connected'),
                      ('baseline-unit', WORK/'baseline/unit'),
                      ('baseline-instrumentation', WORK/'baseline/instrumentation')]:
    for file in source.rglob('TEST-*.xml'):
        archive(file, Path(layer)/file.name)
for file in WORK.glob('*.log'):
    archive(file, Path('logs')/file.name)
for file in (WORK/'before-approval').glob('*.log'):
    archive(file, Path('logs/before-approval')/file.name)
for file in WORK.glob('*-stage.*'):
    archive(file, Path('stages')/file.name)
archive(ROOT/'docs/reviews/design-tests-prereview-20260917.approval.json', 'approval.json')
archive(WORK/'baseline/gradle.log', 'logs/baseline.log')
archive(WORK/'current-coverage.init.gradle', 'current-coverage.init.gradle')
archive(WORK/'collect_device_coverage.py', 'collect_device_coverage.py')
archive(Path(__file__), 'make_report.py')
archive(WORK/'unit-only-jacoco.xml', 'unit-only-jacoco.xml')
archive(WORK/'current-device.ec', 'execution/current-device.ec')
archive(ROOT/'app/build/jacoco/testDebugUnitTest.exec', 'execution/current-unit.exec')
archive(ROOT/'app/build/reports/jacoco/fullDebugUnitTestCoverageReport/fullDebugUnitTestCoverageReport.xml', 'jacoco.xml')
for suffix in ['xml', 'html', 'sarif']:
    archive(ROOT/f'app/build/reports/lint-results-debug.{suffix}', f'lint.{suffix}')

unit = pipeline.junit_summary(OUT/'unit')
device = pipeline.junit_summary(OUT/'instrumentation')
baseline_unit = pipeline.junit_summary(OUT/'baseline-unit')
baseline_device = pipeline.junit_summary(OUT/'baseline-instrumentation')
coverage = pipeline.jacoco_summary(OUT/'jacoco.xml')
unit_coverage = pipeline.jacoco_summary(OUT/'unit-only-jacoco.xml')
lint = pipeline.lint_summary(OUT/'lint.xml')

failures, skipped = [], []
for layer in ['unit', 'instrumentation']:
    for file in (OUT/layer).glob('TEST-*.xml'):
        for case in ET.parse(file).getroot().iter('testcase'):
            name = case.get('classname', '') + '.' + case.get('name', '')
            for tag in ['failure', 'error']:
                failure = case.find(tag)
                if failure is not None:
                    failures.append({'layer': layer, 'test': name, 'detail': failure.text or failure.get('message', '')})
            if case.find('skipped') is not None:
                skipped.append(name)

tree = ET.parse(OUT/'jacoco.xml').getroot()
unit_tree = ET.parse(OUT/'unit-only-jacoco.xml').getroot()
def source_map(root):
    return {(pkg.get('name') + '/' + src.get('name')): src
            for pkg in root.findall('package') for src in pkg.findall('sourcefile')}
covered_sources = source_map(tree)
unit_sources = source_map(unit_tree)

def counters(node, kind):
    if node is None:
        return (0, 0)
    counter = next((n for n in node.findall('counter') if n.get('type') == kind), None)
    return (0, 0) if counter is None else (int(counter.get('covered')), int(counter.get('missed')))

def ratio(covered, missed):
    return '不适用（无可执行计数）' if covered + missed == 0 else f'{covered / (covered + missed):.2%} ({covered}/{covered+missed})'

jvm_names = {'VehicleState', 'VehicleStateValidator', 'Gear', 'TurnSignal', 'WarningState', 'DataValidity',
             'DataSourceStatus', 'DataStatus', 'VehicleProtocol', 'SourceType', 'MockVehicleDataSource',
             'CockpitUiState', 'CockpitConnectionState', 'SomeipConnectionMonitor', 'SomeipPayloadCodec',
             'NetworkStateTracker', 'VehicleEventCodec'}
interfaces = {'VehicleDataSource', 'NativeVehicleTransport'}
tests = list((ROOT/'app/src/test').rglob('*.java')) + list((ROOT/'app/src/androidTest').rglob('*.java'))
test_texts = [(t, t.read_text(encoding='utf-8-sig')) for t in tests]
inventory = []
for path in sorted((ROOT/'app/src/main/java').rglob('*.java')):
    key = path.relative_to(ROOT/'app/src/main/java').as_posix()
    source = covered_sources.get(key)
    line = counters(source, 'LINE')
    branch = counters(source, 'BRANCH')
    runtime = 'JVM' if path.stem in jvm_names else 'instrumentation'
    if path.stem in interfaces:
        runtime = '接口：由实现类行为测试验证'
    if path.stem in {'ThemePreferences', 'VSomeIpNativeTransport'}:
        runtime = 'JVM 决策 + instrumentation 平台边界'
    related = [t.relative_to(ROOT).as_posix() for t, text in test_texts if path.stem in text]
    inventory.append({'source': key, 'runtime': runtime, 'line': line, 'branch': branch,
                      'present': source is not None, 'related': related,
                      'device_added_lines': line[0] - counters(unit_sources.get(key), 'LINE')[0]})

new_files = ['app/src/test/java/com/example/carlauncher/model/VehicleDesignBoundaryTest.java',
             'app/src/test/java/com/example/carlauncher/model/VehicleSnapshotDesignTest.java']
diff = subprocess.run(['git', 'diff', '--', 'app/src/test', 'app/src/androidTest'], cwd=ROOT, capture_output=True, check=True).stdout.decode('utf-8')
(OUT/'tests.patch').write_text(diff, encoding='utf-8')
for name in new_files:
    archive(ROOT/name, Path('new-tests')/Path(name).name)
result = dict(createdAt=datetime.datetime.now().astimezone().isoformat(), baseline_unit=baseline_unit,
              baseline_device=baseline_device, unit=unit, instrumentation=device, coverage=coverage,
              unit_coverage=unit_coverage, lint=lint, failures=failures, skipped=skipped, inventory=inventory,
              gate='FAILED', overall='FAILED', new_jvm_cases=18, new_instrumentation_cases=6)
result['approval'] = dict(reviewId='REV-5d10a82ff17a4f77b7cd107a627d0fc4', revision=1,
    contentSha256='9e622edb325c50ec29a132a1e75280e561ab5bbbf7201a19c549dcc94ab76232',
    outcome='APPROVED', reviewer='武', decidedAt='2026-09-17T09:20:23.449+00:00', comment='通过', answers={})
(OUT/'results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')

e = html.escape
def table(headers, rows):
    return '<table><thead><tr>' + ''.join('<th>'+e(h)+'</th>' for h in headers) + '</tr></thead><tbody>' + ''.join('<tr>'+''.join('<td>'+str(c)+'</td>' for c in row)+'</tr>' for row in rows) + '</tbody></table>'
def link(path, title):
    return f'<a href="{e(path)}">{e(title)}</a>'
def totals(s):
    return [s['tests'], s['tests']-s['failures']-s['errors']-s['skipped'], s['failures'], s['errors'], s['skipped']]

body = '<h1>CarLauncher 设计测试报告</h1><p class="bad">未通过：新增测试复现 2 个缺陷，全模块覆盖率门禁未达标。</p>'
body += '<p>2026-09-17 · Debug · HEAD f2f0de0 · JDK 21 / Gradle 9.5.0 · Automotive API 35 / Android user 10。新增 24 个执行用例：JVM 18、设备 6。未修改生产代码。</p>'
body += '<h2>审批与执行边界</h2><p>REV-5d10a82ff17a4f77b7cd107a627d0fc4，revision 1，APPROVED；审批人“武”，2026-09-17T09:20:23.449+00:00，意见“通过”，answers={}。SHA-256：9e622edb325c50ec29a132a1e75280e561ab5bbbf7201a19c549dcc94ab76232。执行前逐项确认10份文件与批准快照一致。此前误执行的日志单独留存；当前完整两层回归均为此次批准后重新执行。</p>'
body += '<h2>测试结果（两层分别统计）</h2>'
body += table(['阶段','总数','通过','失败','错误','跳过'], [['基线 JVM（Gradle UP-TO-DATE）',*totals(baseline_unit)],['基线设备',*totals(baseline_device)],['当前完整 JVM',*totals(unit)],['当前完整设备',*totals(device)]])
body += '<p>基线包含用户原有 SOME/IP 用例；它们不是本次新增。Android 运行器进度输出对跳过用例计数不同，以上只取 JUnit XML。设备覆盖率采集重跑不是额外测试总数。</p>'
body += '<p>新增24项中：JVM 17通过、1失败；设备5通过、1失败，合计22通过、2失败。</p>'
body += '<h2>失败与未执行场景</h2>'
for failure in failures:
    body += '<details open><summary>'+e(failure['test'])+'</summary><pre>'+e(failure['detail'])+'</pre></details>'
body += '<p>ISSUE-D01：Mock 停止清空 listener 后，已排队 seq700 任务仍直接调用 listener。ISSUE-D02：BroadcastLabActivity.onStop 清空 binding，使同实例 onStart 跳过重新注册。保留失败断言，没有以 @Ignore、重试或修改生产行为掩盖失败。</p>'
body += '<p>跳过的远端/原生集成探针：</p><ul>'+''.join('<li>'+e(t)+'</li>' for t in skipped)+'</ul>'
body += '<h2>覆盖率和分母</h2>'
rows = []
for kind in ['line','branch','class','method','instruction','complexity']:
    a, b = unit_coverage['counters'].get(kind,{}), coverage['counters'].get(kind,{})
    rows.append([kind, ratio(a.get('covered',0),a.get('missed',0)), ratio(b.get('covered',0),b.get('missed',0))])
body += table(['计数','仅当前完整 JVM','当前 JVM + 设备'], rows)
missing = [i['source'] for i in inventory if not i['present'] and Path(i['source']).stem not in interfaces]
body += '<p>门槛：LINE ≥90%，BRANCH ≥85%；verifyFullDebugUnitTestCoverage 实际返回失败。分母为 app 全部手写 Java 可执行类（含 Activity、Fragment、Service、VHAL、SOME/IP），仅使用原配置生成类排除项 R、BuildConfig、Manifest、ViewBinding；没有手工生产类白名单。C++/JNI 不属于 JaCoCo 指标。</p>'
body += '<p>UI 决策已有 CockpitUiState/CockpitConnectionState 的 JVM 测试；资源、绑定、页面生命周期、Fragment恢复由设备层验证。上表两列都使用完整模块分母，不将 JVM 数字称为单独 UI 决策覆盖率。</p>'
body += f'<p>盘点 {len(inventory)} 个 Java 源文件；可执行源文件缺失：{e(str(missing))}。接口没有可执行计数，单独列出。详细逐文件证据如下。</p>'
body += '<p>AGP 默认 user0 路径采集失败、connected .ec 为0字节；通过同套测试的 user10 指定 coverageFile 重跑恢复。仅合并归档的 current-unit.exec 和 current-device.ec，未使用 9 月4日旧 manual-user10-coverage.ec。设备新增覆盖行的类可从下表确认。局部 UI 决策、设备集成和全模块指标不互相替代。</p>'
body += '<h2>Android Lint</h2>'
body += table(['状态','Fatal','Error','Warning','Information','Other'], [['报告有效' if lint['valid'] else '缺失/无效',lint['fatal'],lint['errors'],lint['warnings'],lint['information'],lint['other']]])
body += '<p>审批后再次调用lintDebug成功；源码未变，Gradle复用17:15生成的有效Lint产物（UP-TO-DATE），没有把它描述为重新分析产生的新发现。</p>'
body += '<h2>执行命令与退出状态</h2>'
body += table(['阶段','命令（工程根目录；JAVA_HOME 设 JDK21）','退出状态'], [
    ['基线','gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest --continue',0],
    ['定向 JVM + 设备编译','gradlew :app:testDebugUnitTest --tests model.VehicleDesignBoundaryTest --tests model.VehicleSnapshotDesignTest --tests data.mock.MockVehicleDataSourceTest :app:compileDebugAndroidTestJavaWithJavac --continue（类名前含完整包名）','1；25项中1项失败；设备编译成功'],
    ['完整 JVM','android_test_pipeline.py test --project . --jdk …',1],
    ['完整设备','android_test_pipeline.py instrumentation --project . --jdk …',1],
    ['设备覆盖率恢复','adb shell am instrument --user 10 -w -r -e coverage true -e coverageFile /data/user/10/com.example.carlauncher/files/design-tests-20260917.ec com.example.carlauncher.test/androidx.test.runner.AndroidJUnitRunner','ADB命令0；JUnit仍有1项失败'],
    ['首次单层覆盖率/Lint','gradlew :app:fullDebugUnitTestCoverageReport :app:lintDebug -x :app:testDebugUnitTest -I … -PdesignUnitOnly=true --continue','1；Gradle缺编译输出依赖'],
    ['修正临时依赖后单层覆盖率/Lint','同上，init脚本补充dependsOn compileDebugJavaWithJavac',0],
    ['合并覆盖率/门禁','gradlew :app:fullDebugUnitTestCoverageReport :app:verifyFullDebugUnitTestCoverage -x :app:testDebugUnitTest -I build/design-tests/current-coverage.init.gradle --continue','1；阈值不足']])
body += '<p>覆盖率阶段 -x 仅用于读取已经执行失败的完整 JVM 数据，避免失败的测试依赖阻断报告，不表示测试被跳过或通过。临时 init 脚本只修正证据输入和任务依赖，不改变分母或阈值。</p>'
body += '<h2>统一资料入口</h2><ul>'
for path, title in [('../../docs/CarLauncher设计测试输入基线-20260917.md','六阶段输入基线与 TC-D01–14 追溯'),('results.json','机器可读结果与源码清单'),('tests.patch','本次既有测试增量'),('new-tests/VehicleDesignBoundaryTest.java','新增边界测试'),('new-tests/VehicleSnapshotDesignTest.java','新增快照测试'),('jacoco.xml','当前合并 JaCoCo XML'),('unit-only-jacoco.xml','当前 JVM JaCoCo XML'),('../../app/build/reports/jacoco/fullDebugUnitTestCoverageReport/html/index.html','JaCoCo 逐行 HTML（构建目录可能被后续构建覆盖）'),('lint.html','归档 Lint HTML'),('lint.xml','归档 Lint XML'),('../../app/build/reports/tests/testDebugUnitTest/index.html','JUnit HTML'),('../../app/build/reports/androidTests/connected/debug/index.html','设备 JUnit HTML'),('logs/unit.log','完整 JVM 日志'),('logs/device.log','完整设备日志'),('logs/device-coverage-recovery.log','设备覆盖率恢复日志'),('logs/merged-coverage.log','覆盖率门禁日志'),('logs/unit-coverage-lint-retry.log','Lint 执行日志'),('current-coverage.init.gradle','本次覆盖率输入脚本')]:
    body += '<li>'+link(path,title)+'</li>'
for path, title in [('approval.json','完整审批记录与送审快照'),('../../docs/CarLauncher设计测试结果-20260917.md','本轮结果与后续缺陷交接'),('lint.sarif','Lint SARIF'),('stages/unit-stage.html','JVM阶段报告'),('stages/device-stage.html','设备阶段报告'),('../../build/reports/problems/problems-report.html','Gradle诊断报告'),('logs/before-approval/unit.log','审批前误执行：JVM日志'),('logs/before-approval/device.log','审批前误执行：设备日志'),('make_report.py','本报告归档生成脚本'),('collect_device_coverage.py','设备二进制覆盖率采集脚本')]:
    body += '<li>'+link(path,title)+'</li>'
for layer in ['unit','instrumentation']:
    for file in sorted((OUT/layer).glob('TEST-*.xml')):
        body += '<li>'+link(file.relative_to(OUT).as_posix(),layer+' / '+file.name)+'</li>'
body += '</ul><h2>全模块源文件盘点</h2><p>测试文件引用只用于定位，不自动代表行为覆盖；0行覆盖和未执行的框架场景仍是缺口。</p>'
body += table(['生产源文件','执行层','行覆盖','分支覆盖','设备新增覆盖行'],[[e(i['source']),e(i['runtime']),ratio(*i['line']),ratio(*i['branch']),i['device_added_lines']] for i in inventory])
body += '<h2>剩余限制</h2><p>设计中的 TCP 状态机已过时；VHAL 专项、单栏返回重选、数据库异步退出和远端 JNI 探针未完成验收。当前设备仅证明所选布局。未定义性能测量环境与阈值，不作性能通过结论。内容审阅 APPROVED 与测试失败是独立状态。</p>'
page = '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>CarLauncher 设计测试报告</title><style>body{font:15px/1.6 system-ui,"Microsoft YaHei",sans-serif;max-width:1250px;margin:30px auto;padding:0 24px;color:#192533}h1,h2{color:#183958}table{border-collapse:collapse;width:100%;margin:18px 0}td,th{border:1px solid #d5dce3;padding:9px;text-align:left;overflow-wrap:anywhere}th{background:#edf2f7}pre{white-space:pre-wrap;max-height:320px;overflow:auto;background:#f3f5f7;padding:14px}a{color:#075dab}.bad{background:#ffe6e4;border-left:5px solid #c73226;padding:15px;font-weight:bold}details{margin:16px 0}</style>'+body+'</html>'
(OUT/'summary-zh.html').write_text(page, encoding='utf-8')
manifest = {p.relative_to(OUT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in OUT.rglob('*') if p.is_file() and p.name != 'sha256.json'}
(OUT/'sha256.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'unit':unit,'device':device,'coverage':coverage,'lint':lint,'source_files':len(inventory),'missing_sources':missing},ensure_ascii=False,indent=2))
