package net.mehvahdjukaar.courier_owls.mixins;

import net.mehvahdjukaar.courier_owls.owls.delivery.DeliverItem;
import net.mehvahdjukaar.courier_owls.owls.entities.OwlEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerMixin<T extends EntityAccess> {
    @Shadow
    @Final
    EntitySectionStorage<T> sectionStorage;

    @Inject(method = "processChunkUnload", at = @At("HEAD"))
    private void courier_owls$puffOwlsOutOfTheUnloadingChunk(long chunkPos, CallbackInfoReturnable<Boolean> cir) {
        List<OwlEntity> owls = this.sectionStorage.getExistingSectionsInChunk(chunkPos)
                .flatMap(EntitySection::getEntities)
                .filter(entity -> entity instanceof OwlEntity)
                .map(OwlEntity.class::cast)
                .toList();
        for (OwlEntity owl : owls) {
            if (owl.level() instanceof ServerLevel level) {
                DeliverItem.puffIfUnloading(level, owl);
            }
        }
    }
}
