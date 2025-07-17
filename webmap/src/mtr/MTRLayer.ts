import * as L from "leaflet";
import {fireCustomEvent, toCenteredLatLng} from "../util/Util";
import {World} from "../world/World";
import {Point} from "../util/Point";

export default class MTRLayer extends L.LayerGroup {
    readonly key = "mtr";
    readonly label = "MTR";
    readonly priority = 0;
    readonly showControls = true;
    readonly defaultHidden = false;

    private _world: World;
    private _url: string;

    constructor(world: World, baseUrl: string) {
        super([], { attribution: undefined });
        this._world = world;
        this._url = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.load();
        fireCustomEvent("overlayadded", this as any);
    }

    private dimension(): number {
        switch (this._world.type) {
            case "nether":
                return 1;
            case "the_end":
                return 2;
            default:
                return 0;
        }
    }

    private async load(): Promise<void> {
        try {
            const response = await fetch(this._url + "data", { cache: "no-cache" });
            const json = await response.json();
            this.parse(json[this.dimension()] ?? {});
        } catch (e) {
            console.error("Failed to load MTR data", e);
        }
    }

    private parse(data: any): void {
        this.clearLayers();
        const positions = data.positions ?? {};
        const stations = data.stations ?? {};
        const routes = data.routes ?? [];

        Object.values(stations).forEach((s: any) => {
            const color = "#" + (s.color >>> 0).toString(16).padStart(6, "0");
            const marker = L.circleMarker(toCenteredLatLng(new Point(s.x, s.z)), {
                radius: 3,
                color,
                weight: 1,
                fillOpacity: 1
            });
            if (s.name) {
                marker.bindTooltip(s.name);
            }
            marker.addTo(this);
        });

        routes.forEach((r: any) => {
            const pts: L.LatLng[] = [];
            r.stations.forEach((sid: string) => {
                const pos = positions[sid];
                if (pos) {
                    pts.push(toCenteredLatLng(new Point(pos.x, pos.y)));
                }
            });
            if (pts.length >= 2) {
                const color = "#" + (r.color >>> 0).toString(16).padStart(6, "0");
                L.polyline(pts, { color, weight: 2 }).addTo(this);
            }
        });
    }

    unload(): void {
        this.clearLayers();
        fireCustomEvent("overlayremoved", this as any);
    }
}
