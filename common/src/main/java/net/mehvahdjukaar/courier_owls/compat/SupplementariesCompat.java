package net.mehvahdjukaar.courier_owls.compat;

import net.mehvahdjukaar.supplementaries.common.items.components.PresentAddress;
import net.mehvahdjukaar.supplementaries.reg.ModComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class SupplementariesCompat {
    @Nullable
    public static String presentRecipient(ItemStack stack) {
        PresentAddress address = stack.get(ModComponents.ADDRESS.get());
        if (address == null || address.isPublic()) {
            return null;
        }
        String recipient = address.recipient();
        return recipient == null || recipient.isEmpty() ? null : recipient;
    }
}
