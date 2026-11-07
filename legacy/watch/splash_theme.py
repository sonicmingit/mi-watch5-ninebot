"""Give the Android 12+ launch transition its original icon, independent of launcher."""
import struct

def restore_system_splash(data):
    changed=[]
    def visit(c):
        typ,hs,size=struct.unpack_from('<HHI',c)
        if typ in (2,0x200):
            parts=[];p=hs
            while p<size:
                n=struct.unpack_from('<I',c,p+4)[0];parts.append(visit(c[p:p+n]));p+=n
            out=bytearray(c[:hs])+b''.join(parts);struct.pack_into('<I',out,4,len(out));return out
        if typ!=0x201 or c[8]!=0x11:return c
        assert c[9]==0, 'Only dense resource offsets are supported'
        count,start=struct.unpack_from('<II',c,12)
        if count<=0x218:return c
        off=struct.unpack_from('<I',c,hs+4*0x218)[0]
        if off==0xffffffff:return c
        pos=start+off;entry_size,flags=struct.unpack_from('<HH',c,pos)
        assert entry_size==16 and flags&1
        maps=struct.unpack_from('<I',c,pos+12)[0];end=pos+16+12*maps
        values=[(0x101062c,0x1c,0xff000000),(0x101062d,1,0x7f0d0000),(0x1010630,0x1c,0x00000000)]
        extra=b''.join(struct.pack('<IHBBI',name,8,0,t,v) for name,t,v in values)
        out=bytearray(c[:end])+extra+c[end:]
        struct.pack_into('<I',out,pos+12,maps+len(values))
        for i in range(count):
            x=struct.unpack_from('<I',out,hs+4*i)[0]
            if x!=0xffffffff and x>off:struct.pack_into('<I',out,hs+4*i,x+len(extra))
        struct.pack_into('<I',out,4,len(out));changed.append(True);return out
    result=visit(data);assert len(changed)==1;return result
