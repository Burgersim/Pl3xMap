package net.pl3x.map.core.markers.layer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.configuration.MTRLayerConfig;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.log.Logger;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.marker.Icon;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Stroke;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.util.Colors;
import net.pl3x.map.core.util.FileUtil;
import net.pl3x.map.core.world.World;
import org.jetbrains.annotations.NotNull;

/**
 * Display Minecraft Transit Railway routes and stations.
 */
public class MTRLayer extends WorldLayer {
    public static final String KEY = "pl3xmap_mtr";

    public static boolean hasData(@NotNull World world) {
        Path file = FileUtil.getWebDir().resolve("mtr/data");
        if (Files.exists(file)) {
            return true;
        }
        Path worldDir = world.getRegionDirectory().getParent();
        file = worldDir.resolve("mtr/data");
        return Files.exists(file);
    }

    private final String stationIcon;
    private final String depotIcon;

    private final Map<String, JsonObject> positions = new HashMap<>();
    private final Map<String, JsonObject> stations = new HashMap<>();
    private final Map<String, JsonObject> depots = new HashMap<>();
    private final List<JsonObject> routes = new ArrayList<>();

    public MTRLayer(@NotNull World world) {
        super(KEY, world, () -> "MTR");

        this.stationIcon = MTRLayerConfig.STATION_ICON;
        this.depotIcon = MTRLayerConfig.DEPOT_ICON;

        setShowControls(MTRLayerConfig.SHOW_CONTROLS);
        setDefaultHidden(MTRLayerConfig.DEFAULT_HIDDEN);
        setPriority(MTRLayerConfig.PRIORITY);
        setZIndex(MTRLayerConfig.Z_INDEX);

        registerIcon(this.stationIcon);
        registerIcon(this.depotIcon);

        loadData();
    }

    private void registerIcon(@NotNull String name) {
        try {
            Path icon = FileUtil.getWebDir().resolve("images/icon/" + name + ".png");
            IconImage image = new IconImage(name, ImageIO.read(icon.toFile()), "png");
            Pl3xMap.api().getIconRegistry().register(image);
        } catch (IOException e) {
            Logger.warn("Could not register icon " + name, e);
        }
    }

    private int dimension() {
        return switch (getWorld().getType()) {
            case NETHER -> 1;
            case THE_END -> 2;
            default -> 0;
        };
    }

    private void loadData() {
        Path file = FileUtil.getWebDir().resolve("mtr/data");
        if (!Files.exists(file)) {
            Path worldDir = getWorld().getRegionDirectory().getParent();
            file = worldDir.resolve("mtr/data");
        }
        if (!Files.exists(file)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            if (array.size() <= dimension()) {
                return;
            }
            JsonObject obj = array.get(dimension()).getAsJsonObject();
            JsonObject pos = obj.getAsJsonObject("positions");
            if (pos != null) {
                pos.entrySet().forEach(e -> positions.put(e.getKey(), e.getValue().getAsJsonObject()));
            }
            JsonObject sta = obj.getAsJsonObject("stations");
            if (sta != null) {
                sta.entrySet().forEach(e -> stations.put(e.getKey(), e.getValue().getAsJsonObject()));
            }
            JsonObject dep = obj.getAsJsonObject("depots");
            if (dep != null) {
                dep.entrySet().forEach(e -> depots.put(e.getKey(), e.getValue().getAsJsonObject()));
            }
            JsonArray routesArr = obj.getAsJsonArray("routes");
            if (routesArr != null) {
                routesArr.forEach(el -> routes.add(el.getAsJsonObject()));
            }
        } catch (IOException e) {
            Logger.warn("Failed to read MTR data", e);
        } catch (Exception e) {
            Logger.warn("Failed to parse MTR data", e);
        }
    }

    @Override
    public @NotNull Collection<@NotNull Marker<?>> getMarkers() {
        if (positions.isEmpty() && stations.isEmpty() && depots.isEmpty() && routes.isEmpty()) {
            return Collections.emptyList();
        }

        List<Marker<?>> markers = new ArrayList<>();

        // station markers
        stations.forEach((id, obj) -> {
            int x = obj.get("x").getAsInt();
            int z = obj.get("z").getAsInt();
            String name = obj.has("name") ? obj.get("name").getAsString() : "";
            Icon icon = Marker.icon("station_" + id, Point.of(x, z), this.stationIcon, 16);
            if (!name.isBlank()) {
                icon.setOptions(Options.builder()
                        .tooltipContent(name)
                        .tooltipDirection(Tooltip.Direction.TOP)
                        .build());
            }
            markers.add(icon);
        });

        // depot markers
        depots.forEach((id, obj) -> {
            int x = obj.get("x").getAsInt();
            int z = obj.get("z").getAsInt();
            String name = obj.has("name") ? obj.get("name").getAsString() : "";
            Icon icon = Marker.icon("depot_" + id, Point.of(x, z), this.depotIcon, 16);
            if (!name.isBlank()) {
                icon.setOptions(Options.builder()
                        .tooltipContent(name)
                        .tooltipDirection(Tooltip.Direction.TOP)
                        .build());
            }
            markers.add(icon);
        });

        // route lines
        for (JsonObject route : routes) {
            JsonArray st = route.getAsJsonArray("stations");
            if (st == null || st.size() < 2) {
                continue;
            }
            String name = route.has("name") ? route.get("name").getAsString() : "route";
            int color = route.has("color") ? route.get("color").getAsInt() : 0xFFFFFF;
            Integer override = MTRLayerConfig.ROUTE_COLORS.get(name);
            if (override != null) {
                color = override;
            }
            Polyline line = Marker.polyline("route_" + name);
            for (JsonElement el : st) {
                String sid = el.getAsString();
                JsonObject pos = positions.get(sid);
                if (pos != null) {
                    line.addPoint(Point.of(pos.get("x").getAsInt(), pos.get("y").getAsInt()));
                } else {
                    int split = sid.indexOf('_');
                    if (split > 0) {
                        sid = sid.substring(0, split);
                    }
                    JsonObject station = stations.get(sid);
                    if (station != null) {
                        line.addPoint(Point.of(station.get("x").getAsInt(), station.get("z").getAsInt()));
                    }
                }
            }
            line.setOptions(Options.builder()
                    .stroke(new Stroke(2, color))
                    .build());
            markers.add(line);
        }

        return markers;
    }
}

