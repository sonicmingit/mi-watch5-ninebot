"""Publish the prepared release assets using the local Git credential helper."""
from pathlib import Path
import json,subprocess,urllib.request,urllib.parse,urllib.error

repo=Path(__file__).resolve().parents[1]
tag='v0.0.9-port6'
base='https://api.github.com/repos/sonicmingit/mi-watch5-ninebot'
assets=repo/'dist/release'
names=['watch.apk','phone.apk']
for name in names:assert (assets/name).is_file(),name
cred=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,check=True)
token=next(line.split('=',1)[1] for line in cred.stdout.splitlines() if line.startswith('password='))
headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'mi-watch5-ninebot-release'}
def request(url,method='GET',data=None,content_type=None):
    h=dict(headers)
    if content_type:h['Content-Type']=content_type
    req=urllib.request.Request(url,data=data,headers=h,method=method)
    try:
        with urllib.request.urlopen(req,timeout=120) as res:
            body=res.read()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        detail=e.read().decode('utf8','replace')[:500]
        raise RuntimeError(f'GitHub HTTP {e.code}: {detail}') from None
try:
    release=request(base+'/releases/tags/'+tag)
except RuntimeError as e:
    if 'HTTP 404' not in str(e):raise
    payload={'tag_name':tag,'target_commitish':'main','name':'九号出行手表版',
             'body':(repo/'RELEASE_NOTES.md').read_text(encoding='utf8'),'draft':False,'prerelease':False}
    release=request(base+'/releases','POST',json.dumps(payload,ensure_ascii=False).encode('utf8'),'application/json')
else:
    payload={'name':'九号出行手表版','body':(repo/'RELEASE_NOTES.md').read_text(encoding='utf8')}
    release=request(base+'/releases/'+str(release['id']),'PATCH',json.dumps(payload,ensure_ascii=False).encode('utf8'),'application/json')
for old in release.get('assets',[]):
    if old['name'] not in names:
        request(base+'/releases/assets/'+str(old['id']),'DELETE')
        print('removed',old['name'])
existing={a['name'] for a in release.get('assets',[])}
upload=release['upload_url'].split('{',1)[0]
for name in names:
    if name in existing:continue
    data=(assets/name).read_bytes()
    mime='application/vnd.android.package-archive' if name.endswith('.apk') else 'text/plain'
    result=request(upload+'?'+urllib.parse.urlencode({'name':name}),'POST',data,mime)
    assert result['name']==name and result['size']==len(data)
    print('uploaded',name,len(data))
print('release',release['html_url'])
