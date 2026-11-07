"""Preserve original view classes and IDs while insetting for the 240dp round display."""
import struct,sys
from pathlib import Path

from round_resources import Pool,dim,integer
ATTR={'paddingLeft':0x10100d6,'paddingRight':0x10100d8,'paddingTop':0x10100d7,'paddingBottom':0x10100d9,
 'layout_width':0x10100f4,'layout_height':0x10100f5,'layout_marginLeft':0x10100f7,'layout_marginRight':0x10100f9,
 'layout_marginTop':0x10100f8,'layout_marginBottom':0x10100fa,'textSize':0x1010095,'clipToPadding':0x10100eb,
 'heyShowClock':0x7f0401f3,'back_width':0x7f040043,'front_width':0x7f0401b3,
 'gravity':0x10100af,'scaleType':0x101011d,'padding':0x10100d5,
 'layout_marginStart':0x10103b5,'layout_marginEnd':0x10103b6,
 'paddingHorizontal':0x101053d,'paddingVertical':0x101053e,
 'singleLine':0x101015d,'maxLines':0x1010153,'minLines':0x1010156,'includeFontPadding':0x101015f,'ellipsize':0x10100ab,'minHeight':0x1010140}
def connection_copy(data):
 hs=struct.unpack_from('<H',data,2)[0];p=hs;chunks=[];changed=0
 while p<len(data):
  typ,_,size=struct.unpack_from('<HHI',data,p);c=data[p:p+size]
  if typ==1:
   pool=Pool(c)
   for i,s in enumerate(pool.strings):
    if s=='手机连接服务不可用\n当前版本尚未接通\n小米手机上的九号 App':
     pool.strings[i]='请启动手机授权桥接\n并打开九号 App\n授权后可独立联网使用';changed+=1
   c=pool.encode()
  chunks.append(c);p+=size
 assert changed==1
 out=bytearray(data[:hs])+b''.join(chunks);struct.pack_into('<I',out,4,len(out));return out
