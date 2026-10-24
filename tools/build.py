from pathlib import Path
import subprocess, shutil, zipfile, hashlib
root = Path(__file__).resolve().parents[1]
out = root/'build/classes'
if out.exists(): shutil.rmtree(out)
out.mkdir(parents=True)
result=subprocess.run(['javac','--release','25','-encoding','UTF-8','-proc:none','-cp',(root/'build/compile-classpath.txt').read_text().strip(),'-d',str(out),*map(str,sorted((root/'src/main/java').rglob('*.java')))])
if result.returncode: raise SystemExit(result.returncode)
jar = root/'release/neodymium2-2.0.0-rc.7+26.3.jar'
with zipfile.ZipFile(jar,'w',compression=zipfile.ZIP_DEFLATED) as z:
 for directory in (out,root/'src/main/resources'):
  for f in sorted(directory.rglob('*')):
   if f.is_file():
    info=zipfile.ZipInfo(f.relative_to(directory).as_posix(),(2026,10,8,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
    z.writestr(info,f.read_bytes())
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
(root/'release/SHA256SUMS').write_text(digest+'  '+jar.name+'\n')
print(jar,digest)
