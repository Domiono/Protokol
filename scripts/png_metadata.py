"""Recover only a PNG with bad ancillary EXIF CRC; reject damaged pixel chunks.

Operates on an in-memory copy. Original uploads are never written or repaired.
"""
import io, struct, zlib
from PIL import Image

def open_image(path):
    try:
        return Image.open(path)
    except Image.UnidentifiedImageError:
        data=path.read_bytes()
        if data[:8]!=b'\x89PNG\r\n\x1a\n': raise
        out=data[:8]; offset=8; removed=False; ended=False
        while offset+12<=len(data):
            size=struct.unpack('>I',data[offset:offset+4])[0]
            end=offset+size+12
            if end>len(data): raise ValueError('Truncated PNG chunk')
            kind=data[offset+4:offset+8]
            valid=zlib.crc32(data[offset+4:end-4])&0xffffffff==struct.unpack('>I',data[end-4:end])[0]
            if not valid:
                if kind!=b'eXIf': raise ValueError('Damaged non-EXIF PNG chunk')
                removed=True
            else:out+=data[offset:end]
            offset=end
            if kind==b'IEND':ended=True;break
        if not removed or not ended or offset!=len(data):raise ValueError('Unsupported PNG recovery')
        im=Image.open(io.BytesIO(out));im.load()
        return im
