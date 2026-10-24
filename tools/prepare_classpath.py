"""Rebuild classpaths from an existing, LOCAL Minecraft 26.3 Fabric installation."""
from pathlib import Path
import argparse, zipfile, re, os
p=argparse.ArgumentParser();p.add_argument('--server-dir',type=Path,required=True);args=p.parse_args()
r=Path(__file__).resolve().parents[1];server=args.server_dir.resolve();deps=r/'build/deps';deps.mkdir(parents=True,exist_ok=True)
def unpack(jar):
 with zipfile.ZipFile(jar) as z:
  for name in z.namelist():
   if name.endswith('.jar'):
    out=deps/Path(name).name
    data=z.read(name)
    if not out.exists() or out.read_bytes()!=data:out.write_bytes(data);unpack(out)
for jar in (r/'original/mods').glob('*.jar'):unpack(jar)
def natural(s):return [int(v) if v.isdigit() else v for v in re.split(r'(\d+)',s)]
libs={}
for jar in (server/'libraries').rglob('*.jar'):
 key=jar.parent.parent
 if key not in libs or natural(jar.parent.name)>natural(libs[key].parent.name):libs[key]=jar
vanilla=server/'versions/26.3/server-26.3.jar'
if not vanilla.is_file():raise SystemExit('Minecraft 26.3 server jar is required: '+str(vanilla))
cp=os.pathsep.join(map(str,[vanilla,*libs.values()]));(r/'build/runtime-classpath.txt').write_text(cp)
(r/'build/compile-classpath.txt').write_text(cp+os.pathsep+os.pathsep.join(map(str,[*sorted((r/'original/mods').glob('*.jar')),*sorted(deps.glob('*.jar'))])))
print('Prepared classpaths. Next: python3 tools/build.py')
