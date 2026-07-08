package engine;

import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Opening book covering the top openings up to move 15.
 *
 * Positions are keyed by FEN (without half/full move counters) and map to
 * the best reply move in UCI long-algebraic notation ("e2e4", "g1f3", etc.).
 * All lines hand-curated for Chaturanga — no external engine data used.
 *
 * Coverage:
 *  - Ruy Lopez (Berlin, Morphy, Classical)
 *  - Sicilian (Najdorf, Dragon, Classical)
 *  - Queen's Gambit (Accepted, Declined, Slav)
 *  - King's Indian Defense
 *  - French Defense (Advance, Tarrasch)
 *  - Caro-Kann
 *  - Italian Game / Giuoco Piano
 *  - English Opening
 */
public class OpeningBook {

    // Key: "colorToMove_piece64layout" — simplified FEN fingerprint
    // Value: UCI move, e.g. "e2e4"
    private final Map<String, String[]> book = new HashMap<>();

    private static final OpeningBook INSTANCE = new OpeningBook();
    public static OpeningBook get() { return INSTANCE; }

    private OpeningBook() {
        build();
    }

    /**
     * Given the current position FEN fingerprint, returns a weighted random book move
     * or null if no book line exists.
     */
    public String lookup(String fenFingerprint) {
        String[] moves = book.get(fenFingerprint);
        if (moves == null || moves.length == 0) return null;
        // Pick a weighted random among the candidates
        return moves[new java.util.Random().nextInt(moves.length)];
    }

    /**
     * Loads a PGN file and adds all moves up to depth 15 to the opening book.
     */
    public void loadPGN(File file) throws java.io.IOException {
        String content = Files.readString(file.toPath());
        
        // PGN files can contain multiple games separated by blank lines or headers.
        // A simple split by "[Event" works if standard PGN.
        String[] games = content.split("(?=\\[Event)");
        
        for (String gamePgn : games) {
            if (gamePgn.trim().isEmpty()) continue;
            
            try {
                model.GameState state = io.FENParser.fromFEN(io.FENParser.STARTING_FEN);
                
                List<model.Move> moves = io.PGNParser.parsePGN(gamePgn, state);
                
                // Add first 15 moves to the book
                int maxDepth = Math.min(moves.size(), 30); // 30 plies = 15 full moves
                for (int i = 0; i < maxDepth; i++) {
                    model.Move m = moves.get(i);
                    String fen = io.FENParser.toFEN(state);
                    // Extract just the active color and pieces
                    String[] parts = fen.split(" ");
                    String fingerprint = parts[1] + "/" + parts[0];
                    
                    String uci = "" + (char)('a' + m.fromCol) + (8 - m.fromRow) + 
                                 (char)('a' + m.toCol) + (8 - m.toRow);
                    if (m.promotionPiece != null) {
                        uci += Character.toLowerCase(m.promotionPiece.getFenChar(true));
                    }
                    
                    // Add to book (appending to existing moves if present)
                    String[] existing = book.get(fingerprint);
                    if (existing == null) {
                        book.put(fingerprint, new String[]{uci});
                    } else {
                        // Check if already contains
                        boolean found = false;
                        for (String e : existing) if (e.equals(uci)) found = true;
                        if (!found) {
                            String[] newMoves = new String[existing.length + 1];
                            System.arraycopy(existing, 0, newMoves, 0, existing.length);
                            newMoves[existing.length] = uci;
                            book.put(fingerprint, newMoves);
                        }
                    }
                    
                    // Apply move for next iteration
                    state = new rules.MoveApplier().apply(m, state);
                }
            } catch (Exception e) {
                System.err.println("Failed to parse a game from PGN book: " + e.getMessage());
            }
        }
    }

    /** Checks whether a line is in the book. */
    public boolean inBook(String fenFingerprint) {
        return book.containsKey(fenFingerprint);
    }

    // ---- Book entries ----
    // Format: put(FEN_FINGERPRINT, CANDIDATE_MOVES...)
    // FEN fingerprint = active-color + "/" + pieces portion of FEN (no clocks)

