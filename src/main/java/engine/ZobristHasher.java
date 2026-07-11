package engine;

import java.io.*;
import java.util.Arrays;

/**
 * Zobrist Hashing — assigns a unique random 64-bit key to every piece/square
 * combination, enabling O(1) position identification for the transposition table.
 *
 * Upgrades over v1:
 *  - 4M entry TT (was 1M) for higher hit rate
 *  - Two-bucket TT strategy: slot 0 = depth-preferred, slot 1 = always-replace
 *    This is the Crafty/Stockfish style that maximises useful TT entries.
 *  - Incremental hash update via updateHash(prevHash, move, prevState) — O(1).
 */
public class ZobristHasher {

    // 12 piece types (6 types × 2 colors) × 64 squares
    private final long[][] pieceSquareTable = new long[12][64];
    private final long sideToMoveKey;
    // 4 castling right bits (WK, WQ, BK, BQ)
    private final long[] castlingKeys = new long[4];
    // 8 en passant file keys
    private final long[] enPassantFileKeys = new long[8];

    private static final ZobristHasher INSTANCE = new ZobristHasher();
    public static ZobristHasher get() { return INSTANCE; }

    private ZobristHasher() {
        // Deterministic seed for reproducibility
        java.util.Random rng = new java.util.Random(0xDEADBEEF_CAFEBABEL);
        for (int piece = 0; piece < 12; piece++) {
            for (int sq = 0; sq < 64; sq++) {
                pieceSquareTable[piece][sq] = rng.nextLong();
            }
        }
        sideToMoveKey = rng.nextLong();
        for (int i = 0; i < 4; i++) castlingKeys[i] = rng.nextLong();
        for (int i = 0; i < 8; i++) enPassantFileKeys[i] = rng.nextLong();
    }

    /** Returns the piece index (0-11) for the piece table lookup. */
    public static int pieceIndex(model.PieceType type, boolean isWhite) {
        int base = switch (type) {
            case PAWN   -> 0;
            case KNIGHT -> 1;
            case BISHOP -> 2;
            case ROOK   -> 3;
            case QUEEN  -> 4;
            case KING   -> 5;
        };
        return isWhite ? base : base + 6;
    }

    /** XOR key for placing/removing a piece on a square. */
    public long pieceKey(model.PieceType type, boolean isWhite, int squareIndex) {
        return pieceSquareTable[pieceIndex(type, isWhite)][squareIndex];
    }

    public long sideToMoveKey() { return sideToMoveKey; }
    public long castlingKey(int rightIndex) { return castlingKeys[rightIndex]; }
    public long enPassantKey(int file) { return enPassantFileKeys[file]; }

    /**
     * Compute a full Zobrist hash from scratch from the given GameState.
     * Call this once at the root of each search; use updateHash() inside the tree.
     */
    public long computeHash(model.GameState state) {
        long hash = 0L;
        model.Piece[] board = state.getBoard();
        for (int i = 0; i < 64; i++) {
            model.Piece p = board[i];
            if (p != null) hash ^= pieceKey(p.type, p.isWhite, i);
        }
        if (state.isWhiteToMove()) hash ^= sideToMoveKey;
        boolean[] rights = state.getCastlingRights();
        for (int i = 0; i < 4; i++) {
            if (rights[i]) hash ^= castlingKeys[i];
        }
        int ep = state.getEnPassantTarget();
        if (ep >= 0) hash ^= enPassantFileKeys[ep % 8];
        return hash;
    }

