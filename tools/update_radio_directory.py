#!/usr/bin/env python3
"""Import the pinned Wikipedia/ABS captures documented in docs/RADIO_METADATA.md.
Developer-only dependencies: pandas and lxml. No network or Android dependency.
"""
from lxml import html
import pandas as pd,io,re,json,collections
from pathlib import Path
import argparse
parser=argparse.ArgumentParser(description="Regenerate offline station/border assets from reviewed source captures")
parser.add_argument("--stations-html",type=Path,required=True)
parser.add_argument("--boundaries-geojson",type=Path,required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
s=html.parse(str(args.stations_html))
states={'Australian Capital Territory (ACT)':'ACT','New South Wales':'NSW','Victoria':'VIC','Queensland':'QLD','South Australia':'SA','Western Australia':'WA','Tasmania':'TAS','Northern Territory':'NT'}
rows=set();skipped=collections.Counter()
for table in s.xpath('//table[contains(@class,"wikitable")]'):
 h=table.xpath('preceding::h2[1]');state=states.get(' '.join(''.join(h[0].itertext()).split()) if h else '')
 if not state:continue
 region=' '.join(''.join(table.xpath('preceding::h3[1]')[0].itertext()).split())
 try:d=pd.read_html(io.StringIO(html.tostring(table,encoding='unicode')),flavor='lxml',keep_default_na=False)[0]
 except ValueError:continue
 cols=[str(c).lower() for c in d.columns]
 def col(term):return next((i for i,c in enumerate(cols) if term in c),None)
 fi=col('frequency');ci=col('callsign');bi=col('branding');bi=col('station name') if bi is None else bi;li=col('location')
 if fi is None or ci is None or bi is None:skipped[state]+=len(d);continue
 for vals in d.itertuples(index=False,name=None):
  def clean(v):return re.sub(r'\s+',' ',re.sub(r'\[[^]]*\]','',str(v))).strip()
  raw=clean(vals[fi]).split('(')[0];m=re.match(r'^(\d+(?:\.\d+)?)\s*(MHz|kHz)?(?:\s|$)',raw,re.I)
  if not m:continue
  value=float(m[1]);unit=(m[2] or '').lower()
  if unit=='mhz' or (not unit and 65<=value<=108):khz=round(value*1000);band='FM'
  elif unit=='khz' or (not unit and 500<=value<=1710):khz=round(value);band='AM'
  else:continue
  if not ((band=='FM' and 65000<=khz<=108000) or (band=='AM' and 500<=khz<=1710)):continue
  rows.add((state,clean(vals[li]) if li is not None else region,clean(vals[ci]),str(khz),band,clean(vals[bi])))
# The same article presents most regional/state rows as frequency-led lists.
for li in s.xpath('//div[contains(@class,"mw-parser-output")]//li[not(ancestor::table)]'):
 h=li.xpath('preceding::h2[1]');state=states.get(' '.join(''.join(h[0].itertext()).split()) if h else '')
 if not state:continue
 hs=li.xpath('preceding::h3[1]');region=' '.join(''.join(hs[0].itertext()).split()) if hs else ''
 text=' '.join(''.join(li.itertext()).split());m=re.match(r'^(\d+(?:\.\d+)?)\s*(MHz|kHz)\s*[–—-]?\s*(.+)',text,re.I)
 if not m:continue
 band='FM' if m[2].lower()=='mhz' else 'AM';khz=round(float(m[1])*(1000 if band=='FM' else 1))
 if not ((band=='FM' and 65000<=khz<=108000) or (band=='AM' and 500<=khz<=1710)):continue
 brand=re.split(r'\s+[–—]\s+',m[3])[0];brand=re.sub(r'\[\d+\]','',brand).strip()
 if any(t in brand.lower() for t in ['defunct','formerly','relay','repeater']):continue
 calls=re.findall(r'(?<![A-Za-z0-9])[1-8][A-Z]{2,4}(?![A-Za-z0-9])',brand)
 call=calls[0] if len(calls)==1 else ''
 brand=re.sub(r'\s*\([1-8][A-Z]{2,4}\)\s*',' ',brand).strip()
 rows.add((state,region,call,str(khz),band,brand))
p=root/'launcher/src/main/assets/radio';p.mkdir(parents=True,exist_ok=True)
(p/'au-stations.tsv').write_text('# Wikipedia revision 1378942509; 2026-10-19; CC BY-SA 4.0\n# state\tlocality\tcallsign\tkHz\tband\tbranding\n'+''.join('\t'.join(r)+'\n' for r in sorted(rows)))
print('stations',len(rows),collections.Counter(r[0] for r in rows),'skipped non-frequency tables',skipped)
# Keep all polygons/rings; simplify by iterative Ramer-Douglas-Peucker to ~1 km.
def simplify(points,tol=.01):
 keep={0,len(points)-1};stack=[(0,len(points)-1)]
 while stack:
  a,b=stack.pop();x,y=points[a];dx=points[b][0]-x;dy=points[b][1]-y;length=dx*dx+dy*dy
  best=tol*tol;at=None
  for i in range(a+1,b):
   px,py=points[i];t=max(0,min(1,((px-x)*dx+(py-y)*dy)/length)) if length else 0
   dist=(px-x-t*dx)**2+(py-y-t*dy)**2
   if dist>best:best=dist;at=i
  if at is not None:keep.add(at);stack.extend([(a,at),(at,b)])
 return [points[i] for i in sorted(keep)]
mapstate={'1':'NSW','2':'VIC','3':'QLD','4':'SA','5':'WA','6':'TAS','7':'NT','8':'ACT'}
out=['# ABS ASGS 2026 STE; GDA2020 -> WGS84; simplified 0.01 degrees; CC BY 4.0\n']
for f in json.load(args.boundaries_geojson.open())['features']:
 state=mapstate.get(f['properties']['STATE_CODE_2026'])
 if not state:continue
 g=f['geometry'];polys=g['coordinates'] if g['type']=='MultiPolygon' else [g['coordinates']]
 for polygon in polys:
  for ring in polygon:
   # Preserve tiny islands as original rings when simplification collapses them.
   pts=simplify(ring);
   if len(pts)<4:
    xs=[v[0] for v in ring];ys=[v[1] for v in ring]
    pts=[[min(xs),min(ys)],[max(xs),min(ys)],[max(xs),max(ys)],[min(xs),max(ys)],[min(xs),min(ys)]]
   out.append(state+'\t'+';'.join(f'{x:.4f},{y:.4f}' for x,y in pts)+'\n')
import gzip
(p/'au-state-rings.tsv.gz').write_bytes(gzip.compress(''.join(out).encode(),mtime=0));print('asset bytes',sum(f.stat().st_size for f in p.iterdir()))
