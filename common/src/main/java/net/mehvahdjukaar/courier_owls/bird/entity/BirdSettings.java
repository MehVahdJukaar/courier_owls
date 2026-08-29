package net.mehvahdjukaar.courier_owls.bird.entity;

import net.mehvahdjukaar.courier_owls.bird.controller.GaitSettings;
import net.mehvahdjukaar.courier_owls.bird.navigator.PursuitFlightSettings;
import net.mehvahdjukaar.courier_owls.bird.navigator.direct.DirectFlightSettings;
import net.mehvahdjukaar.courier_owls.bird.navigator.trim.PathTrimSettings;
import net.mehvahdjukaar.courier_owls.bird.pathfinding.PathfindingSettings;
import net.mehvahdjukaar.courier_owls.bird.envelope.EnvelopeSettings;

public record BirdSettings(EnvelopeSettings envelope, GaitSettings gait, PathfindingSettings search,
                           PursuitFlightSettings pursuit, DirectFlightSettings direct,
                           PathTrimSettings trim) {
    public static final BirdSettings DEFAULTS = new BirdSettings(new EnvelopeSettings(), new GaitSettings(),
                new PathfindingSettings(), new PursuitFlightSettings(), new DirectFlightSettings(), new PathTrimSettings());
}
