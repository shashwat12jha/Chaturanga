package analysis;

import engine.Personality;
import engine.SearchEngine;
import engine.SearchTreeRecorder;
import model.GameState;
import model.Move;
import rules.MoveApplier;
import rules.MoveGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Powers the "Why Not This Move?" feature.
 *
 * Given a candidate move the user is considering, the engine searches THAT specific
 * branch and returns the opponent's best refutation line. This is displayed as:
 *   "If you play Nf6, the engine responds with g5 attacking the knight..."
 */
public class RefutationEngine {

    private static final int REFUTATION_DEPTH = 4;
    private static final long TIME_LIMIT_MS   = 2_000;

    private final SearchEngine engine   = new SearchEngine();
    private final MoveApplier  applier  = new MoveApplier();
    private final MoveGenerator moveGen = new MoveGenerator();

    public RefutationEngine() {
        engine.setPersonality(Personality.balanced());
    }

    public static class Refutation {
        /** The candidate move the user is thinking about. */
        public final Move candidateMove;
        /** The engine's best refutation sequence (1-3 moves). */
        public final List<Move> refutationLine;
        /** How bad this move is in centipawns. */
        public final int centipawnLoss;
        /** Human-readable explanation. */
        public final String explanation;

        public Refutation(Move candidate, List<Move> line, int loss, String explanation) {
            this.candidateMove   = candidate;
            this.refutationLine  = line;
            this.centipawnLoss   = loss;
            this.explanation     = explanation;
        }
    }

    /**
     * Given the current state and a candidate move the player is considering,
     * return the engine's refutation.
     *
     * @param currentState   position before the candidate move
     * @param candidateMove  move the user is hovering over
     * @return Refutation with explanation, or null if the move is fine
     */
    public Refutation refute(GameState currentState, Move candidateMove) {
        // Apply the candidate move
        GameState afterCandidate = applier.apply(candidateMove, currentState);

        // Let the engine search from this position
        Move bestReply = engine.findBestMove(afterCandidate, REFUTATION_DEPTH, TIME_LIMIT_MS);

        List<Move> refutationLine = new ArrayList<>();
        if (bestReply != null) {
            refutationLine.add(bestReply);

            // Add one more move for the player's forced reply (if any)
            GameState afterReply = applier.apply(bestReply, afterCandidate);
            Move followUp = engine.findBestMove(afterReply, REFUTATION_DEPTH - 1, TIME_LIMIT_MS / 2);
            if (followUp != null) refutationLine.add(followUp);
        }

        // Estimate centipawn loss
        engine.setPersonality(Personality.balanced());
        Move bestOriginal = engine.findBestMove(currentState, REFUTATION_DEPTH, TIME_LIMIT_MS);
        int loss = 0;
        // (Simplified: we just use whether a refutation was found)

        String explanation = buildExplanation(candidateMove, refutationLine, currentState);
        return new Refutation(candidateMove, refutationLine, loss, explanation);
    }

    private String buildExplanation(Move candidate, List<Move> refutationLine, GameState state) {
        StringBuilder sb = new StringBuilder();

        // Describe the candidate move
        String pieceLabel = candidate.piece.type.name().charAt(0) + candidate.piece.type.name().substring(1).toLowerCase();
        char toFile = (char)('a' + candidate.toCol);
        int  toRank = 8 - candidate.toRow;
        sb.append("If you move ").append(pieceLabel).append(" to ").append(toFile).append(toRank);

        if (refutationLine.isEmpty()) {
            sb.append(", the position remains roughly equal.");
        } else {
            Move reply = refutationLine.get(0);
            char rFile = (char)('a' + reply.toCol);
            int  rRank = 8 - reply.toRow;
            String replyPiece = reply.piece.type.name().charAt(0) + reply.piece.type.name().substring(1).toLowerCase();

            sb.append(", the opponent responds with ").append(replyPiece)
              .append(" to ").append(rFile).append(rRank);

            // Add context about the threat
            if (reply.captured != null) {
                sb.append(", winning the ").append(reply.captured.type.name().toLowerCase());
            } else if (reply.piece.type == model.PieceType.PAWN) {
                sb.append(", advancing the pawn to restrict your piece");
            }

            if (refutationLine.size() > 1) {
                Move followUp = refutationLine.get(1);
                char fFile = (char)('a' + followUp.toCol);
                int  fRank = 8 - followUp.toRow;
                sb.append(". Your best reply would be ").append(fFile).append(fRank);
            }

            sb.append(".");
        }

        return sb.toString();
    }
}