def edit(data,path,overrides=None):
 chunks=[];p=8
 while p<len(data):
  _,_,sz=struct.unpack_from('<HHI',data,p);chunks.append(bytearray(data[p:p+sz]));p+=sz
 pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1);pool=Pool(chunks[pi])
 ri=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x180)
 resources=list(struct.unpack_from('<'+'I'*((len(chunks[ri])-8)//4),chunks[ri],8))
 depth=0
 for k,c in enumerate(chunks):
  typ=struct.unpack_from('<H',c)[0]
  if typ==0x103:depth-=1;continue
  if typ!=0x102:continue
  tag=pool.strings[struct.unpack_from('<I',c,20)[0]];ast,asz,an=struct.unpack_from('<HHH',c,24);assert asz==20
  attrs=[bytearray(c[16+ast+i*20:16+ast+(i+1)*20]) for i in range(an)]
  values={pool.strings[struct.unpack_from('<I',a,4)[0]]:struct.unpack_from('<I',a,16)[0] for a in attrs};rid=values.get('id');v={}
  if path in ('activity_device_location.xml','activity_dynamic_show.xml','activity_statement.xml'):
   if depth==0:v={'paddingLeft':dim(24),'paddingRight':dim(24),'paddingTop':dim(24),'paddingBottom':dim(28)}
   if tag.endswith('HeyBackTitleBar'):v={'layout_width':dim(148),'layout_height':dim(32),'heyShowClock':(18,0)}
  if path=='activity_device_location.xml':
   if tag.endswith('NestedScrollView'):v={'paddingBottom':dim(44),'clipToPadding':(18,0)}
   if tag=='LinearLayout':v={'paddingBottom':dim(36)}
   if tag.endswith('ConstraintLayout') and depth>1:v={'layout_height':integer(-2)}
   if rid==0x7f0900f3:v={'layout_height':dim(112),'scaleType':integer(3)}
   if rid==0x7f090219:v={'textSize':dim(11,True),'layout_marginRight':dim(8),'layout_marginBottom':dim(8)}
   if rid==0x7f090214:v={'layout_width':integer(-1),'textSize':dim(13,True),'gravity':integer(17),'paddingTop':dim(8),'paddingBottom':dim(8)}
   if rid==0x7f090218:v={'layout_height':dim(44),'layout_marginLeft':dim(18),'layout_marginRight':dim(18),'textSize':dim(15,True)}
  if path=='activity_dynamic_show.xml':
   if tag.endswith('GifImageView'):v={'layout_width':dim(164),'layout_height':integer(0),'scaleType':integer(3)}
   if rid==0x7f090216:v={'layout_width':dim(156),'textSize':dim(14,True),'layout_marginBottom':dim(4)}
   if rid==0x7f090211:v={'textSize':dim(12,True),'layout_marginRight':dim(16),'layout_marginBottom':dim(8)}
  if path=='activity_statement.xml':
   if rid==0x7f090216:v={'layout_width':dim(160),'padding':dim(4),'textSize':dim(13,True)}
   if rid==0x7f0900f6:v={'layout_width':dim(144),'layout_height':integer(0),'scaleType':integer(3)}
  if path=='adapter_device_list.xml':
   if depth==0:v={'layout_height':dim(136),'paddingLeft':dim(12),'paddingRight':dim(12)}
   if rid==0x7f090212:v={'textSize':dim(14,True),'layout_marginTop':dim(12)}
  if path=='hey_dialog_layout.xml' and depth==0:v={'paddingLeft':dim(28),'paddingRight':dim(28),'paddingTop':dim(28),'paddingBottom':dim(28)}
  if path=='hey_toast_layout.xml':
   if depth==0:v={'layout_width':dim(160),'paddingVertical':dim(10)}
   if tag=='TextView':v={'layout_width':integer(-1),'textSize':dim(14,True),'paddingHorizontal':dim(8)}
  if path=='hey_back_textclock.xml':v={'layout_width':integer(0),'layout_height':integer(0)}
  if path=='hey_back_title_bar.xml' and rid==0x7f09018e:v={'layout_width':integer(0),'layout_height':integer(0),'layout_marginLeft':dim(0)}
  if path in ('hey_title_content_layout.xml','hey_protocol_layout.xml','hey_content_layout.xml'):
   if tag=='ScrollView':v={'paddingBottom':dim(36),'clipToPadding':(18,0)}
   if tag=='TextView':v={'textSize':dim(18 if rid==0x7f0900e0 else 14,True)}
  if path=='activity_web.xml' and depth==0:v={'paddingLeft':dim(36),'paddingRight':dim(36),'paddingTop':dim(36),'paddingBottom':dim(36)}
  if path=='hey_accept_deny_dialog.xml':
   if depth==0:v={'paddingBottom':dim(36),'clipToPadding':(18,0)}
   if tag=='TextView':v={'textSize':dim(18 if rid==0x01020016 else 14,True)}
  if path in ('activity_device_detail.xml','activity_device_list.xml'):
   if depth==0:v={'paddingLeft':dim(16),'paddingRight':dim(16),'paddingTop':dim(22),'paddingBottom':dim(8)}
   if tag.endswith(('HeyMainTitleBar','HeyBackTitleBar')):v={'layout_width':dim(148),'layout_height':dim(28),'heyShowClock':(18,0)}
  if path=='vs_device_detail.xml':
   if depth==0:v={'paddingBottom':dim(56),'clipToPadding':(18,0)}
   if depth==1:v={'layout_height':integer(-2),'paddingBottom':dim(44)}
   if rid==0x7f090113:v={'paddingTop':dim(4),'paddingBottom':dim(8)}
   if tag.endswith('ColorArcProgressBar'):v={'layout_width':dim(88),'layout_height':dim(88),'front_width':dim(6),'back_width':dim(6)}
   if tag=='TextView':v={'textSize':dim(12,True)}
   if rid in (0x7f090210,0x7f090215):v={'textSize':dim(20,True)}
   if tag=='Button':
    v={'layout_height':dim(44),'textSize':dim(15,True),'layout_marginTop':dim(4),'layout_marginBottom':dim(4)}
    if rid==0x7f090064:v.update(layout_marginLeft=dim(24),layout_marginRight=dim(2))
    elif rid==0x7f090065:v.update(layout_marginLeft=dim(2),layout_marginRight=dim(24))
    else:v.update(layout_marginLeft=dim(24),layout_marginRight=dim(24),layout_width=integer(0))
  if overrides is not None:v.update(overrides(tag,rid,depth))
  for name,(at,value) in v.items():
   ni=pool.index(name);resources.extend([0]*max(0,ni+1-len(resources)));resources[ni]=ATTR[name]
   ns=pool.index('http://schemas.android.com/apk/res-auto' if name in ['heyShowClock','back_width','front_width'] else 'http://schemas.android.com/apk/res/android')
   a=struct.pack('<IIIHBBI',ns,ni,0xffffffff,8,0,at,value)
   existing=next((i for i,x in enumerate(attrs) if struct.unpack_from('<I',x,4)[0]==ni),None)
   if existing is None:attrs.append(a)
   else:attrs[existing]=a
  if v:
   attrs.sort(key=lambda a:resources[struct.unpack_from('<I',a,4)[0]] if struct.unpack_from('<I',a,4)[0]<len(resources) else 0)
   c=bytearray(c[:16+ast])+b''.join(attrs);struct.pack_into('<I',c,4,len(c));struct.pack_into('<H',c,28,len(attrs))
   for i,name in enumerate(['id','class','style']):struct.pack_into('<H',c,30+2*i,next((j+1 for j,a in enumerate(attrs) if pool.strings[struct.unpack_from('<I',a,4)[0]]==name),0))
   chunks[k]=c
  depth+=1
 chunks[pi]=pool.encode();chunks[ri]=struct.pack('<HHI',0x180,8,8+4*len(resources))+struct.pack('<'+'I'*len(resources),*resources)
 out=bytearray(data[:8])+b''.join(chunks);struct.pack_into('<I',out,4,len(out));return out
