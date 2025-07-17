import requests
import json
import os

# -----------------------------------------------------------------------------
# Script: fetch_mtr_to_pl3xmap.py
# Description:
#   Fetches MTR /data JSON and writes one Pl3xMap markers file per dimension,
#   with the pack key suffixed by the dimension name.
# Requirements:
#   pip install requests
# Usage:
#   python fetch_mtr_to_pl3xmap.py --base-url https://host/mtr/data -o mtr_markers.json -k mtr -l "MTR Transit"
# -----------------------------------------------------------------------------

DIM_NAMES = ['overworld', 'the_nether', 'the_end']

def fetch_mtr_data(data_url):
    resp = requests.get(data_url)
    resp.raise_for_status()
    return resp.json()  # list of 3 dimension dicts

def to_signed_int32(x: int) -> int:
    return x - 0x100000000 if x & 0x80000000 else x

def convert_to_pl3xmap(dim, pack_key, pack_label):
    markers = []
    station_positions = {}

    # stations
    for sid, st in dim.get('stations', {}).items():
        x, z = st['x'], st['z']
        station_positions[sid] = (x, z)
        markers.append({
            "type":"icon","data":{
                "key":f"station_{sid}",
                "point":{"x":x,"z":z},
                "image":"station","anchor":{"x":8,"z":8}
            },"options":{
                "tooltip":{"content":st.get('name','Station'),"direction":2}
            }
        })

    # depots
    for did, dp in dim.get('depots', {}).items():
        x, z = dp['x'], dp['z']
        station_positions[did] = (x, z)
        markers.append({
            "type":"icon","data":{
                "key":f"depot_{did}",
                "point":{"x":x,"z":z},
                "image":"depot","anchor":{"x":8,"z":8}
            },"options":{
                "tooltip":{"content":dp.get('name','Depot'),"direction":2}
            }
        })

    # straight lines
    for route in dim.get('routes', []):
        rid = route.get('routeId')
        name = route.get('name') or f"route_{rid}"
        rgb = route.get('color', 0) & 0xFFFFFF
        argb = (0xFF << 24) | rgb
        color_val = to_signed_int32(argb)

        pts = []
        for comp in route.get('stations', []):
            sid = comp.split('_', 1)[0]
            if sid in station_positions:
                pts.append(station_positions[sid])
        if len(pts) < 2:
            continue

        markers.append({
            "type":"line","data":{
                "key":f"{pack_key}_{name.replace(' ','_')}",
                "points":[{"x":x,"z":z} for x,z in pts]
            },"options":{
                "stroke":{"weight":10,"color":color_val},
                "tooltip":{"content":name,"direction":2, "sticky": True}
            }
        })

    return {
        "key": pack_key,
        "label": pack_label,
        "showControls": True,
        "defaultHidden": False,
        "markers": markers
    }

if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser(
        description="Fetch MTR /data JSON and write one Pl3xMap markers file per dimension"
    )
    p.add_argument('--base-url', required=True,
        help="URL to the MTR /data endpoint, e.g. https://host/mtr/data")
    p.add_argument('-o','--output', default='mtr_markers.json',
        help="Base output filename; will become <base>_<dimension>.json")
    p.add_argument('-k','--key', default='mtr',
        help="Pl3xMap markers pack key (will be suffixed per dimension)")
    p.add_argument('-l','--label', default='MTR Transit',
        help="Pl3xMap markers pack label")
    args = p.parse_args()

    dims = fetch_mtr_data(args.base_url)
    base, ext = os.path.splitext(args.output)
    for idx, dim in enumerate(dims):
        # skip if empty
        if not dim.get('stations') and not dim.get('routes'):
            continue
        world = DIM_NAMES[idx]
        pack_key_dim = f"{args.key}_{world}"
        pack = convert_to_pl3xmap(dim, pack_key_dim, args.label)
        out_fn = f"{base}_{world}{ext}"
        with open(out_fn, 'w', encoding='utf-8') as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)
        print(f"Wrote {len(pack['markers'])} markers to {out_fn}")
