package net.mehvahdjukaar.courier_owls.owls.client.block_models;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.moonlight.api.client.model.CustomBlockModel;
import net.mehvahdjukaar.moonlight.api.client.model.CustomUnbakedModel;
import net.mehvahdjukaar.moonlight.api.client.model.ExtraModelData;
import net.mehvahdjukaar.moonlight.api.client.model.QuadEmitter;
import net.mehvahdjukaar.moonlight.api.util.math.ColorUtils;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Optional;

public class NestBlockModel implements CustomBlockModel {
    private static final int EYE_LIGHT = 15;

    private final BlockStateModel body;
    private final @Nullable BlockStateModel noAo;
    private final @Nullable BlockStateModel eyes;

    public NestBlockModel(BlockStateModel body, @Nullable BlockStateModel noAo, @Nullable BlockStateModel eyes) {
        this.body = body;
        this.noAo = noAo;
        this.eyes = eyes;
    }

    @Override
    public void emitQuads(QuadEmitter emitter, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos,
                          @Nullable BlockState state, RandomSource random, ExtraModelData data) {
        emitter.emitAll(this.body, level, pos, state, random);
        if (this.noAo != null) {
            for (BakedQuad quad : CustomBlockModel.collectQuads(this.noAo, level, pos, state, random)) {
                shadeByNormal(emitter, quad);
            }
        }
        if (this.eyes != null) {
            for (BakedQuad quad : CustomBlockModel.collectQuads(this.eyes, level, pos, state, random)) {
                emitter.fromQuad(quad)
                        .lightEmission(EYE_LIGHT)
                        .ambientOcclusion(false)
                        .forceTranslucent(true)
                        .emit();
            }
        }
    }

    private static void shadeByNormal(QuadEmitter emitter, BakedQuad quad) {
        emitter.fromQuad(quad)
                .ambientOcclusion(false)
                .shade(false)
                .color(ColorUtils.shadeColor(normalOf(quad), -1))
                .emit();
    }

    private static Vector3f normalOf(BakedQuad quad) {
        Vector3fc first = quad.position(0);
        Vector3fc second = quad.position(1);
        Vector3fc third = quad.position(2);
        Vector3f normal = third.sub(second, new Vector3f())
                .cross(first.sub(second, new Vector3f()))
                .normalize();
        if (normal.isFinite()) return normal;
        Vec3i cardinal = quad.direction().getUnitVec3i();
        return new Vector3f(cardinal.getX(), cardinal.getY(), cardinal.getZ());
    }

    @Override
    public TextureAtlasSprite getParticle(ExtraModelData data) {
        return this.body.particleMaterial().sprite();
    }

    @Override
    public @Nullable Object geometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                        RandomSource random, ExtraModelData data) {
        return this;
    }

    public record Unbaked(BlockStateModel.Unbaked model, Optional<BlockStateModel.Unbaked> noAo,
                          Optional<BlockStateModel.Unbaked> eyes) implements CustomUnbakedModel {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockStateModel.Unbaked.CODEC.fieldOf("model").forGetter(Unbaked::model),
                BlockStateModel.Unbaked.CODEC.optionalFieldOf("no_ao").forGetter(Unbaked::noAo),
                BlockStateModel.Unbaked.CODEC.optionalFieldOf("eyes").forGetter(Unbaked::eyes)
        ).apply(i, Unbaked::new));

        @Override
        public CustomBlockModel bake(ModelBaker baker) {
            return new NestBlockModel(this.model.bake(baker),
                    this.noAo.map(m -> m.bake(baker)).orElse(null),
                    this.eyes.map(m -> m.bake(baker)).orElse(null));
        }

        @Override
        public MapCodec<? extends CustomUnbakedModel> codec() {
            return CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            this.model.resolveDependencies(resolver);
            this.noAo.ifPresent(m -> m.resolveDependencies(resolver));
            this.eyes.ifPresent(m -> m.resolveDependencies(resolver));
        }
    }
}
