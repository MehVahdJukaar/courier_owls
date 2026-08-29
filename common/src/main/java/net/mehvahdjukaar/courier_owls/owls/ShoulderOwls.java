package net.mehvahdjukaar.courier_owls.owls;

import net.mehvahdjukaar.courier_owls.owls.entities.OwlType;
import org.jetbrains.annotations.Nullable;

public interface ShoulderOwls {
    void courier_owls$setShoulderOwl(boolean left, @Nullable OwlType type);

    @Nullable
    OwlType courier_owls$getShoulderOwl(boolean left);
}