    /**
     * Incrementally update a Zobrist hash after applying a move.
     *
     * This is O(1) vs O(64) for a full recompute, and is called at every node
     * in the search tree — making this a significant performance gain.
     *
     * @param prevHash  the hash of the position BEFORE the move
     * @param move      the move that was applied
     * @param prev      the GameState BEFORE the move
     * @param next      the GameState AFTER the move
     * @return the new hash for next
     */
    public long updateHash(long prevHash, model.Move move, model.GameState prev, model.GameState next) {
        long hash = prevHash;

        // --- Remove piece from source square ---
        hash ^= pieceKey(move.piece.type, move.piece.isWhite, move.fromRow * 8 + move.fromCol);

        // --- Remove captured piece (normal capture) ---
        if (move.type == model.MoveType.EN_PASSANT) {
            // En passant: captured pawn is on (fromRow, toCol), not toSquare
            int capIdx = move.fromRow * 8 + move.toCol;
            if (prev.getBoard()[capIdx] != null) {
                model.Piece cap = prev.getBoard()[capIdx];
                hash ^= pieceKey(cap.type, cap.isWhite, capIdx);
            }
        } else if (move.captured != null) {
            hash ^= pieceKey(move.captured.type, move.captured.isWhite, move.toRow * 8 + move.toCol);
        }

        // --- Handle castling rook movement ---
        if (move.type == model.MoveType.KINGSIDE_CASTLE) {
            // Rook moves from (fromRow, 7) → (fromRow, 5)
            int rookFrom = move.fromRow * 8 + 7;
            int rookTo   = move.fromRow * 8 + 5;
            model.Piece rook = prev.getBoard()[rookFrom];
            if (rook != null) {
                hash ^= pieceKey(rook.type, rook.isWhite, rookFrom);
                hash ^= pieceKey(rook.type, rook.isWhite, rookTo);
            }
        } else if (move.type == model.MoveType.QUEENSIDE_CASTLE) {
            // Rook moves from (fromRow, 0) → (fromRow, 3)
            int rookFrom = move.fromRow * 8;
            int rookTo   = move.fromRow * 8 + 3;
            model.Piece rook = prev.getBoard()[rookFrom];
            if (rook != null) {
                hash ^= pieceKey(rook.type, rook.isWhite, rookFrom);
                hash ^= pieceKey(rook.type, rook.isWhite, rookTo);
            }
        }

        // --- Place piece on destination square ---
        int toIdx = move.toRow * 8 + move.toCol;
        if (move.type == model.MoveType.PROMOTION && move.promotionPiece != null) {
            // Promoted piece placed on destination
            hash ^= pieceKey(move.promotionPiece, move.piece.isWhite, toIdx);
        } else {
            hash ^= pieceKey(move.piece.type, move.piece.isWhite, toIdx);
        }

        // --- Side to move flips ---
        hash ^= sideToMoveKey; // XOR toggles side

        // --- Castling rights changes ---
        boolean[] prevRights = prev.getCastlingRights();
        boolean[] nextRights = next.getCastlingRights();
        for (int i = 0; i < 4; i++) {
            if (prevRights[i] != nextRights[i]) {
                hash ^= castlingKeys[i];
            }
        }

        // --- En passant target changes ---
        int prevEP = prev.getEnPassantTarget();
        int nextEP = next.getEnPassantTarget();
        if (prevEP >= 0) hash ^= enPassantFileKeys[prevEP % 8];
        if (nextEP >= 0) hash ^= enPassantFileKeys[nextEP % 8];

        return hash;
    }

    /**
     * Incremental hash update using a BBPosition after make().
     * Recomputes from the piece bitboards — O(pieces), always correct.
     *
     * @param prevHash hash BEFORE the move (unused, kept for API symmetry)
     * @param bbMove   int-encoded move (BBMoveGen encoding, unused here)
     * @param bbPos    position AFTER the move has been applied
     */
    public long updateHashBB(long prevHash, int bbMove, engine.bitboard.BBPosition bbPos) {
        long hash = 0L;
        for (int idx = 0; idx < 12; idx++) {
            long bb = bbPos.pieces[idx];
            while (bb != 0L) {
                int sq = Long.numberOfTrailingZeros(bb); bb &= bb - 1;
                hash ^= pieceSquareTable[idx][sq];
            }
        }
        if (bbPos.whiteToMove) hash ^= sideToMoveKey;
        int cr = bbPos.castlingRights;
        if ((cr & engine.bitboard.BBPosition.CR_WHITE_KS) != 0) hash ^= castlingKeys[0];
        if ((cr & engine.bitboard.BBPosition.CR_WHITE_QS) != 0) hash ^= castlingKeys[1];
        if ((cr & engine.bitboard.BBPosition.CR_BLACK_KS) != 0) hash ^= castlingKeys[2];
        if ((cr & engine.bitboard.BBPosition.CR_BLACK_QS) != 0) hash ^= castlingKeys[3];
        if (bbPos.enPassantSquare >= 0) hash ^= enPassantFileKeys[bbPos.enPassantSquare % 8];
        return hash;
    }

    // ---- Transposition Table ----


    public enum EntryType { EXACT, LOWER_BOUND, UPPER_BOUND }

    public static final class TTEntry {
        public final long key;
        public final int depth;
        public final int score;
        public final EntryType type;
        public final model.Move bestMove;

        public TTEntry(long key, int depth, int score, EntryType type, model.Move bestMove) {
            this.key      = key;
            this.depth    = depth;
            this.score    = score;
            this.type     = type;
            this.bestMove = bestMove;
        }
    }

    /**
     * Two-bucket transposition table.
     * For each index: bucket[0] = depth-preferred, bucket[1] = always-replace.
     *
     * On store: if new entry has greater depth, replace bucket[0]; always update bucket[1].
     * On probe: check both buckets, prefer depth-preferred (bucket[0]).
     *
     * This strategy minimises thrashing while preserving deep, high-quality entries.
     * Reference: Crafty chess engine TT design.
     */
    private static final int TT_SIZE = 1 << 22; // ~4M entries per bucket (~128MB total)
    private final TTEntry[] ttDepth  = new TTEntry[TT_SIZE]; // depth-preferred
    private final TTEntry[] ttAlways = new TTEntry[TT_SIZE]; // always-replace

