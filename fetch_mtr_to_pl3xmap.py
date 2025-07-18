import requests
import json
import os

# -----------------------------------------------------------------------------
# Script: fetch_mtr_to_pl3xmap_split.py
# Description:
#   Fetches MTR /data JSON and writes two Pl3xMap marker files per dimension:
#   stations and routes—placing them under a given markers directory per dimension.
# Requirements:
#   pip install requests
# Usage:
#   python fetch_mtr_to_pl3xmap_split.py --base-url https://host/mtr/data \
#       [-o /path/to/markers] -k mtr -l "MTR Transit"
# -----------------------------------------------------------------------------

DIM_NAMES = ['overworld', 'the_nether', 'the_end']
# threshold (in blocks) under which we skip the diagonal leg
DIAGONAL_THRESHOLD = 1
# default base directory for output marker packs
DEFAULT_OUTPUT_BASE = '/config/pl3xmap/markers'


def fetch_mtr_data(data_url):
    import requests
    resp = requests.get(data_url)
    resp.raise_for_status()
    return resp.json()  # list of 3 dimension dicts


def to_signed_int32(x: int) -> int:
    return x - 0x100000000 if x & 0x80000000 else x


def build_station_markers(dim):
    markers = []
    for sid, st in dim.get('stations', {}).items():
        x, z = st['x'], st['z']
        markers.append({
            "type": "icon",
            "data": {
                "key": f"station_{sid}",
                "point": {"x": x, "z": z},
                "image": "station", "anchor": {"x": 8, "z": 8}
            },
            "options": {
                "tooltip": {"content": st.get('name', 'Station'), "direction": 2}
            }
        })
    return markers

# Commented out: depots
# def build_depot_markers(dim):
#     ...


def metro_leg_points(p1, p2):
    """
    Split the hop p1->p2 into axis -> diagonal -> axis legs,
    all integer coords. Skip diagonal when short.
    """
    x1, z1 = p1
    x2, z2 = p2
    dx, dz = x2 - x1, z2 - z1
    adx, azd = abs(dx), abs(dz)
    if adx <= DIAGONAL_THRESHOLD or azd <= DIAGONAL_THRESHOLD or adx == azd:
        return [p1, p2]

    diag = min(adx, azd)
    extra_x = adx - diag
    extra_z = azd - diag
    half_x = extra_x // 2
    half_z = extra_z // 2
    sx, sz = (1 if dx > 0 else -1), (1 if dz > 0 else -1)

    if adx > azd:
        pA = (x1 + sx * half_x, z1)
        pB = (pA[0] + sx * diag,   z1 + sz * diag)
    else:
        pA = (x1,                z1 + sz * half_z)
        pB = (x1 + sx * diag,    pA[1]   + sz * diag)

    return [p1, pA, pB, p2]


def build_route_markers(dim):
    markers = []
    positions = {
        **{sid: (st['x'], st['z']) for sid, st in dim.get('stations', {}).items()},
        **{did: (dp['x'], dp['z']) for did, dp in dim.get('depots', {}).items()}
    }

    for route in dim.get('routes', []):
        rid = route.get('routeId')
        name = route.get('name') or f"route_{rid}"
        rgb = route.get('color', 0) & 0xFFFFFF
        argb = (0xFF << 24) | rgb
        color_val = to_signed_int32(argb)

        stops = [c.split('_',1)[0] for c in route.get('stations', []) if c.split('_',1)[0] in positions]
        if len(stops) < 2:
            continue

        all_pts = []
        for a, b in zip(stops, stops[1:]):
            pA, pB = positions[a], positions[b]
            segment = metro_leg_points(pA, pB)
            for pt in segment:
                if all_pts and all_pts[-1] == pt:
                    continue
                all_pts.append(pt)

        markers.append({
            "type":"line",
            "data": {"key": name.replace(' ', '_'),
                      "points":[{"x":x,"z":z} for x,z in all_pts]},
            "options": {"stroke": {"weight":10,"color":color_val},
                        "tooltip": {"content":name,"direction":2,"sticky":True}}
        })

    return markers


def write_pack_file(out_fn, pack_key, pack_label, markers):
    os.makedirs(os.path.dirname(out_fn), exist_ok=True)
    with open(out_fn, 'w', encoding='utf-8') as f:
        json.dump({
            "key": pack_key,
            "label": pack_label,
            "showControls": True,
            "defaultHidden": False,
            "markers": markers
        }, f, ensure_ascii=False, indent=2)
    print(f"Wrote {len(markers)} markers to {out_fn}")


if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser(
        description="Fetch MTR data and write Pl3xMap marker files"
    )
    p.add_argument('--base-url', required=True,
                   help="MTR /data endpoint URL")
    p.add_argument('-o', '--output-base', default=DEFAULT_OUTPUT_BASE,
                   help="Base directory for markers output")
    p.add_argument('-k', '--key', default='mtr',
                   help="Pack key prefix, e.g. 'mtr'")
    p.add_argument('-l', '--label', default='MTR Transit',
                   help="Pack label prefix, e.g. 'MTR Transit'")
    args = p.parse_args()

    base_dir = args.output_base
    dims = fetch_mtr_data(args.base_url)
    for idx, dim in enumerate(dims):
        if not (dim.get('stations') or dim.get('routes')):
            continue
        world = "minecraft-" + DIM_NAMES[idx]
        key_pref = args.key
        lbl_pref = args.label

        dir_world = os.path.join(base_dir, world)
        # stations
        st_file = os.path.join(dir_world, 'stations.json')
        st_m = build_station_markers(dim)
        write_pack_file(st_file,
                        f"{key_pref}_{world}_stations",
                        f"{lbl_pref} Stations",
                        st_m)
        # routes
        rt_file = os.path.join(dir_world, 'routes.json')
        rt_m = build_route_markers(dim)
        write_pack_file(rt_file,
                        f"{key_pref}_{world}_routes",
                        f"{lbl_pref} Routes",
                        rt_m)
