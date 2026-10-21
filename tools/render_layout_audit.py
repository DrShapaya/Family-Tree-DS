"""Render layout audit coordinates and actual FamilyLineRouter segments."""
import json
import re
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1] / 'app/build/layout-qa'
FONT = 'C:/Windows/Fonts/arial.ttf'

def render(name, bounds=None, suffix='overview', scale=.13):
    data = json.loads((ROOT / (name + '.json')).read_text(encoding='utf-8'))
    people = data['people']
    if bounds is None:
        bounds = (min(p['x'] for p in people)-160, min(p['y'] for p in people)-100,
                  max(p['x'] for p in people)+440, max(p['y'] for p in people)+260)
    x0,y0,x1,y1 = bounds
    img = Image.new('RGB', (int((x1-x0)*scale)+1, int((y1-y0)*scale)+1), '#fafafa')
    draw = ImageDraw.Draw(img)
    def pt(x,y): return ((x-x0)*scale,(y-y0)*scale)
    for x in range(int(x0//40)*40,int(x1)+40,40):
        draw.line([pt(x,y0),pt(x,y1)],fill='#eeeeee')
    for y in range(int(y0//40)*40,int(y1)+40,40):
        draw.line([pt(x0,y),pt(x1,y)],fill='#eeeeee')
    for idx,route in enumerate(data.get('routes', [])):
        color=['#276c89','#8a5366','#438551','#a37327'][idx%4]
        for a,b,c,d in route['segments']:
            draw.line([pt(a,b),pt(c,d)], fill=color, width=max(1,int(scale*5)))
    by_id={p['id']:p for p in people}
    for link in data['links']:
        if link['type'] not in ('partner','family'): continue
        a,b=by_id[link['from']],by_id[link['to']]
        if a['y']==b['y']:
            draw.line([pt(a['x']+140,a['y']+80),pt(b['x']+140,b['y']+80)],fill='#999999')
    font = ImageFont.truetype(FONT,max(11,int(scale*70)))
    small = ImageFont.truetype(FONT,max(8,int(scale*32)))
    for p in people:
        x,y=p['x'],p['y']
        if x+280<x0 or x>x1 or y+160<y0 or y>y1:continue
        draw.rounded_rectangle([pt(x,y),pt(x+280,y+160)],radius=max(2,int(scale*14)),fill='#e4efef',outline='#456771',width=1)
        nums=re.findall(r'\b\d{1,3}\b',p['name'])
        label=nums[0] if nums else p['name'][:8]
        center=pt(x+140,y+55)
        draw.text(center,label,font=font,fill='#152c34',anchor='mm')
        if scale>=.22:
            draw.text(pt(x+140,y+115),p['name'][:22],font=small,fill='#29404a',anchor='mm')
    out=ROOT / f'{name}-{suffix}.png'
    img.save(out)
    print(out)

def focus(name, numbers, suffix, scale=.26):
    data = json.loads((ROOT / (name + '.json')).read_text(encoding='utf-8'))
    selected = [p for p in data['people'] if any(
        int(n) in numbers for n in re.findall(r'\b\d{1,3}\b', p['name']))]
    if not selected:
        raise ValueError('No people found for focus ' + suffix)
    bounds = (min(p['x'] for p in selected)-320, min(p['y'] for p in selected)-180,
              max(p['x'] for p in selected)+600, max(p['y'] for p in selected)+360)
    render(name, bounds, suffix, scale)

if __name__ == '__main__':
    render('kennedy-current', scale=.1)
    render('kennedy-reference', scale=.1)
    render('washington-current', scale=.075)
    for name in ['kennedy-current','kennedy-reference']:
        render(name,(10000,2080,28000,4650),'middle',.23)
    render('washington-current',(0,0,30000,2100),'upper',.13)
    render('washington-current',(0,2100,30000,4500),'middle',.13)
    focus('kennedy-current', {92,93,96,97,98,137,138,139,140,141,142,146,159,160}, 'shared-lineages')
    focus('kennedy-current', {107,108,109,110,111,112,113,114,148,149,154,156,157,158}, 'parent-centering')
    focus('washington-current', {382,383,429,430,431,432,433,434,341}, '383-siblings')
    focus('washington-current', {411,412,413,371,372}, '411-centering')
    render('washington-current',(28000,0,41000,3000),'right-upper',.16)
    render('washington-current',(40000,0,52500,3100),'far-right',.16)
    render('washington-current',(28000,2800,44000,6200),'right-lower',.13)