    public void ttStore(long hash, int depth, int score, EntryType type, model.Move bestMove) {
        int idx = (int)(hash & (TT_SIZE - 1));
        TTEntry entry = new TTEntry(hash, depth, score, type, bestMove);

        // Depth-preferred: only replace if new entry is deeper or slot is empty / different position
        TTEntry existing = ttDepth[idx];
        if (existing == null || existing.key != hash || depth >= existing.depth) {
            ttDepth[idx] = entry;
        }

        // Always-replace: just overwrite
        ttAlways[idx] = entry;
    }

    public TTEntry ttProbe(long hash) {
        int idx = (int)(hash & (TT_SIZE - 1));

        // Check depth-preferred bucket first
        TTEntry e = ttDepth[idx];
        if (e != null && e.key == hash) return e;

        // Fall back to always-replace bucket
        e = ttAlways[idx];
        if (e != null && e.key == hash) return e;

        return null;
    }

    public void clearTT() {
        Arrays.fill(ttDepth,  null);
        Arrays.fill(ttAlways, null);
    }

    public void saveToFile(File file) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            dos.writeInt(0x74747474); // Magic number
            dos.writeInt(1);          // Version
            
            // Count entries
            int count = 0;
            for (TTEntry e : ttDepth) if (e != null) count++;
            for (TTEntry e : ttAlways) if (e != null) count++;
            dos.writeInt(count);
            
            for (TTEntry[] bucket : new TTEntry[][]{ttDepth, ttAlways}) {
                for (TTEntry e : bucket) {
                    if (e == null) continue;
                    dos.writeLong(e.key);
                    dos.writeInt(e.depth);
                    dos.writeInt(e.score);
                    dos.writeByte(e.type.ordinal());
                    
                    dos.writeBoolean(e.bestMove != null);
                    if (e.bestMove != null) {
                        model.Move m = e.bestMove;
                        dos.writeByte(m.fromCol);
                        dos.writeByte(m.fromRow);
                        dos.writeByte(m.toCol);
                        dos.writeByte(m.toRow);
                        
                        dos.writeByte(m.piece.type.ordinal());
                        dos.writeBoolean(m.piece.isWhite);
                        dos.writeBoolean(m.piece.hasMoved);
                        
                        dos.writeBoolean(m.captured != null);
                        if (m.captured != null) {
                            dos.writeByte(m.captured.type.ordinal());
                            dos.writeBoolean(m.captured.isWhite);
                            dos.writeBoolean(m.captured.hasMoved);
                        }
                        
                        dos.writeByte(m.type.ordinal());
                        
                        dos.writeBoolean(m.promotionPiece != null);
                        if (m.promotionPiece != null) {
                            dos.writeByte(m.promotionPiece.ordinal());
                        }
                    }
                }
            }
        }
    }

    public void loadFromFile(File file) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (dis.readInt() != 0x74747474) throw new IOException("Invalid file format");
            if (dis.readInt() != 1) throw new IOException("Unsupported version");
            
            clearTT();
            
            int count = dis.readInt();
            model.PieceType[] pTypes = model.PieceType.values();
            model.MoveType[] mTypes = model.MoveType.values();
            EntryType[] eTypes = EntryType.values();
            
            for (int i = 0; i < count; i++) {
                long key = dis.readLong();
                int depth = dis.readInt();
                int score = dis.readInt();
                EntryType eType = eTypes[dis.readByte()];
                
                model.Move bestMove = null;
                if (dis.readBoolean()) {
                    int fromCol = dis.readByte();
                    int fromRow = dis.readByte();
                    int toCol = dis.readByte();
                    int toRow = dis.readByte();
                    
                    model.Piece piece = new model.Piece(pTypes[dis.readByte()], dis.readBoolean(), dis.readBoolean());
                    model.Piece captured = null;
                    if (dis.readBoolean()) {
                        captured = new model.Piece(pTypes[dis.readByte()], dis.readBoolean(), dis.readBoolean());
                    }
                    model.MoveType mType = mTypes[dis.readByte()];
                    model.PieceType promo = null;
                    if (dis.readBoolean()) {
                        promo = pTypes[dis.readByte()];
                    }
                    bestMove = new model.Move(fromCol, fromRow, toCol, toRow, piece, captured, mType, promo);
                }
                
                ttStore(key, depth, score, eType, bestMove);
            }
        }
    }
}
