package net.mehvahdjukaar.courier_owls.bird.navigator.direct;

import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record DirectVerdict(Kind kind, double distance, double openReach, @Nullable AABB blockedAt) {
    public enum Kind {
        NONE,

        TOO_CLOSE,
        FLOWN,

        PARTIAL,

        BLOCKED,

        PARTIAL_TOO_SHORT,

        DEMOTED,

        WRONG_ARRIVAL
    }

    public static final DirectVerdict NONE = new DirectVerdict(Kind.NONE, 0.0, 0.0, null);

    static DirectVerdict flown(double distance) {
        return new DirectVerdict(Kind.FLOWN, distance, distance, null);
    }

    static DirectVerdict partial(double distance, double openReach) {
        return new DirectVerdict(Kind.PARTIAL, distance, openReach, null);
    }

    static DirectVerdict tooClose(double distance) {
        return new DirectVerdict(Kind.TOO_CLOSE, distance, 0.0, null);
    }

    static DirectVerdict demoted() {
        return new DirectVerdict(Kind.DEMOTED, 0.0, 0.0, null);
    }

    static DirectVerdict wrongArrival(double distance) {
        return new DirectVerdict(Kind.WRONG_ARRIVAL, distance, 0.0, null);
    }

    static DirectVerdict blocked(double distance, double openReach, @Nullable AABB at) {
        return new DirectVerdict(Kind.BLOCKED, distance, openReach, at);
    }

    static DirectVerdict partialTooShort(double distance, double openReach, @Nullable AABB at) {
        return new DirectVerdict(Kind.PARTIAL_TOO_SHORT, distance, openReach, at);
    }

    public boolean isDirect() {
        return this.kind == Kind.FLOWN || this.kind == Kind.PARTIAL;
    }

    private boolean shutOnTheSpot() {
        return this.openReach <= 0.0;
    }

    public String summary(DirectFlightSettings settings) {
        return switch (this.kind) {
            case NONE -> "no flight asked for yet";
            case TOO_CLOSE -> String.format(Locale.ROOT,
                    "SEARCHED: %.0f blocks is inside the %.0f close-quarters floor (minDistance)",
                    this.distance, settings.minDistance);
            case FLOWN -> String.format(Locale.ROOT,
                    "DIRECT: straight %.0f blocks, no search run", this.distance);
            case PARTIAL -> String.format(Locale.ROOT,
                    "DIRECT: %.0f blocks of %.0f, next leg asked for from there", this.openReach, this.distance);

            case BLOCKED -> this.shutOnTheSpot()
                    ? String.format(Locale.ROOT,
                    "SEARCHED: corridor never opened - the bird itself does not fit heading that way "
                            + "(box is on the bird), over a %.0f block line", this.distance)
                    : String.format(Locale.ROOT,
                    "SEARCHED: corridor shut %.1f blocks into a %.0f block line, box drawn there",
                    this.openReach, this.distance);
            case PARTIAL_TOO_SHORT -> String.format(Locale.ROOT,
                    "SEARCHED: only %.0f blocks of corridor open, needs %.0f to be worth flying (minPartialLeg)",
                    this.openReach, settings.minPartialLeg);
            case DEMOTED ->
                    "SEARCHED: direct is demoted for this trip - its last leg got no closer, search drives until one does";
            case WRONG_ARRIVAL -> String.format(Locale.ROOT,
                    "SEARCHED: a straight %.0f block line would not end pointed the way this flight has to arrive",
                    this.distance);
        };
    }
}