    private void build() {
        // ---- Starting position ----
        // White's first moves
        add("w/rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
            "e2e4", "d2d4", "g1f3", "c2c4");

        // ---- After 1.e4 ----
        add("b/rnbqkbnr/pppppppp/8/4P3/8/PPPP1PPP/RNBQKBNR",
            "e7e5", "c7c5", "e7e6", "c7c6", "d7d5");

        // ---- Ruy Lopez: 1.e4 e5 2.Nf3 Nc6 3.Bb5 ----
        add("b/rnbqkbnr/pppp1ppp/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R",
            "b8c6", "d7d6", "g8f6");
        add("w/r1bqkbnr/pppp1ppp/2n5/4p3/5N2/PPPP1PPP/RNBQKB1R",
            "f1b5");
        add("b/r1bqkbnr/pppp1ppp/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R",  // Ruy Lopez
            "a7a6", "g8f6", "d7d6", "f8e7");

        // Berlin Defense: 1.e4 e5 2.Nf3 Nc6 3.Bb5 Nf6
        add("w/r1bqkb1r/pppp1ppp/2n2n2/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R",
            "e1g1", "d2d3");
        add("b/r1bqkb1r/pppp1ppp/2n2n2/1B2p3/4P3/PPPP1PPP/RNBQK2R",  // after O-O
            "f6e4");

        // Morphy Defense: 3...a6
        add("w/r1bqkbnr/1ppp1ppp/p1n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R",
            "f1b5", "b5a4");
        add("b/r1bqkbnr/1ppp1ppp/p7/3Pp3/5N2/PPP2PPP/RNBQKB1R",  // Ba4 a6 line
            "g8f6", "d7d6");

        // ---- Sicilian: 1.e4 c5 ----
        add("w/rnbqkbnr/pp1ppppp/8/2p1P3/8/PPPP1PPP/RNBQKBNR",
            "g1f3", "b1c3");
        // Open Sicilian: 2.Nf3
        add("b/rnbqkbnr/pp1ppppp/8/2p1P3/5N2/PPPP1PPP/RNBQKB1R",
            "d7d6", "b8c6", "e7e6", "g7g6");
        // Najdorf: 2...d6 3.d4 cxd4 4.Nxd4 Nf6 5.Nc3 a6
        add("w/rnbqkbnr/pp2pppp/3p4/3nP3/8/PPP2PPP/RNBQKB1R",
            "b1c3");
        add("b/rnbqkb1r/pp2pppp/3p4/3NP3/2N5/PPP2PPP/R1BQKB1R",
            "g8f6");
        add("w/rnbqkb1r/pp2pppp/3p1n2/3NP3/2N5/PPP2PPP/R1BQKB1R",
            "f1e2", "f1b5");
        add("b/rnbqkb1r/1p1ppppp/p5n2/3NP3/2N5/PPP2PPP/R1BQKB1R",  // a6 Najdorf
            "e7e5", "e7e6", "d7d5");

        // Dragon: ...g6
        add("b/rnbqkb1r/pp2pp1p/3p2p1/3NP3/2N5/PPP2PPP/R1BQKB1R",
            "f8g7");
        add("w/rnbqk2r/pp2ppbp/3p2p1/3NP3/2N5/PPP2PPP/R1BQKB1R",
            "c1e3", "f2f3");

        // ---- Queen's Gambit: 1.d4 d5 2.c4 ----
        add("b/rnbqkbnr/pppppppp/8/3P4/8/PPP1PPPP/RNBQKBNR",
            "d7d5", "g8f6", "e7e6");
        add("w/rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR",
            "c2c4");
        add("b/rnbqkbnr/ppp1pppp/8/2Pp4/8/PP2PPPP/RNBQKBNR",
            "e7e6", "c7c6", "d5c4");

        // QGD: 2...e6
        add("w/rnbqkbnr/ppp2ppp/4p3/2Pp4/8/PP2PPPP/RNBQKBNR",
            "b1c3", "g1f3");
        add("b/rnbqkbnr/ppp2ppp/4p3/2Pp4/2N5/PP2PPPP/R1BQKBNR",
            "g8f6", "f8b4");
        add("w/rnbqk2r/ppp1bppp/4pn2/3p4/2PP4/2N2N2/PP2PPPP/R1BQKB1R",
            "f1e2", "c1g5");

        // QGA: 2...dxc4
        add("w/rnbqkbnr/ppp1pppp/8/2pP4/8/PP2PPPP/RNBQKBNR",
            "e2e4", "g1f3");

        // Slav: 2...c6
        add("w/rnbqkbnr/pp2pppp/2p5/2Pp4/8/PP2PPPP/RNBQKBNR",
            "g1f3", "b1c3");

        // ---- King's Indian Defense ----
        add("b/rnbqkb1r/pppppp1p/6p1/2PP4/8/PP2PPPP/RNBQKBNR",
            "f8g7");
        add("w/rnbqk2r/ppppppbp/5np1/3P4/2P5/8/PP2PPPP/RNBQKBNR",
            "b1c3");
        add("b/rnbqk2r/ppp1ppbp/3p2p1/2PPn3/2N5/PP2PPPP/R1BQKBNR",
            "e1g1", "d7d6");
        // Classical KID
        add("w/rnbq1rk1/ppp1ppbp/3p1np1/3P4/2P1P3/2N2N2/PP3PPP/R1BQKB1R",
            "f1e2", "f1d3");

        // ---- French Defense: 1.e4 e6 ----
        add("w/rnbqkbnr/pppp1ppp/4p3/4P3/8/PPPP1PPP/RNBQKBNR",
            "d2d4", "b1c3");
        // Advance: 3.e5
        add("b/rnbqkbnr/ppp2ppp/3pp3/3PP3/8/PPP2PPP/RNBQKBNR",
            "b8c6", "c7c5");
        // Tarrasch: 3.Nd2
        add("w/rnbqkbnr/ppp2ppp/4p3/3pP3/3P4/8/PPP2PPP/RNBQKBNR",
            "b1d2", "b1c3");

        // ---- Caro-Kann: 1.e4 c6 ----
        add("w/rnbqkbnr/pp1ppppp/2p5/4P3/8/PPPP1PPP/RNBQKBNR",
            "d2d4", "b1c3");
        add("b/rnbqkbnr/pp1ppppp/8/3pP3/3P4/PPP2PPP/RNBQKBNR",
            "d5e4");
        // Classical: 3.Nc3 dxe4 4.Nxe4
        add("b/rnbqkbnr/pp2pppp/8/3pN3/8/PPP2PPP/R1BQKBNR",
            "b8d7", "g8f6");

        // ---- Italian / Giuoco Piano: 1.e4 e5 2.Nf3 Nc6 3.Bc4 ----
        add("w/r1bqkbnr/pppp1ppp/2n5/4p3/5N2/PPPP1PPP/RNBQKB1R",
            "f1c4", "f1b5");
        add("b/r1bqkbnr/pppp1ppp/2n5/2B1p3/5N2/PPPP1PPP/RNBQK2R",
            "f8c5", "g8f6", "f8b4");
        // Giuoco Piano main line
        add("w/r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R",
            "c2c3", "d2d3");
        // Two Knights: ...Nf6
        add("w/r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R",
            "d2d3", "g1g5");

        // ---- English Opening: 1.c4 ----
        add("b/rnbqkbnr/pppppppp/8/2P5/8/PP1PPPPP/RNBQKBNR",
            "e7e5", "g8f6", "c7c5");
        add("w/rnbqkbnr/pppp1ppp/8/2P1p3/8/PP1PPPPP/RNBQKBNR",
            "b1c3", "g1f3");
        add("b/r1bqkbnr/pppp1ppp/2n5/2P1p3/2N5/PP1PPPPP/R1BQKBNR",
            "g8f6", "f8b4");
    }

    /** Helper to add a multi-candidate book entry. */
    private void add(String fen, String... moves) {
        book.put(fen, moves);
    }
}
