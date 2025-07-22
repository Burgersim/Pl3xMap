/*
 * MIT License
 *
 * Copyright (c) 2020-2023 William Blake Galbreath
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.pl3x.map.core.markers.layer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.configuration.Config;
import net.pl3x.map.core.configuration.Lang;
import net.pl3x.map.core.configuration.MtrLayerConfig;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.log.Logger;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.marker.Icon;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.util.FileUtil;
import net.pl3x.map.core.world.World;
import org.jetbrains.annotations.NotNull;

/**
 * Layer that displays Minecart Transit Railway stations and routes.
 */
public class MtrLayer extends WorldLayer {
    public static final String KEY = "pl3xmap_mtr";

    private static final int DIAGONAL_THRESHOLD = 1;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String endpoint;

    private List<Marker<?>> markers = new ArrayList<>();
    private long lastFetch = 0L;

    public MtrLayer(@NotNull World world) {
        super(KEY, world, () -> Lang.UI_LAYER_MTR);

        this.endpoint = Config.WEB_ADDRESS + "/mtr/data";

        setUpdateInterval(MtrLayerConfig.UPDATE_INTERVAL);
        registerIcon("station");
        registerIcon("depot");
    }

    private void registerIcon(@NotNull String icon) {
        Path path = FileUtil.getWebDir().resolve("images/icon/" + icon + ".png");
        try {
            IconImage image = new IconImage(icon, ImageIO.read(path.toFile()), "png");
            Pl3xMap.api().getIconRegistry().register(image);
        } catch (IOException e) {
            Logger.warn("Cannot load icon " + path, e);
        }
    }

    private int worldIndex() {
        return switch (getWorld().getType()) {
            case OVERWORLD -> 0;
            case NETHER -> 1;
            case THE_END -> 2;
            default -> -1;
        };
    }

    private static int toColor(int rgb) {
        return (0xFF << 24) | (rgb & 0xFFFFFF);
    }

    private static List<Point> metroLegPoints(Point p1, Point p2) {
        int x1 = p1.x();
        int z1 = p1.z();
        int x2 = p2.x();
        int z2 = p2.z();
        int dx = x2 - x1;
        int dz = z2 - z1;
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);
        if (adx <= DIAGONAL_THRESHOLD || adz <= DIAGONAL_THRESHOLD || adx == adz) {
            return List.of(p1, p2);
        }
        int diag = Math.min(adx, adz);
        int extraX = adx - diag;
        int extraZ = adz - diag;
        int halfX = extraX / 2;
        int halfZ = extraZ / 2;
        int sx = dx > 0 ? 1 : -1;
        int sz = dz > 0 ? 1 : -1;
        Point pA;
        Point pB;
        if (adx > adz) {
            pA = Point.of(x1 + sx * halfX, z1);
            pB = Point.of(pA.x() + sx * diag, z1 + sz * diag);
        } else {
            pA = Point.of(x1, z1 + sz * halfZ);
            pB = Point.of(x1 + sx * diag, pA.z() + sz * diag);
        }
        return List.of(p1, pA, pB, p2);
    }

    private synchronized void fetch() {
        int idx = worldIndex();
        if (idx < 0) {
            return;
        }
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(this.endpoint)).build();
        try {
            HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonElement el = JsonParser.parseString(resp.body());
            if (!el.isJsonArray()) {
                return;
            }
            JsonArray arr = el.getAsJsonArray();
            if (idx >= arr.size()) {
                return;
            }
            JsonObject dim = arr.get(idx).getAsJsonObject();
            Map<String, Point> positions = new HashMap<>();
            List<Marker<?>> newMarkers = new ArrayList<>();

            if (dim.has("stations")) {
                JsonObject stations = dim.getAsJsonObject("stations");
                for (Map.Entry<String, JsonElement> e : stations.entrySet()) {
                    String id = e.getKey();
                    JsonObject obj = e.getValue().getAsJsonObject();
                    int x = obj.get("x").getAsInt();
                    int z = obj.get("z").getAsInt();
                    String name = obj.has("name") ? obj.get("name").getAsString() : "Station";
                    Point pt = Point.of(x, z);
                    positions.put(id, pt);
                    newMarkers.add(Marker.icon("station_" + id, pt, "station", 16)
                            .setOptions(Options.builder()
                                    .tooltipContent(name)
                                    .tooltipDirection(Tooltip.Direction.TOP)
                                    .build()));
                }
            }
            if (dim.has("depots")) {
                JsonObject depots = dim.getAsJsonObject("depots");
                for (Map.Entry<String, JsonElement> e : depots.entrySet()) {
                    String id = e.getKey();
                    JsonObject obj = e.getValue().getAsJsonObject();
                    int x = obj.get("x").getAsInt();
                    int z = obj.get("z").getAsInt();
                    String name = obj.has("name") ? obj.get("name").getAsString() : "Depot";
                    Point pt = Point.of(x, z);
                    positions.put(id, pt);
                    newMarkers.add(Marker.icon("depot_" + id, pt, "depot", 16)
                            .setOptions(Options.builder()
                                    .tooltipContent(name)
                                    .tooltipDirection(Tooltip.Direction.TOP)
                                    .build()));
                }
            }
            if (dim.has("routes")) {
                for (JsonElement routeEl : dim.getAsJsonArray("routes")) {
                    if (!routeEl.isJsonObject()) continue;
                    JsonObject route = routeEl.getAsJsonObject();
                    String name = route.has("name") ? route.get("name").getAsString() :
                            "route_" + route.get("routeId").getAsString();
                    int color = toColor(route.has("color") ? route.get("color").getAsInt() : 0);
                    List<String> stops = new ArrayList<>();
                    for (JsonElement s : route.getAsJsonArray("stations")) {
                        String id = s.getAsString().split("_", 2)[0];
                        if (positions.containsKey(id)) {
                            stops.add(id);
                        }
                    }
                    if (stops.size() < 2) continue;
                    List<Point> pts = new ArrayList<>();
                    for (int i = 0; i < stops.size() - 1; i++) {
                        Point a = positions.get(stops.get(i));
                        Point b = positions.get(stops.get(i + 1));
                        for (Point pt : metroLegPoints(a, b)) {
                            if (!pts.isEmpty() && pts.get(pts.size() - 1).equals(pt)) {
                                continue;
                            }
                            pts.add(pt);
                        }
                    }
                    Polyline line = Marker.polyline(name.replace(' ', '_'))
                            .addPoint(pts)
                            .setOptions(Options.builder()
                                    .strokeWeight(10)
                                    .strokeColor(color)
                                    .tooltipContent(name)
                                    .tooltipSticky(true)
                                    .tooltipDirection(Tooltip.Direction.TOP)
                                    .build());
                    newMarkers.add(line);
                }
            }
            this.markers = newMarkers;
            this.lastFetch = System.currentTimeMillis();
        } catch (Exception e) {
            Logger.warn("Failed to fetch MTR data", e);
        }
    }

    @Override
    public @NotNull List<@NotNull Marker<?>> getMarkers() {
        if (System.currentTimeMillis() - this.lastFetch > MtrLayerConfig.UPDATE_INTERVAL * 1000L) {
            fetch();
        }
        return this.markers;
    }
}
