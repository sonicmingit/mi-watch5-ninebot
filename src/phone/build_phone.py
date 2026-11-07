"""Build the phone authorization helper from the shared bridge source."""
from pathlib import Path
import os,subprocess,zipfile,hashlib

root=Path(__file__).resolve().parents[2];src=Path(__file__).parent
bt=Path(os.environ['ANDROID_BUILD_TOOLS']);android=Path(os.environ['ANDROID_JAR']);j=Path(os.environ['JAVA_HOME'])/'bin'
key=Path(os.environ['NINEBOT_KEYSTORE']);alias=os.environ['NINEBOT_KEY_ALIAS']
assert key.is_file()
work=root/'build/phone';out=root/'dist';out.mkdir(exist_ok=True)
for n in ('classes','dex','res/drawable'):(work/n).mkdir(parents=True,exist_ok=True)
def run(*a):subprocess.run([str(x) for x in a],check=True)
sources=[src/'ConfigReceiver.java']+[root/'src/bridge'/n for n in ('DirectLink.java','ProbeActivity.java','ProbeService.java')]
run(j/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',android,'-d',work/'classes',*sources)
run(j/'jar.exe','cf',work/'classes.jar','-C',work/'classes','.')
run(j/'java.exe','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','26','--output',work/'dex',work/'classes.jar')
(work/'res/drawable/ninebot_authorization.xml').write_bytes((src/'ninebot_authorization.xml').read_bytes())
run(bt/'aapt2.exe','compile','--dir',work/'res','-o',work/'resources.zip')
run(bt/'aapt2.exe','link','-I',android,'--manifest',src/'AndroidManifest.xml','-o',work/'base.apk',work/'resources.zip')
with zipfile.ZipFile(work/'base.apk','a') as z:z.write(work/'dex/classes.dex','classes.dex')
run(bt/'zipalign.exe','-f','-p','4',work/'base.apk',work/'aligned.apk')
apk=out/'ninebot-phone-authorization-v0.5.apk'
run(j/'java.exe','-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias',alias,'--ks-pass','env:NINEBOT_KEYSTORE_PASSWORD','--key-pass','env:NINEBOT_KEY_PASSWORD','--out',apk,work/'aligned.apk')
run(j/'java.exe','-jar',bt/'lib/apksigner.jar','verify',apk);run(bt/'zipalign.exe','-c','-p','4',apk)
print(apk,hashlib.sha256(apk.read_bytes()).hexdigest())
