# Implementation provenance

The source in `engine-core` was written as a clean, independent implementation for the Chaturanga project.

## Original expression

- Source structure, APIs, data layout and all Java implementations are project-specific.
- Piece-square terms are generated from geometric formulas rather than copied tables.
- Zobrist constants are deterministically generated from a project-specific seed.
- No third-party opening book, endgame tablebase or neural network is bundled.
- No source code was copied from Stockfish or another engine.

## Standard ideas used

The engine uses established computer-chess techniques: bitboards, make/unmake, perft, Zobrist hashing, iterative deepening, negamax, alpha-beta pruning, principal-variation search, quiescence search, transposition tables, killer/history ordering, aspiration windows and late-move reductions.

These are algorithmic ideas. The implementation, tests, documentation and tuning in this module are the project's own expression. Future contributors should preserve this distinction: study concepts and public specifications, but do not paste code or data whose license is incompatible with the repository's MIT license.

## Verification trail

Correctness is demonstrated using public, position-based perft counts. Position strings and numeric chess outcomes are test facts; the move generator that produces them is independently implemented here.

Any future external data, code, generated model or tablebase must be listed in this file with its exact source and license before being committed.
