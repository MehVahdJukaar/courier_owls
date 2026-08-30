package net.mehvahdjukaar.courier_owls.parcel;

import net.mehvahdjukaar.courier_owls.BirdMod;
import net.mehvahdjukaar.moonlight.api.platform.network.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class SetPackageRecipientMessage implements Message {
    public static final TypeAndCodec<RegistryFriendlyByteBuf, SetPackageRecipientMessage> TYPE = Message.makeType(
            BirdMod.res("set_package_recipient"),
            SetPackageRecipientMessage::new
    );

    private final String recipient;

    public SetPackageRecipientMessage(String recipient) {
        this.recipient = recipient;
    }

    public SetPackageRecipientMessage(FriendlyByteBuf buf) {
        this.recipient = buf.readUtf(CardboardPackageMenu.NAME_LIMIT);
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(recipient, CardboardPackageMenu.NAME_LIMIT);
    }

    @Override
    public void handle(Context context) {
        if (context.getPlayer().containerMenu instanceof CardboardPackageMenu menu) {
            menu.setRecipient(recipient);
        }
    }

    @Override
    public Type<?> type() {
        return TYPE.type();
    }
}
