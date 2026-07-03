# Chaturanga Classical Engine

Chaturanga Classical is an independently implemented orthodox-chess engine written in Java. It is intentionally separated from the Swing application: Phase 1 produces a reusable engine and UCI executable; Phase 2 will integrate it through a narrow service boundary.

## Current engine

- Twelve bitboards plus a mailbox, with `a1 = 0` square indexing
- Strict six-field FEN parser and serializer
- Complete legal move generation: checks, pins, both castles, en passant and all promotions
- Incremental deterministic Zobrist hashing
- Reversible make/unmake with repetition and fifty-move state
- Tapered classical evaluation: material, generated piece-square terms, mobility, pawn structure, passed pawns, bishop pair and king shelter
- Iterative deepening, fail-soft negamax, alpha-beta and principal-variation search
- Check-aware quiescence search
- Depth-preferred transposition table with mate-score normalization
- TT, capture, killer and history move ordering
- Conservative late-move reductions and aspiration windows
- Time, depth and node limits with cooperative cancellation
- UCI protocol, perft command and deterministic benchmark

No Stockfish source, tables, NNUE networks or opening-book data are included.

## Requirements

- JDK 24 or newer on `PATH`
- Windows PowerShell for the supplied build wrappers

No third-party dependencies are required.

## Build and test

```bat
cd engine-core
test.cmd
```

The executable JAR is written to `build\chaturanga-engine.jar`.

## Run as a UCI engine

```bat
run.cmd
```

Example commands:

```text
uci
isready
position startpos moves e2e4 e7e5
go depth 6
quit
```

The engine can be loaded into any GUI supporting UCI by configuring:

```text
java -jar C:\absolute\path\to\engine-core\build\chaturanga-engine.jar
```

## Diagnostics

Inside UCI mode:

```text
perft 4
bench 5
d
```

Or run the benchmark directly:

```bat
bench.cmd 5
```

Reference results on the development machine (JDK 24, July 2026):

- 101 automated assertions passed
- Start perft: `20 / 400 / 8,902 / 197,281`
- Kiwipete: `48 / 2,039 / 97,862`
- En-passant/endgame suite: `14 / 191 / 2,812 / 43,238`
- Depth-5 benchmark: `181,600` nodes, approximately `335k NPS`, signature `c9355990b326d2a3`

Performance varies by machine and JVM warm-up. Correctness counts are invariant.

## Package map

- `Position`, `Move`, `Undo`: search state and reversible transitions
- `AttackTables`, `MoveGenerator`: attacks and legal move generation
- `Fen`, `Perft`: notation and correctness diagnostics
- `ClassicalEvaluator`: deterministic handcrafted evaluation
- `SearchEngine`, `TranspositionTable`: search and caching
- `uci/UciMain`: protocol boundary
- `cli`: benchmark and perft entry points

## Scope and strength

This is a credible classical engine foundation, not a claim of Stockfish parity. Modern Stockfish represents decades of work and uses NNUE. Strength must be improved through measured A/B self-play and tactical suites, not by adding unverified heuristics.

The Phase 1 handoff and remaining strength roadmap are recorded in `PHASE1_HANDOFF.md`.
