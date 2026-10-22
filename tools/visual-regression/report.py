"""Build a portable before/after report from actual PNG captures (Pillow required)."""
import argparse
import hashlib
import html
import json
from pathlib import Path
import shutil
from PIL import Image, ImageChops

SCENES = {
    "home_demo": "演示首页",
    "map_fullscreen": "地图全屏",
    "media_queue": "媒体队列弹窗",
    "home_connected": "车辆已连接 · 首页",
    "vehicle_connected": "车辆已连接 · 控制页",
    "vehicle_disconnected": "车辆断开 · 控制页",
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--current", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    rows = []
    cards = []
    for name, title in SCENES.items():
        before_path = args.baseline / f"{name}.png"
        after_path = args.current / f"{name}.png"
        try:
            before = Image.open(before_path).convert("RGB")
            after = Image.open(after_path).convert("RGB")
            if before.size != after.size:
                raise ValueError(f"Size mismatch: {before.size} / {after.size}")
        except (OSError, ValueError) as error:
            rows.append({"scene": name, "error": str(error), "changed_pixels": None})
            cards.append(f'<section><h2>{title}</h2><p class="bad">未完成比较：{html.escape(str(error))}</p></section>')
            continue
        diff = ImageChops.difference(before, after)
        red, green, blue = diff.split()
        mask = ImageChops.lighter(ImageChops.lighter(red, green), blue).point(lambda p: 255 if p else 0)
        changed = mask.histogram()[255]
        total = before.width * before.height
        output_images = args.output / "images"
        output_images.mkdir(exist_ok=True)
        shutil.copy2(before_path, output_images / f"{name}-before.png")
        shutil.copy2(after_path, output_images / f"{name}-after.png")
        heat = Image.new("RGB", before.size, "#111827")
        heat.paste("#ff2469", mask=mask)
        heat.save(output_images / f"{name}-diff.png")
        rows.append({"scene": name, "title": title, "width": before.width, "height": before.height,
                     "changed_pixels": changed, "total_pixels": total,
                     "changed_ratio": changed / total, "baseline_sha256": digest(before_path),
                     "current_sha256": digest(after_path)})
        pictures = "".join(
            f'<figure><figcaption>{label}</figcaption><a href="images/{name}-{suffix}.png">'
            f'<img loading="lazy" src="images/{name}-{suffix}.png" alt="{title} {label}"></a></figure>'
            for suffix, label in [("before", "改造前基准"), ("after", "改造后"), ("diff", "差异高亮")])
        cards.append(f'<section><h2>{title}</h2><p class="{"bad" if changed else "ok"}">'
                     f'{changed:,} / {total:,} 像素变化（{changed / total:.6%}）'
                     f' · {before.width} × {before.height}</p><div class="grid">{pictures}</div></section>')
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    shutil.copy2(args.manifest, args.output / "source-manifest.json")
    summary = {"comparison": "Exact RGB pixel equality; no masks, shifts or tolerance",
               "reference_commit": manifest["reference_commit"], "scenes": rows,
               "incomplete_scenes": sum(row["changed_pixels"] is None for row in rows),
               "changed_scenes": sum((row["changed_pixels"] or 0) > 0 for row in rows)}
    (args.output / "pixel-results.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>首页重构截图回归报告</title>
<style>body{font:16px/1.65 system-ui,sans-serif;background:#0c1220;color:#e5e7eb;margin:0;padding:32px}
main{max-width:1800px;margin:auto}h1{font-size:30px}h2{font-size:22px}a{color:#73baff}
.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}figure{margin:0}
img{width:100%;border:1px solid #374151}section{background:#162032;padding:22px;margin:24px 0;border-radius:12px}
.ok{color:#70e0ad}.bad{color:#ff8caa}code{overflow-wrap:anywhere}.note{max-width:1100px}
@media(max-width:950px){.grid{grid-template-columns:1fr}body{padding:16px}}
</style><main><h1>CarLauncher 首页重构 · 截图回归</h1>'''
    page += f'<p class="{"bad" if summary["changed_scenes"] or summary["incomplete_scenes"] else "ok"}">6 个场景，{summary["changed_scenes"]} 个存在像素差异，{summary["incomplete_scenes"]} 个未完成比较。</p>'
    run_file = args.output / "run.json"
    if run_file.exists():
        run = json.loads(run_file.read_text(encoding="utf-8"))
        failed = run.get("capture_exit", 1) or run.get("verify_exit", 1)
        page += f'<section><h2>CI 执行记录</h2><p class="{"bad" if failed else "ok"}">'
        page += '截图任务执行失败；请查看日志。' if failed else '截图录制与 Roborazzi 校验均成功。'
        page += '</p><pre>'
        page += html.escape(json.dumps(run, ensure_ascii=False, indent=2)) + '</pre></section>'
    page += f'''<div class="note"><p>基准来自提交 <code>{html.escape(manifest['reference_commit'])}</code>
中的 4 个重构前文件，其余未提交改动与测试夹具保持相同。源文件哈希和替换清单见
<a href="source-manifest.json">源码清单</a>；图片 SHA-256 与精确像素计数见
<a href="pixel-results.json">对比结果 JSON</a>。</p>
<p>Windows / JDK 21 / Robolectric Android API 35 Native Graphics / Roborazzi 1.76.0。
窗口 1408 × 792 dp，mdpi，字体缩放 1，en-US，Asia/Shanghai；日期固定为 2026-10-21 10:00。
同一套测试渲染真实 CarLauncherScreen 和对应版本的 ViewModel，通过语义点击打开地图与队列。
车辆状态来自固定 Service 快照替身，不启动 Binder、Car 服务、SOME/IP 或 JNI。</p>
<p>采用零像素容差，无动态区域遮罩。深色差异图表示相同，粉红色表示变化。
点击图片可查看原始尺寸。基准没有用改造后截图覆盖。</p>
<p>本报告是本机渲染的视觉回归证据，不是 AAOS 设备、Activity 生命周期、真实连接或 TaskView 验收。</p></div>'''
    validation_file = args.output / "validation.json"
    if validation_file.exists():
        validation = json.loads(validation_file.read_text(encoding="utf-8"))
        page += '<section><h2>验证与证据边界</h2><ul>'
        for item in validation["checks"]:
            page += f'<li>{html.escape(item)}</li>'
        page += '</ul><p><a href="validation.json">验证记录</a> · '
        page += '<a href="roborazzi/index.html">Roborazzi 原生报告</a> · '
        page += '<a href="negative/home_demo_compare.png">负向校验差异图</a></p></section>'
    page += "".join(cards) + "</main></html>"
    (args.output / "summary-zh.html").write_text(page, encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 1 if summary["changed_scenes"] or summary["incomplete_scenes"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
