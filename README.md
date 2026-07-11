<h1 align="center">Chaturanga ♟️</h1>

<p align="center">
  <strong>A high-performance classical Chess Engine and Premium Custom GUI built entirely from scratch in Java.</strong>
</p>

---

## 🌟 Overview

Chaturanga is a flagship software engineering project designed to showcase deep algorithmic knowledge, low-level optimization, and custom UI design. It features a completely decoupled architecture, consisting of an insanely fast bitboard-based chess engine (`engine-core`) and a sleek, animated Swing GUI complete with live coaching analysis.

## 🚀 Key Features

### 🧠 The Engine (`engine-core`)
The engine is written in pure Java using a standard **UCI-like** architecture, capable of evaluating millions of positions per second thanks to advanced bitwise math and heavily optimized heuristics:
- **Bitboard State Representation**: Zero-allocation bitboards using 64-bit Longs for lightning-fast move generation.
- **Minimax with Alpha-Beta Pruning**: The core adversarial search tree.
- **Iterative Deepening**: Progressively deeper searches ensuring the best move is always ready when the clock runs out.
- **Transposition Tables (TT)**: Zobrist hashing caches exact evaluations to avoid redundant calculation trees.
- **Null Move Pruning (NMP)**: Aggressive pruning heuristic assuming that passing a turn is the worst possible move.
- **Static Exchange Evaluation (SEE)**: Tactically evaluates capture sequences without expanding the search tree, critical for Quiescence Search and Move Ordering.
- **Futility Pruning**: Discards branches at shallow depths if they fail to raise the alpha bound by a specific margin.

### 🎨 The Interface (Custom Swing GUI)
A major focus of this project was breaking out of the ugly, dated look of standard Java apps.
- **Modern Animated Toggles**: Custom-painted, iOS-style sliding switches for all toolbar actions.
- **Live Coaching Analysis**: Play against the engine (or watch it play itself) with real-time centipawn analysis and move breakdowns.
- **Custom Aesthetic**: HSL-tailored colors, antialiasing rendering hints, and a strict, pixel-perfect layout using advanced CardLayout and GridBagLayout configurations.
- **Zero "Ghosting" & Freezing**: The GUI operates on a dedicated Event Dispatch Thread (EDT) while offloading heavy engine computations to an asynchronous executor pool, preventing UI locking.

---

## 💿 Installation & Usage

You do **not** need Java installed to play Chaturanga! It uses `jpackage` to bundle a heavily minimized, native Java Runtime Environment.

### Download Native Executable
1. Go to the **Releases** tab on GitHub.
2. Download `Chaturanga-Windows.zip`.
3. Extract the folder and double-click `Chaturanga.exe`.

### Build from Source
If you wish to compile the engine and GUI yourself:

```bash
# Clone the repository
git clone https://github.com/yourusername/Chaturanga.git
cd Chaturanga

# Build using the Gradle wrapper
./gradlew build -x test

# Package the standalone native executable (Windows)
./gradlew installDist
jpackage --name "Chaturanga" --input "build/install/Chaturanga/lib" --main-jar "Chaturanga-1.0-SNAPSHOT.jar" --main-class "main.Main" --type app-image --dest "build/dist"
```

## 🏗️ Architecture

```
Chaturanga
├── engine-core/          # The high-performance algorithmic search engine
│   ├── SEE.java          # Static Exchange Evaluation heuristics
│   ├── SearchEngine.java # Alpha-Beta & Iterative Deepening loop
│   └── Bitboards.java    # Low-level bitwise move generators
└── src/main/java/gui/    # The presentation layer
    ├── GameWindow.java   # Core orchestrator and Layout manager
    ├── ToggleSwitch.java # Custom animated UI components
    └── NewEngineAdapter  # Decouples the engine from the GUI
```

## 📜 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
