"""Append private account UI components without regenerating vendor manifest."""
import struct
from round_detail_resources import Pool

def add_account(data):
    chunks=[];p=8
    while p<len(data):
        t,h,n=struct.unpack_from('<HHI',data,p);chunks.append(bytearray(data[p:p+n]));p+=n
    pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1)
    pool=Pool(chunks[pi]);ns=pool.index('http://schemas.android.com/apk/res/android')
    ri=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x180)
    resources=list(struct.unpack_from('<'+'I'*((len(chunks[ri])-8)//4),chunks[ri],8))
    def node(tag,values):
        attrs=[]
        ids={'name':0x1010003,'exported':0x1010010,'authorities':0x1010018}
        for name,value in sorted(values.items(),key=lambda x:ids[x[0]]):
            idx=pool.index(name);resources.extend([0]*max(0,idx+1-len(resources)));resources[idx]=ids[name]
            typ,val=(18,0) if value is False else (3,pool.index(value))
            attrs.append(struct.pack('<IIIHBBI',ns,idx,val if typ==3 else 0xffffffff,8,0,typ,val))
        start=struct.pack('<HHIII',0x102,16,36+20*len(attrs),0,0xffffffff)+struct.pack('<II6H',0xffffffff,pool.index(tag),20,20,len(attrs),0,0,0)+b''.join(attrs)
        end=struct.pack('<HHIIIII',0x103,16,24,0,0xffffffff,0xffffffff,pool.index(tag))
        return start+end
    extra=node('provider',{'name':'local.ninebot.account.AccountUi','exported':False,'authorities':'com.ninebot.ninebot.account.ui'})
    extra+=node('activity',{'name':'local.ninebot.account.AccountUi$ConfirmActivity','exported':False})
    end=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x103 and pool.strings[struct.unpack_from('<I',c,20)[0]]=='application')
    chunks.insert(end,extra);chunks[pi]=pool.encode()
    chunks[ri]=struct.pack('<HHI',0x180,8,8+4*len(resources))+struct.pack('<'+'I'*len(resources),*resources)
    out=bytearray(data[:8])+b''.join(chunks);struct.pack_into('<I',out,4,len(out));return out
