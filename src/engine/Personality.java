package engine;

/**
 * Defines a named AI personality that influences evaluation weights.
 * Each personality produces different playing styles.
 */
public interface Personality {

    String getName();

    // ---- Evaluation weight multipliers (base = 1.0) ----

    /** Weight applied to material score. */
    double materialWeight();

    /** Weight applied to mobility (number of available moves). */
    double mobilityWeight();

    /** Weight applied to king safety heuristic. */
    double kingSafetyWeight();

    /** Weight applied to center control bonus. */
    double centerControlWeight();

    /** Weight applied to pawn structure score. */
    double pawnStructureWeight();

    /** Optional: flat random noise in centipawns added to leaf evaluations (0 = deterministic). */
    int evaluationNoise();

    // ---- Factory methods ----

    static Personality balanced() {
        return new Personality() {
            public String getName()             { return "Balanced"; }
            public double materialWeight()      { return 1.0; }
            public double mobilityWeight()      { return 1.0; }
            public double kingSafetyWeight()    { return 1.0; }
            public double centerControlWeight() { return 1.0; }
            public double pawnStructureWeight() { return 1.0; }
            public int evaluationNoise()        { return 0; }
        };
    }

    static Personality aggressive() {
        return new Personality() {
            public String getName()             { return "Aggressive"; }
            public double materialWeight()      { return 0.9; }
            public double mobilityWeight()      { return 1.4; }
            public double kingSafetyWeight()    { return 0.6; }  // less care for own king
            public double centerControlWeight() { return 1.3; }
            public double pawnStructureWeight() { return 0.7; }
            public int evaluationNoise()        { return 15; }
        };
    }

    static Personality positional() {
        return new Personality() {
            public String getName()             { return "Positional"; }
            public double materialWeight()      { return 1.0; }
            public double mobilityWeight()      { return 0.8; }
            public double kingSafetyWeight()    { return 1.3; }
            public double centerControlWeight() { return 0.9; }
            public double pawnStructureWeight() { return 1.6; }
            public int evaluationNoise()        { return 0; }
        };
    }

    static Personality chaotic() {
        return new Personality() {
            public String getName()             { return "Chaotic"; }
            public double materialWeight()      { return 0.8; }
            public double mobilityWeight()      { return 1.0; }
            public double kingSafetyWeight()    { return 0.5; }
            public double centerControlWeight() { return 0.8; }
            public double pawnStructureWeight() { return 0.5; }
            public int evaluationNoise()        { return 80; }  // large random variance
        };
    }
}
