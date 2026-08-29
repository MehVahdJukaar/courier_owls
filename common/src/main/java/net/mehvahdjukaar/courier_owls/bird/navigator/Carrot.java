package net.mehvahdjukaar.courier_owls.bird.navigator;

import net.mehvahdjukaar.courier_owls.bird.line.LineCursor;
import net.minecraft.world.phys.Vec3;

public record Carrot(Vec3 point, double lookahead, double pullInAhead) {
    static Carrot of(LineCursor cursor, PursuitFlightSettings pursuit) {
        double along = cursor.distanceAlongArc();
        double pullIn = cursor.line().pullInAhead(along, pursuit.openAirLookahead,
                pursuit.lookaheadTaper, pursuit.enclosureCaution);
        double lookahead = pursuit.lookahead(pullIn, cursor.offRoute(), cursor.remaining());
        return new Carrot(cursor.lookaheadPoint(lookahead), lookahead, pullIn);
    }
}
