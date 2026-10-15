"""Self-contained, period-weighted flame graph for simpleperf call stacks."""
import json


def new_node(name):
    return {'name': name, 'value': 0, 'self': 0, 'children': {}}


def add_stack(root, frames, period):
    if period <= 0:
        raise ValueError('Sample period must be positive')
    root['value'] += period
    node = root
    for frame in frames:
        node = node['children'].setdefault(frame, new_node(frame))
        node['value'] += period
    node['self'] += period


def write_flamegraph(path, root):
    if root['value'] <= 0:
        raise ValueError('Cannot render an empty flame graph')

    def pack(node):
        return dict(node, children=[pack(child) for child in sorted(
            node['children'].values(), key=lambda child: (-child['value'], child['name']))])

    # Symbols are untrusted input, including sequences which could close script.
    data = json.dumps(pack(root), ensure_ascii=True).replace('<', '\\u003c').replace('>', '\\u003e').replace('&', '\\u0026')
    path.write_text(HTML.replace('__STACK_DATA__', data), encoding='utf-8')


HTML = r'''<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CarLauncher simpleperf CPU flame graph</title>
<style>body{font:15px system-ui;margin:24px;color:#17212b}button,input{font:inherit;padding:7px;margin:4px}
svg{width:100%;height:auto;min-width:800px;font:12px monospace}#chart{overflow:auto;max-height:70vh;border:1px solid #aaa}
rect{stroke:#fff;stroke-width:.5;cursor:pointer}rect:hover{stroke:#000;stroke-width:2}
#detail{white-space:pre-wrap;overflow-wrap:anywhere;min-height:4em;background:#f2f4f6;padding:12px}
#crumb{overflow-wrap:anywhere}p{max-width:1000px}</style>
<h1>CarLauncher simpleperf CPU flame graph</h1>
<p>Width = sampled user-space CPU time, weighted by event period. Stack order: process → thread → callers → leaf. Root starts at the top; switch direction for a conventional flame graph.
Click a frame to zoom; hover or focus to read its full name and cost. Horizontal order is alphabetical/cost grouping, not time.
This is CPU consumption, not elapsed latency, frame rate or GPU time. Recursive frames are retained.</p>
<button id="reset">Reset zoom</button><button id="back">Back</button>
<label>Find function <input id="search" type="search" placeholder="e.g. com.example or RenderThread"></label>
<label><input id="inverted" type="checkbox" checked>Root at top</label>
<span id="matches" aria-live="polite"></span><p id="crumb"></p>
<div id="detail" aria-live="polite">Select or hover a frame.</div><div id="chart"></div>
<script id="stacks" type="application/json">__STACK_DATA__</script>
<script>
'use strict';
const root=JSON.parse(document.getElementById('stacks').textContent),NS='http://www.w3.org/2000/svg';
const chart=document.getElementById('chart'),detail=document.getElementById('detail');
let selected=root,history=[];
function element(tag,attrs){const e=document.createElementNS(NS,tag);for(const [k,v] of Object.entries(attrs))e.setAttribute(k,v);return e;}
function info(n){return n.name+'\nInclusive: '+(n.value/1e9).toFixed(6)+' CPU s ('+(100*n.value/root.value).toFixed(3)+'% of process)'+
 '\nSelf: '+(n.self/1e9).toFixed(6)+' CPU s ('+(100*n.self/root.value).toFixed(3)+'% of process)';}
function depth(n){return 1+Math.max(0,...n.children.map(depth));}
function color(name){let h=0;for(const c of name)h=(h*31+c.charCodeAt(0))>>>0;return 'hsl('+(h%55)+',85%,72%)';}
function matchedCost(n,q){if(n.name.toLowerCase().includes(q))return n.value;return n.children.reduce((s,c)=>s+matchedCost(c,q),0);}
function render(){
 const q=document.getElementById('search').value.toLowerCase(),height=depth(selected)*24+8,width=Math.max(800,chart.clientWidth-16);
 const svg=element('svg',{viewBox:'0 0 '+width+' '+height,height,role:'img','aria-label':'Interactive CPU flame graph'});
 document.getElementById('crumb').textContent='Zoom: '+selected.name+' | '+(selected.value/1e9).toFixed(6)+' sampled CPU s';
 document.getElementById('matches').textContent=q?'Matched: '+(100*matchedCost(root,q)/root.value).toFixed(3)+'% of process CPU':'';
 function draw(n,x,w,level){
  if(w<.15)return;
  const y=document.getElementById('inverted').checked?level*24:height-(level+1)*24,g=element('g',{}),r=element('rect',{x,y,width:w,height:23,fill:q&&n.name.toLowerCase().includes(q)?'#d773ef':color(n.name),tabindex:w>=10?'0':'-1'});
  const title=element('title',{});title.textContent=info(n);r.append(title);
  const show=()=>{detail.textContent=info(n);};r.addEventListener('mouseover',show);r.addEventListener('focus',show);
  const zoom=()=>{if(selected!==n){history.push(selected);selected=n;render();show();}};
  r.addEventListener('click',zoom);r.addEventListener('keydown',e=>{if(e.key==='Enter'){zoom();}});g.append(r);
  if(w>35){const text=element('text',{x:x+4,y:y+16,'pointer-events':'none'}),count=Math.max(0,Math.floor((w-8)/7));
   text.textContent=n.name.length>count?n.name.slice(0,Math.max(0,count-1))+'…':n.name;g.append(text);}
  svg.append(g);let offset=x;
  for(const child of n.children){const cw=w*child.value/n.value;draw(child,offset,cw,level+1);offset+=cw;}
 }
 draw(selected,0,width,0);chart.replaceChildren(svg);
}
document.getElementById('reset').onclick=()=>{selected=root;history=[];render();};
document.getElementById('back').onclick=()=>{selected=history.pop()||root;render();};
document.getElementById('search').oninput=render;
document.getElementById('inverted').onchange=()=>{render();chart.scrollTop=document.getElementById('inverted').checked?0:chart.scrollHeight;};render();
</script></html>'''
