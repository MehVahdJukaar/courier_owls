package net.mehvahdjukaar.courier_owls.parcel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.moonlight.api.resources.ResType;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicServerResourceProvider;
import net.mehvahdjukaar.moonlight.api.resources.pack.PackGenerationStrategy;
import net.mehvahdjukaar.moonlight.api.resources.pack.ResourceGenTask;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class CardboardRecipePack extends DynamicServerResourceProvider {
    public CardboardRecipePack() {
        super(BirdMod.res("cardboard_recipe"), PackGenerationStrategy.REGEN_ON_EVERY_RELOAD);
    }

    @Override
    protected Collection<String> gatherSupportedNamespaces() {
        return List.of(BirdMod.MOD_ID);
    }

    @Override
    public void regenerateDynamicAssets(Consumer<ResourceGenTask> executor) {
        executor.accept((manager, sink) -> sink.addJson(
                BirdMod.res(ParcelMod.CARDBOARD_PACKAGE_NAME), recipe(), ResType.RECIPES));
    }

    private static JsonObject recipe() {
        JsonObject key = new JsonObject();
        key.addProperty("P", "minecraft:paper");

        JsonArray pattern = new JsonArray();
        pattern.add("PPP");
        pattern.add("P P");
        pattern.add("PPP");

        JsonObject result = new JsonObject();
        result.addProperty("id", BirdMod.res(ParcelMod.CARDBOARD_PACKAGE_NAME).toString());
        result.addProperty("count", 1);

        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.addProperty("category", "misc");
        json.add("pattern", pattern);
        json.add("key", key);
        json.add("result", result);
        return json;
    }
}
