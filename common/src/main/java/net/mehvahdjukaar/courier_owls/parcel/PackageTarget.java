package net.mehvahdjukaar.courier_owls.parcel;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

public interface PackageTarget {
    Container contents();

    String recipient();

    void setRecipient(String name);

    boolean stillValid(Player player);
}
