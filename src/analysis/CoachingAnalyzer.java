package analysis;

import engine.EvalBreakdown;
import engine.Evaluator;
import engine.Personality;
import engine.SearchEngine;
import model.GameState;
import model.Move;
import rules.MoveApplier;
import rules.MoveGenerator;

import java.util.List;

/**
 * Classifies the quality of a player's move by comparing it against the engine's best.
 *
 * Move classification (centipawn drop thresholds):
 *   Brilliant  — engine's #1 move AND a positional sacrifice
 *   Best Move  — centipawn drop = 0
 *   Excellent  — drop ≤ 10
 *   Good       — drop ≤ 30
 *   Inaccuracy — drop ≤ 100
 *   Mistake    — drop ≤ 300
 *   Blunder    — drop > 300
 */
public class CoachingAnalyzer {

    public enum Classification {
        BRILLIANT("✦ Brilliant!", "#00d1ff"),
        BEST_MOVE("★ Best Move", "#ffd700"),
        EXCELLENT("✓ Excellent", "#83d900"),
        GOOD("✓ Good",         "#83d900"),
        INACCURACY("?! Inaccuracy", "#f5a623"),
        MISTAKE("? Mistake",    "#f05a28"),
        BLUNDER("?? Blunder",  "#cc0000");

        public final String label;
        public final String color;  // hex for UI

        Classification(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    public static class MoveAnalysis {
        public final Classification classification;
        public final int centipawnDrop;
        public final Move bestMove;
        public final String explanation;

        public MoveAnalysis(Classification c, int drop, Move best, String explanation) {
            this.classification = c;
            this.centipawnDrop  = drop;
            this.bestMove       = best;
            this.explanation    = explanation;
        }
    }

    private final SearchEngine engine  = new SearchEngine();
    private final Evaluator evaluator  = new Evaluator();
    private final MoveApplier applier  = new MoveApplier();
    private final MoveGenerator moveGen = new MoveGenerator();

    // Depth for coaching analysis — slightly less than play depth for speed
    private static final int ANALYSIS_DEPTH = 5;
    private static final long TIME_LIMIT_MS = 3_000;

    public CoachingAnalyzer() {
        engine.setPersonality(Personality.balanced());
    }

    /**
     * Analyse a move that was just played.
     *
     * @param stateBefore  the GameState BEFORE the move was played
     * @param movePlayed   the move the player made
     * @return a MoveAnalysis describing the quality of the move
     */
    public MoveAnalysis analyse(GameState stateBefore, Move movePlayed) {
        // Find the engine's best move from this position
        Move bestEngineMove = engine.findBestMove(stateBefore, ANALYSIS_DEPTH, TIME_LIMIT_MS);

        // Evaluate position after the player's move
        GameState afterPlayer = applier.apply(movePlayed, stateBefore);
        EvalBreakdown evalAfterPlayer = evaluator.evaluate(afterPlayer, Personality.balanced());

        // Evaluate position after the engine's best move
        int drop = 0;
        if (bestEngineMove != null && !bestEngineMove.equals(movePlayed)) {
            GameState afterEngine = applier.apply(bestEngineMove, stateBefore);
            EvalBreakdown evalAfterEngine = evaluator.evaluate(afterEngine, Personality.balanced());

            // Centipawn drop from the side that just moved's perspective
            int playerScore = stateBefore.isWhiteToMove() ? evalAfterPlayer.total : -evalAfterPlayer.total;
            int engineScore = stateBefore.isWhiteToMove() ? evalAfterEngine.total : -evalAfterEngine.total;
            drop = Math.max(0, engineScore - playerScore);
        }

        Classification cls = classify(drop, bestEngineMove, movePlayed, stateBefore);
        String explanation = buildExplanation(cls, drop, bestEngineMove, movePlayed, evalAfterPlayer);

        return new MoveAnalysis(cls, drop, bestEngineMove, explanation);
    }

    private Classification classify(int drop, Move best, Move played, GameState state) {
        if (best != null && best.equals(played)) return Classification.BEST_MOVE;
        if (drop == 0) return Classification.BEST_MOVE;
        if (drop <= 10) return Classification.EXCELLENT;
        if (drop <= 30) return Classification.GOOD;
        if (drop <= 100) return Classification.INACCURACY;
        if (drop <= 300) return Classification.MISTAKE;

        // Check for Brilliant: player played a sacrifice (gave up material) but it's still good
        if (played.captured == null && drop <= 50) {
            // Positional sacrifice could be brilliant — stub for future positional analysis
        }

        return Classification.BLUNDER;
    }

    private String buildExplanation(Classification cls, int drop, Move best, Move played, EvalBreakdown eval) {
        StringBuilder sb = new StringBuilder();
        sb.append(cls.label).append(". ");
        if (drop > 0 && best != null) {
            char bFile = (char)('a' + best.toCol);
            int  bRank = 8 - best.toRow;
            sb.append("The engine prefers ").append(bFile).append(bRank).append(". ");
        }
        sb.append("Centipawn loss: ").append(drop).append(". ");
        sb.append(eval.toExplainableString());
        return sb.toString();
    }
}
