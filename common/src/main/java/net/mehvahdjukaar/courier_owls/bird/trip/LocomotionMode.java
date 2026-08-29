package net.mehvahdjukaar.courier_owls.bird.trip;

public enum LocomotionMode {
    AIRBORNE,
    FLUTTERING,
    PERCHED,
    LAUNCHING,
    HOVERING;

    private static final LocomotionMode[] BY_ID = values();

    public static LocomotionMode byId(byte id) {
        return BY_ID[id];
    }

    public boolean isOnFoot() {
        return this == PERCHED || this == LAUNCHING;
    }

    public boolean isHanging() {
        return this == FLUTTERING || this == HOVERING;
    }

    public boolean hasNoVanillaGravity() {
        return this == AIRBORNE || this == HOVERING;
    }

    public boolean isHeadingCommanded() {
        return this == AIRBORNE || this == LAUNCHING;
    }
}
