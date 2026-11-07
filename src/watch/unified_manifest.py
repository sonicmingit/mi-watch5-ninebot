"""Add the private in-app authorization UI and OMS service to port5."""
import struct
from round_resources import Pool

def add_unified(data):
    chunks=[];p=8
    while p<len(data):
        n=struct.unpack_from('<I',data,p+4)[0];chunks.append(bytearray(data[p:p+n]));p+=n
    pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1)
    pool=Pool(chunks[pi]);ns=pool.index('http://schemas.android.com/apk/res/android')
    ri=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x180)
    resources=list(struct.unpack_from('<'+'I'*((len(chunks[ri])-8)//4),chunks[ri],8))
    ids={'name':0x1010003,'exported':0x1010010,'foregroundServiceType':0x1010599}
    def start(tag,values):
        attrs=[]
        for name,value in sorted(values.items(),key=lambda x:ids[x[0]]):
            i=pool.index(name);resources.extend([0]*max(0,i+1-len(resources)));resources[i]=ids[name]
            typ,v=(18,0) if value is False else ((17,value) if isinstance(value,int) else (3,pool.index(value)))
            attrs.append(struct.pack('<IIIHBBI',ns,i,v if typ==3 else 0xffffffff,8,0,typ,v))
        return struct.pack('<HHIII',0x102,16,36+20*len(attrs),0,0xffffffff)+struct.pack('<II6H',0xffffffff,pool.index(tag),20,20,len(attrs),0,0,0)+b''.join(attrs)
    def end(tag):return struct.pack('<HHIIIII',0x103,16,24,0,0xffffffff,0xffffffff,pool.index(tag))
    activity=start('activity',{'name':'local.ninebot.bridge.ProbeActivity','exported':False})+end('activity')
    service=start('service',{'name':'local.ninebot.bridge.ProbeService','exported':False,'foregroundServiceType':1})
    service+=start('intent-filter',{})+start('action',{'name':'com.heytap.wearable.oms.SERVICE'})+end('action')+end('intent-filter')+end('service')
    append=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x103 and pool.strings[struct.unpack_from('<I',c,20)[0]]=='application')
    chunks.insert(append,activity+service)
    appstart=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x102 and pool.strings[struct.unpack_from('<I',c,20)[0]]=='application')
    perms=b''.join(start('uses-permission',{'name':s})+end('uses-permission') for s in ('android.permission.WAKE_LOCK','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_DATA_SYNC'))
    chunks.insert(appstart,perms)
    chunks[pi]=pool.encode();chunks[ri]=struct.pack('<HHI',0x180,8,8+4*len(resources))+struct.pack('<'+'I'*len(resources),*resources)
    b=bytearray(data[:8])+b''.join(chunks);struct.pack_into('<I',b,4,len(b));return b
