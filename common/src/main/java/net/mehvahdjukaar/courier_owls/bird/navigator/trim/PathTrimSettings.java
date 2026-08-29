package net.mehvahdjukaar.courier_owls.bird.navigator.trim;

public class PathTrimSettings {
    public double openAirEnclosure = 0.1;

    public int minChordInterior = 5;

    public int bufferNodes = 2;

    public int goalRailNodes = 3;

    public double splitMinSpan = 30.0;

    public double maxChordDetour = 1.5;

    public double maxClimbDetour = 1.2;

    public double minChordDetour = 1.05;

    public double maxDescentWinding = 60.0;
}
