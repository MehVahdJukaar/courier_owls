package net.mehvahdjukaar.courier_owls.owls.blocks;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.courier_owls.owls.OwlMod;
import net.mehvahdjukaar.moonlight.api.events.AfterLanguageLoadEvent;
import net.mehvahdjukaar.moonlight.api.resources.RPUtils;
import net.mehvahdjukaar.moonlight.api.resources.ResType;
import net.mehvahdjukaar.moonlight.api.resources.StaticResource;
import net.mehvahdjukaar.moonlight.api.resources.assets.LangBuilder;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicClientResourceProvider;
import net.mehvahdjukaar.moonlight.api.resources.pack.PackGenerationStrategy;
import net.mehvahdjukaar.moonlight.api.resources.pack.ResourceGenTask;
import net.mehvahdjukaar.moonlight.api.resources.pack.ResourceSink;
import net.mehvahdjukaar.moonlight.api.resources.textures.Palette;
import net.mehvahdjukaar.moonlight.api.resources.textures.Respriter;
import net.mehvahdjukaar.moonlight.api.resources.textures.TextureImage;
import net.mehvahdjukaar.moonlight.api.resources.textures.TextureOps;
import net.mehvahdjukaar.moonlight.api.set.wood.VanillaWoodTypes;
import net.mehvahdjukaar.moonlight.api.set.wood.WoodType;
import net.mehvahdjukaar.moonlight.api.util.math.colors.RGBColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class OwlBlocksClientPack extends DynamicClientResourceProvider {
    private static final String BIRD_HOUSE_OAK = "bird_house_oak";
    private static final String BIRD_HOUSE_FOLDER = "block/bird_house/";
    private static final List<String> BIRD_HOUSE_PARTS = List.of("front", "back", "side", "top", "top_2", "bottom");

    private static final String TREE_HOLLOW_OAK = "tree_hollow_oak";
    private static final String TREE_HOLLOW_FOLDER = "block/tree_hollow/";

    public OwlBlocksClientPack() {
        super(BirdMod.res("dynamic_assets"), PackGenerationStrategy.REGEN_ON_EVERY_RELOAD);
    }

    @Override
    protected Collection<String> gatherSupportedNamespaces() {
        return List.of("minecraft");
    }

    @Override
    public void regenerateDynamicAssets(Consumer<ResourceGenTask> executor) {
        executor.accept(this::generateBirdHouseModels);
        executor.accept(this::generateBirdHouseTextures);
        executor.accept(this::generateTreeHollowModels);
        executor.accept(this::generateTreeHollowTextures);
    }

    private void generateBirdHouseModels(ResourceManager manager, ResourceSink sink) {
        StaticResource blockState = StaticResource.getOrLog(manager,
                ResType.BLOCKSTATES.getPath(BirdMod.res(BIRD_HOUSE_OAK)));
        StaticResource bodyModel = StaticResource.getOrLog(manager,
                ResType.BLOCK_MODELS.getPath(BirdMod.res("bird_house/oak_body")));
        StaticResource roofModel = StaticResource.getOrLog(manager,
                ResType.BLOCK_MODELS.getPath(BirdMod.res("bird_house/oak_roof")));
        StaticResource itemModel = StaticResource.getOrLog(manager,
                ResType.ITEMS.getPath(BirdMod.res(BIRD_HOUSE_OAK)));
        if (blockState == null || bodyModel == null || roofModel == null || itemModel == null) return;

        OwlMod.BIRD_HOUSES.forEach((wood, block) -> {
            if (wood == VanillaWoodTypes.OAK) return;
            String id = wood.getVariantId(OwlMod.BIRD_HOUSE_NAME);
            String woodPath = wood.getVariantId("%s");

            Function<String, String> retarget = s ->
                    s.replace(BIRD_HOUSE_FOLDER + "oak", BIRD_HOUSE_FOLDER + woodPath);
            try {
                sink.addSimilarJsonResource(manager, blockState, retarget, s -> id + ".json");
                sink.addSimilarJsonResource(manager, bodyModel, retarget, s -> woodPath + "_body.json");
                sink.addSimilarJsonResource(manager, roofModel, retarget, s -> woodPath + "_roof.json");
                sink.addSimilarJsonResource(manager, itemModel, retarget, s -> id + ".json");
            } catch (Exception e) {
                BirdMod.LOGGER.error("Failed to generate bird house models for {}:", wood, e);
            }
        });
    }

    private void generateBirdHouseTextures(ResourceManager manager, ResourceSink sink) {
        List<TextureImage> templates = new ArrayList<>();
        try {
            for (String part : BIRD_HOUSE_PARTS) {
                templates.add(TextureImage.open(manager, BirdMod.res(BIRD_HOUSE_FOLDER + "oak_" + part)));
            }

            Palette shared = Palette.merge(templates.stream().map(Palette::fromImage).toArray(Palette[]::new));

            shared.remove(shared.getDarkest());

            List<Respriter> respriters = templates.stream()
                    .map(t -> Respriter.ofPalette(t, shared))
                    .toList();

            OwlMod.BIRD_HOUSES.forEach((wood, block) -> {
                if (wood == VanillaWoodTypes.OAK) return;
                String woodPath = wood.getVariantId("%s");
                try (TextureImage planks = TextureImage.open(manager,
                        RPUtils.findFirstBlockTextureLocation(manager, wood.planks))) {
                    Palette target = Palette.fromImage(planks);
                    for (int i = 0; i < BIRD_HOUSE_PARTS.size(); i++) {
                        Identifier res = BirdMod.res(BIRD_HOUSE_FOLDER + woodPath + "_" + BIRD_HOUSE_PARTS.get(i));
                        Respriter respriter = respriters.get(i);
                        sink.addTextureIfNotPresent(manager, res, () -> respriter.recolor(target));
                    }
                } catch (Exception e) {
                    BirdMod.LOGGER.error("Failed to generate bird house textures for {}:", wood, e);
                }
            });
        } catch (Exception e) {
            BirdMod.LOGGER.error("Could not generate any bird house texture:", e);
        } finally {
            templates.forEach(TextureImage::close);
        }
    }

    private void generateTreeHollowModels(ResourceManager manager, ResourceSink sink) {
        StaticResource blockState = StaticResource.getOrLog(manager,
                ResType.BLOCKSTATES.getPath(BirdMod.res(TREE_HOLLOW_OAK)));
        StaticResource blockModel = StaticResource.getOrLog(manager,
                ResType.BLOCK_MODELS.getPath(BirdMod.res("tree_hollow/oak")));
        StaticResource sealedModel = StaticResource.getOrLog(manager,
                ResType.BLOCK_MODELS.getPath(BirdMod.res("tree_hollow/oak_sealed")));
        StaticResource itemModel = StaticResource.getOrLog(manager,
                ResType.ITEMS.getPath(BirdMod.res(TREE_HOLLOW_OAK)));
        if (blockState == null || blockModel == null || sealedModel == null || itemModel == null) return;

        OwlMod.TREE_HOLLOWS.forEach((wood, block) -> {
            if (wood == VanillaWoodTypes.OAK) return;
            String id = wood.getVariantId(OwlMod.TREE_HOLLOW_NAME);
            String woodPath = wood.getVariantId("%s");
            try {
                String side = logTexture(manager, wood, false).toString();
                String top = logTexture(manager, wood, true).toString();

                Function<String, String> retarget = s -> s
                        .replace(TREE_HOLLOW_FOLDER + "oak", TREE_HOLLOW_FOLDER + woodPath)
                        .replace("minecraft:block/oak_log_top", top)
                        .replace("minecraft:block/oak_log", side);

                sink.addSimilarJsonResource(manager, blockState, retarget, s -> id + ".json");
                sink.addSimilarJsonResource(manager, blockModel, retarget, s -> woodPath + ".json");
                sink.addSimilarJsonResource(manager, sealedModel, retarget, s -> woodPath + "_sealed.json");
                sink.addSimilarJsonResource(manager, itemModel, retarget, s -> id + ".json");
            } catch (Exception e) {
                BirdMod.LOGGER.error("Failed to generate tree hollow models for {}:", wood, e);
            }
        });
    }

    private void generateTreeHollowTextures(ResourceManager manager, ResourceSink sink) {
        try (TextureImage hole = TextureImage.open(manager, BirdMod.res(TREE_HOLLOW_FOLDER + "hole"))) {
            RGBColor holeRim = Palette.fromImage(hole).getLightest().rgb();

            OwlMod.TREE_HOLLOWS.forEach((wood, block) -> {
                Identifier res = BirdMod.res(TREE_HOLLOW_FOLDER + wood.getVariantId("%s"));
                sink.addTextureUnlessPresent(manager, res, () -> {
                    try (TextureImage logSide = TextureImage.open(manager, logTexture(manager, wood, false))) {
                        RGBColor rim = brightestBodyShade(Palette.fromImage(logSide));
                        TextureImage front = logSide.makeCopy();
                        carveHole(front, hole, holeRim, rim);
                        return front;
                    }
                });
            });
        } catch (Exception e) {
            BirdMod.LOGGER.error("Could not generate any tree hollow texture:", e);
        }
    }

    private static void carveHole(TextureImage front, TextureImage hole, RGBColor oakRim, RGBColor rim) {
        float minChannel = 1 / 255f;
        float red = rim.red() / Math.max(minChannel, oakRim.red());
        float green = rim.green() / Math.max(minChannel, oakRim.green());
        float blue = rim.blue() / Math.max(minChannel, oakRim.blue());
        try (TextureImage tinted = matchFrameSize(hole, front)) {
            tinted.forEachPixel(p ->
                    p.setValue(new RGBColor(p.getValue()).multiply(red, green, blue, 1).toInt()));
            TextureOps.applyOverlay(front, tinted);
        }
    }

    private static TextureImage matchFrameSize(TextureImage hole, TextureImage front) {
        float w = front.frameWidth() / (float) hole.frameWidth();
        float h = front.frameHeight() / (float) hole.frameHeight();
        if (w <= 1 && h <= 1) return hole.makeCopy();
        return TextureOps.createScaled(hole, Math.max(1, w), Math.max(1, h));
    }

    private static RGBColor brightestBodyShade(Palette palette) {
        Palette body = palette.copy();
        if (body.size() > 2) body.removeLeastUsed();
        return body.getLightest().rgb();
    }

    private static Identifier logTexture(ResourceManager manager, WoodType wood, boolean top)
            throws FileNotFoundException {
        try {
            return RPUtils.findFirstBlockTextureLocation(manager, wood.log, t -> top == t.endsWith("_top"));
        } catch (FileNotFoundException e) {
            return RPUtils.findFirstBlockTextureLocation(manager, wood.log);
        }
    }

    @Override
    public void addDynamicTranslations(AfterLanguageLoadEvent lang) {
        OwlMod.BIRD_HOUSES.forEach((wood, block) ->
                LangBuilder.addDynamicEntry(lang, "block.courier_owls.bird_house", wood, block));
        OwlMod.TREE_HOLLOWS.forEach((wood, block) ->
                LangBuilder.addDynamicEntry(lang, "block.courier_owls.tree_hollow", wood, block));
    }
}
