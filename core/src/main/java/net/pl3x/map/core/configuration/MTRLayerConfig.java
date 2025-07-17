package net.pl3x.map.core.configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.util.Colors;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("CanBeFinal")
public final class MTRLayerConfig extends AbstractConfig {
    @Key("settings.enabled")
    @Comment("""
            Show Minecraft Transit Railway overlay on the map.""")
    public static boolean ENABLED = true;

    @Key("settings.layer.show-controls")
    @Comment("""
            Whether this layer control shows up in the layers list or not.""")
    public static boolean SHOW_CONTROLS = true;
    @Key("settings.layer.default-hidden")
    @Comment("""
            Whether the layer should be hidden (toggled off) by default.""")
    public static boolean DEFAULT_HIDDEN = false;
    @Key("settings.layer.priority")
    @Comment("""
            Priority order this layer shows up in the layers list.
            (lower values = higher in the list)""")
    public static int PRIORITY = 40;
    @Key("settings.layer.z-index")
    @Comment("""
            Z-Index order this layer shows up in the map.
            (higher values are drawn on top of lower values)""")
    public static int Z_INDEX = 800;

    @Key("settings.icons.station")
    @Comment("""
            Icon to use for stations.""")
    public static String STATION_ICON = "marker-icon";
    @Key("settings.icons.depot")
    @Comment("""
            Icon to use for depots.""")
    public static String DEPOT_ICON = "marker-icon";

    @Key("settings.routes")
    @Comment("""
            Override colors for routes by name.""")
    public static Map<@NotNull String, @NotNull Integer> ROUTE_COLORS = new LinkedHashMap<>();

    private static final MTRLayerConfig CONFIG = new MTRLayerConfig();

    public static void reload() {
        CONFIG.reload(Pl3xMap.api().getMainDir().resolve("layers/mtr.yml"), MTRLayerConfig.class);
    }

    @Override
    protected @NotNull Object addToMap(@NotNull String rawValue) {
        return Colors.fromHex(rawValue);
    }

    @Override
    protected void set(@NotNull String path, @NotNull Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, val) -> getConfig().set(path + "." + key, Colors.toHex((int) val)));
        } else {
            getConfig().set(path, value);
        }
    }
}
