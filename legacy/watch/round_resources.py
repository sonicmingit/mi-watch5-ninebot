"""Scoped binary resource edits for Watch 5's 240dp circular screen."""
import struct

class Pool:
 def __init__(self, chunk):
  _,self.hs,_=struct.unpack_from('<HHI',chunk)
  n,self.styles,self.flags,start,style_start=struct.unpack_from('<5I',chunk,8)
  self.style_offsets=chunk[self.hs+n*4:self.hs+(n+self.styles)*4]
  self.style_data=chunk[style_start:] if style_start else b''
  self.strings=[]
  def length(p,wide=False):
   if wide:
    v=struct.unpack_from('<H',chunk,p)[0];p+=2
    if v&0x8000:v=((v&0x7fff)<<16)|struct.unpack_from('<H',chunk,p)[0];p+=2
   else:
    v=chunk[p];p+=1
    if v&128:v=((v&127)<<8)|chunk[p];p+=1
   return v,p
  for i in range(n):
   p=start+struct.unpack_from('<I',chunk,self.hs+4*i)[0]
   count,p=length(p,not self.flags&0x100)
   if self.flags&0x100:
    count,p=length(p);s=chunk[p:p+count].decode('utf8')
   else:s=chunk[p:p+count*2].decode('utf-16-le')
   self.strings.append(s)
 def index(self,text):
  if text not in self.strings:self.strings.append(text)
  return self.strings.index(text)
 def encode(self):
  def length(n,wide=False):
   if wide:return struct.pack('<H',n) if n<0x8000 else struct.pack('<HH',(n>>16)|0x8000,n&65535)
   return bytes([n]) if n<128 else bytes([(n>>8)|128,n&255])
  offsets=[];body=bytearray()
  for s in self.strings:
   offsets.append(len(body));u=s.encode('utf-16-le')
   if self.flags&0x100:
    b=s.encode('utf8');body+=length(len(u)//2)+length(len(b))+b+b'\0'
   else:body+=length(len(u)//2,True)+u+b'\0\0'
  body+=b'\0'*((-len(body))%4)
  start=self.hs+4*len(offsets)+len(self.style_offsets)
  style_start=start+len(body) if self.style_data else 0
  head=struct.pack('<HHI5I',1,self.hs,start+len(body)+len(self.style_data),len(offsets),self.styles,self.flags&~1,start,style_start)
  assert self.hs==28
  return head+struct.pack('<'+'I'*len(offsets),*offsets)+self.style_offsets+body+self.style_data

ATTRS={'paddingLeft':16842966,'paddingTop':16842967,'paddingRight':16842968,'paddingBottom':16842969,
 'text':16843087,'textSize':16842901,'textColor':16842904,'gravity':16842927,
 'layout_width':16842996,'layout_height':16842997,'layout_margin':16842998,'maxLines':16843091}
def dim(n,sp=False):return (5,(int(n)<<8)|(2 if sp else 1))
def integer(n):return (16,n&0xffffffff)

def round_main(data):
 chunks=[];p=8
 while p<len(data):
  t,hs,sz=struct.unpack_from('<HHI',data,p);chunks.append(bytearray(data[p:p+sz]));p+=sz
 pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1)
 pool=Pool(chunks[pi]);ri=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x180)
 resources=list(struct.unpack_from('<'+'I'*((len(chunks[ri])-8)//4),chunks[ri],8))
 android=pool.index('http://schemas.android.com/apk/res/android')
 def attr_index(name):
  idx=pool.index(name)
  resources.extend([0]*max(0,idx+1-len(resources)))
  resources[idx]=ATTRS[name];return idx
 def update(c,values):
  ast,asz,an=struct.unpack_from('<HHH',c,24);assert asz==20
  attrs=[bytearray(c[16+ast+i*20:16+ast+(i+1)*20]) for i in range(an)]
  for name,(typ,value) in values.items():
   idx=attr_index(name)
   a=struct.pack('<IIIHBBI',android,idx,0xffffffff,8,0,typ,value)
   matches=[i for i,x in enumerate(attrs) if struct.unpack_from('<I',x,4)[0]==idx]
   if matches:attrs[matches[0]]=a
   else:attrs.append(a)
  attrs.sort(key=lambda a:resources[struct.unpack_from('<I',a,4)[0]] if struct.unpack_from('<I',a,4)[0]<len(resources) else 0)
  result=bytearray(c[:16+ast])+b''.join(attrs)
  struct.pack_into('<I',result,4,len(result));struct.pack_into('<H',result,28,len(attrs))
  # Recompute id/class/style one-based attribute indexes after sorting.
  for k,name in enumerate(['id','class','style']):
   index=next((i+1 for i,a in enumerate(attrs) if pool.strings[struct.unpack_from('<I',a,4)[0]]==name),0)
   struct.pack_into('<H',result,30+2*k,index)
  return result
 depth=0;stack=[]
 for i,c in enumerate(chunks):
  typ=struct.unpack_from('<H',c)[0]
  if typ==0x102:
   nameidx=struct.unpack_from('<I',c,20)[0];tag=pool.strings[nameidx]
   ast,asz,an=struct.unpack_from('<HHH',c,24)
   attrs={pool.strings[struct.unpack_from('<I',c,16+ast+j*20+4)[0]]:struct.unpack_from('<I',c,16+ast+j*20+16)[0] for j in range(an)}
   rid=attrs.get('id');values={};newtag=None
   if depth==0:values={'paddingLeft':dim(24),'paddingRight':dim(24),'paddingTop':dim(20),'paddingBottom':dim(24)}
   if tag.endswith('HeyMainTitleBar'):
    newtag=pool.index('TextView');struct.pack_into('<I',c,20,newtag)
    values={'text':(1,0x7f10001b),'textSize':dim(16,True),'textColor':(28,0xffffffff),'gravity':integer(17),'layout_height':dim(28)}
   if rid==0x7f0900ce:values={'layout_width':dim(120),'layout_height':dim(120)}
   if rid==0x7f090216:values={'layout_width':dim(172),'layout_margin':dim(0),'textSize':dim(14,True),'gravity':integer(17),'maxLines':integer(5)}
   if rid==0x7f09007f:values={'paddingBottom':dim(20)}
   if rid==0x7f090213:values={'layout_width':dim(168),'gravity':integer(17),'textSize':dim(13,True),'maxLines':integer(4)}
   if rid==0x7f090062:values={'layout_height':dim(48)}
   if values:c=update(c,values)
   chunks[i]=c;stack.append(newtag);depth+=1
  elif typ==0x103:
   newtag=stack.pop();depth-=1
   if newtag is not None:struct.pack_into('<I',c,20,newtag)
 chunks[pi]=pool.encode()
 chunks[ri]=struct.pack('<HHI',0x180,8,8+4*len(resources))+struct.pack('<'+'I'*len(resources),*resources)
 out=bytearray(data[:8])+b''.join(chunks);struct.pack_into('<I',out,4,len(out));return out

def fix_connection_copy(data):
 # Only replace the exact existing Chinese connection prompt, not auth/state logic.
 p=struct.unpack_from('<H',data,2)[0];chunks=[];changed=[]
 while p<len(data):
  t,hs,sz=struct.unpack_from('<HHI',data,p);chunk=data[p:p+sz]
  if t==1:
   pool=Pool(chunk)
   for i,s in enumerate(pool.strings):
    if '请将您的Watch靠近手机' in s:
     changed.append(s);pool.strings[i]='手机连接服务不可用\n当前版本尚未接通\n小米手机上的九号 App'
   chunk=pool.encode()
  chunks.append(chunk);p+=sz
 assert len(changed)==1,changed
 hs=struct.unpack_from('<H',data,2)[0];out=bytearray(data[:hs])+b''.join(chunks);struct.pack_into('<I',out,4,len(out));return out
