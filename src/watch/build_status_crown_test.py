"""Build the local status/crown experiment unsigned; never signs or changes release outputs."""
from pathlib import Path
import subprocess,zipfile,hashlib,struct,zlib,json,datetime,os,argparse,uuid,sys
sys.dont_write_bytecode=True
from round_resources import Pool
from unified_manifest import add_unified

root=Path(__file__).resolve().parents[2];src=Path(__file__).parent
workspace=root.parent
parser=argparse.ArgumentParser()
parser.add_argument('--output-dir',type=Path,default=workspace/'download/test/ninebot')
parser.add_argument('--log-dir',type=Path,default=workspace/'download/logs/ninebot')
args=parser.parse_args()
out=args.output_dir.resolve();logs=args.log_dir.resolve()
# Keep every test artifact inside its designated workspace tree.
assert out.is_relative_to((workspace/'download/test/ninebot').resolve())
assert logs.is_relative_to((workspace/'download/logs/ninebot').resolve())
out.mkdir(parents=True,exist_ok=True);logs.mkdir(parents=True,exist_ok=True)
stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]
work=out/('status-crown-'+stamp);work.mkdir()
out=work
base=root/'inputs/ninebot-port5.apk'
assert hashlib.sha256(base.read_bytes()).hexdigest()=='0d396a2c279f669ee876043a9debf987e2159be93207adb50fb1b10ddfb1f169'
bt=Path(os.environ['ANDROID_BUILD_TOOLS']);android=Path(os.environ['ANDROID_JAR']);j=Path(os.environ['JAVA_HOME'])/'bin'
assert base.is_file()
def run(*args):subprocess.run([str(x) for x in args],check=True)
for n in ['classes','dex']:(work/n).mkdir(exist_ok=True)
sources=[src/n for n in ('AccountUi.java','StatusFormat.java','CrownScroll.java','VehicleStatusUi.java','ReadOnlyStatusRaw.java')]+[root/'src/bridge'/n for n in ('DirectLink.java','ProbeActivity.java','ProbeService.java')]
run(j/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',android,'-d',work/'classes',*sources)
run(j/'jar.exe','cf',work/'classes.jar','-C',work/'classes','.')
run(j/'java.exe','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','26','--output',work/'dex',work/'classes.jar')
def version(b):
    b=bytearray(b);p=8
    while p<len(b):
        typ,hs,n=struct.unpack_from('<HHI',b,p)
        if typ==1:
            pool=Pool(b[p:p+n]);pool.strings=['0.0.9-test90011-status-crown' if x=='0.0.9-port5' else x for x in pool.strings]
            c=pool.encode();b[p:p+n]=c;n=len(c);struct.pack_into('<I',b,4,len(b))
        if typ==0x102:
            ast,asz,an=struct.unpack_from('<HHH',b,p+24)
            for i in range(an):
                a=p+16+ast+i*asz
                if b[a+15]==16 and struct.unpack_from('<I',b,a+16)[0]==90004:struct.pack_into('<I',b,a+16,90011)
        p+=n
    return b
def patch_dex(raw):
    b=bytearray(raw);inner=0xc834;size=struct.unpack_from('<I',b,inner+0x20)[0]
    old=b'com.heytap.wearable.oms.service';new=b'com.ninebot.ninebot';p=b.find(old,inner,inner+size)
    assert p>inner and b.find(old,p+1,inner+size)==-1 and b[p-1]==len(old)
    b[p-1]=len(new);b[p:p+len(old)+1]=new+b'\0'+bytes(len(old)-len(new))
    b[inner+12:inner+32]=hashlib.sha1(b[inner+32:inner+size]).digest()
    struct.pack_into('<I',b,inner+8,zlib.adler32(b[inner+12:inner+size])&0xffffffff)
    b[12:32]=hashlib.sha1(b[32:]).digest();struct.pack_into('<I',b,8,zlib.adler32(b[12:])&0xffffffff)
    return b
with zipfile.ZipFile(base) as z,zipfile.ZipFile(out/'port6-unified-unsigned.apk','w') as dst:
    for info in z.infolist():
        if info.filename.startswith('META-INF/'):continue
        b=z.read(info.filename)
        if info.filename=='AndroidManifest.xml':b=add_unified(version(b))
        if info.filename=='classes.dex':b=patch_dex(b)
        if info.filename=='classes2.dex':b=(work/'dex/classes.dex').read_bytes()
        dst.writestr(info,b)
run(bt/'zipalign.exe','-f','-p','4',out/'port6-unified-unsigned.apk',out/'port6-unified-aligned.apk')
apk=out/'port6-unified-aligned.apk'
run(bt/'zipalign.exe','-c','-p','4',apk)
record={'version':'0.0.9-test90011-status-crown','versionCode':90011,'unsignedAlignedApk':str(apk),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'signed':False,'releaseUntouched':True}
log=logs/(stamp+'-status-crown-build.json')
with log.open('x',encoding='utf8') as f:json.dump(record,f,indent=2)
print(json.dumps(record))
