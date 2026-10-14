package dev.nostalgiacraft.monitor.compat;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Map;

public final class Integrations {
    private static final String PACKAGE = "dev.nostalgiacraft.monitor.compat.";
    private static final String ENTRY_POINT = "register";
    private static final List<Map.Entry<String, String>> COMPAT_CLASS_BY_MOD = List.of(
            Map.entry("ftbteams", "ftbteams.FtbTeamsCompat"),
            Map.entry("ae2", "ae2.Ae2Compat"),
            Map.entry("mekanism", "mekanism.MekanismCompat"),
            Map.entry("ars_nouveau", "arsnouveau.ArsNouveauCompat"),
            Map.entry("third_life_plus", "thirdlife.ThirdLifeCompat"),
            Map.entry("create", "create.CreateCompat"));

    private Integrations() {
    }

    public static void register() {
        for (Map.Entry<String, String> compat : COMPAT_CLASS_BY_MOD) {
            if (ModList.get().isLoaded(compat.getKey())) {
                load(compat.getKey(), PACKAGE + compat.getValue());
            }
        }
    }

    private static void load(String mod, String className) {
        try {
            Class.forName(className).getMethod(ENTRY_POINT).invoke(null);
        } catch (ReflectiveOperationException | LinkageError failure) {
            MonitoringCenter.LOGGER.warn("{} integration disabled: it does not match the installed version", mod, failure);
        }
    }
}
