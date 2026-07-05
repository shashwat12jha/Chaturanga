package engine;

/**
 * A breakdown of a position's evaluation into human-understandable components.
 * All scores are in centipawns from White's perspective (positive = White better).
 */
public class EvalBreakdown {

    public final int materialScore;
    public final int mobilityScore;
    public final int kingSafetyScore;
    public final int centerControlScore;
    public final int pawnStructureScore;
    public final int total;

    public EvalBreakdown(int materialScore, int mobilityScore, int kingSafetyScore,
                         int centerControlScore, int pawnStructureScore) {
        this.materialScore    = materialScore;
        this.mobilityScore    = mobilityScore;
        this.kingSafetyScore  = kingSafetyScore;
        this.centerControlScore = centerControlScore;
        this.pawnStructureScore = pawnStructureScore;
        this.total = materialScore + mobilityScore + kingSafetyScore
                   + centerControlScore + pawnStructureScore;
    }

    /**
     * Returns a human-readable explanation of the current evaluation.
     */
    public String toExplainableString() {
        StringBuilder sb = new StringBuilder();
        String side = total > 50 ? "White" : total < -50 ? "Black" : "Neither side";

        if (Math.abs(total) < 50) {
            sb.append("Position is roughly equal. ");
        } else if (Math.abs(total) < 200) {
            sb.append(side).append(" has a slight edge. ");
        } else if (Math.abs(total) < 500) {
            sb.append(side).append(" is clearly better. ");
        } else {
            sb.append(side).append(" has a decisive advantage. ");
        }

        // Material
        if (Math.abs(materialScore) > 50) {
            String matSide = materialScore > 0 ? "White" : "Black";
            sb.append(matSide).append(" leads in material. ");
        }

        // Mobility
        if (mobilityScore > 30) {
            sb.append("White's pieces are more active. ");
        } else if (mobilityScore < -30) {
            sb.append("Black's pieces are more active. ");
        }

        // King Safety
        if (kingSafetyScore < -80) {
            sb.append("White's king looks exposed! ");
        } else if (kingSafetyScore > 80) {
            sb.append("Black's king looks exposed! ");
        }

        // Center Control
        if (centerControlScore > 30) {
            sb.append("White controls the center. ");
        } else if (centerControlScore < -30) {
            sb.append("Black controls the center. ");
        }

        // Pawn Structure
        if (pawnStructureScore > 30) {
            sb.append("White has a healthier pawn structure. ");
        } else if (pawnStructureScore < -30) {
            sb.append("Black has a healthier pawn structure. ");
        }

        return sb.toString().trim();
    }

    /** Returns total score clamped and scaled to a percentage for the eval bar (-100 to +100). */
    public double toEvalBarPercent() {
        // Use tanh-like sigmoid scaling so extreme values still fit on the bar
        double t = total / 800.0;
        double sigmoid = t / (1.0 + Math.abs(t));
        return sigmoid * 100.0; // -100 (full black) to +100 (full white)
    }

    @Override
    public String toString() {
        return String.format("Total: %+d  [Material:%+d  Mobility:%+d  King:%+d  Center:%+d  Pawns:%+d]",
                total, materialScore, mobilityScore, kingSafetyScore, centerControlScore, pawnStructureScore);
    }
}
