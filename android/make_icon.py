from PIL import Image, ImageDraw, ImageFilter
S = 1024
def grad(size, top, bot):
    img = Image.new('RGB', (1, size), top)
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        d.point((0, y), tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    return img.resize((size, size))

base = grad(S, (26, 31, 44), (12, 13, 17)).convert('RGBA')
mask = Image.new('L', (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=232, fill=255)
icon = Image.new('RGBA', (S, S), (0, 0, 0, 0))
icon.paste(base, (0, 0), mask)

d = ImageDraw.Draw(icon)
# 顶部三个分类色点
for i, c in enumerate([(74, 140, 255), (255, 159, 67), (52, 211, 153)]):
    x = 392 + i * 120
    d.ellipse([x - 34, 268, x + 34, 336], fill=c)
# 对勾
lw = 92
pts = [(330, 596), (455, 722), (706, 441)]
d.line(pts, fill=(238, 244, 255), width=lw, joint='curve')
for p in (pts[0], pts[-1]):
    d.ellipse([p[0] - lw // 2, p[1] - lw // 2, p[0] + lw // 2, p[1] + lw // 2], fill=(238, 244, 255))

# 内描边，让边缘更干净
edge = Image.new('RGBA', (S, S), (0, 0, 0, 0))
ImageDraw.Draw(edge).rounded_rectangle([1, 1, S - 2, S - 2], radius=231,
                                       outline=(60, 72, 96, 120), width=6)
icon = Image.alpha_composite(icon, edge)

import os
for name, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    d2 = 'res/mipmap-' + name
    os.makedirs(d2, exist_ok=True)
    icon.resize((size, size), Image.LANCZOS).save(d2 + '/ic_launcher.png')

icon.resize((512, 512), Image.LANCZOS).save('icon-preview.png')
print('图标已生成')
