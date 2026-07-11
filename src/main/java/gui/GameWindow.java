package gui;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import analysis.CoachingAnalyzer;
import engine.*;
import io.PGNExporter;
import model.GameState;
import model.Move;
import rules.SANFormatter;
import utils.ChessTimer;

/**
 * Main game window — fully integrated with the Chaturanga Search Engine.
 *
 * Modes:
 *  - Play Mode  : Human vs Engine (or Human vs Human) with timers + move history
 *  - Coaching   : Analysis panel + eval bar replace the left timer panel
 *  - Viz Mode   : Search tree is recorded and shown in the analysis panel
 *
 * Engine Integration fixes in this version:
 *  - Board is locked (input disabled) while the engine is thinking
 *  - "Engine thinking…" spinner shown during search
 *  - Eval bar updates after EVERY move (not just in coaching mode)
 *  - Coaching analysis correctly captures `stateBefore` before applying move
 *  - Engine depth: 8 for play, 5 for coaching, 4 for viz
 *  - Colour-lock: player cannot move opponent's pieces
 *  - Proper cancellation of previous engine Future on restart/undo/load
 *  - Timer labels initialised with actual time on startup
 */
public class GameWindow {

    // ---- Engine infrastructure ----
    private final ExecutorService engineThread = Executors.newSingleThreadExecutor();
    private volatile Future<?> pendingEngine   = null;   // active engine task

    private final SANFormatter    sanFormatter    = new SANFormatter();
    private final SearchEngine    searchEngine    = new SearchEngine();
    private final CoachingAnalyzer coachingAnalyzer = new CoachingAnalyzer();

    // ---- Game mode flags ----
    private boolean coachingMode    = false;
    private boolean vsEngine        = true;
    private boolean playerIsWhite   = true;
    private boolean visualizationMode = false;
    private volatile boolean engineThinking = false;

    // Incremented every time a new engine task starts.
    // The task captures this value; if it no longer matches when the result
    // arrives, the task was cancelled — discard the move.
    private volatile int engineGeneration = 0;

    // ---- UI panels ----
    private JPanel     leftPanel;
    private AnalysisPanel analysisPanel;
    private EvalBar    evalBar;
    private BoardPanel board;
    private ChessTimer chessTimer;
    private JTextArea  moveHistoryArea;
    private JFrame     frame;

    // Status bar (shows "Engine thinking…", "Your turn", etc.)
    private JLabel statusLabel;

    // ---- Constructor ----

    public GameWindow(int baseMinutes, int incrementSeconds) {
        frame = new JFrame("Chaturanga ♟");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(new Color(22, 22, 30));

        // ---- Right Panel (Move History) ----
        JPanel rightPanel = buildRightPanel();

        // ---- Left Panel (toggling: timer ↔ analysis) ----
        leftPanel = new JPanel(new CardLayout());
        leftPanel.setBackground(new Color(22, 22, 30));
        leftPanel.setPreferredSize(new Dimension(200, 700));

        JPanel timerPanel = buildTimerPanel(baseMinutes);
        JLabel blackTimerLabel = (JLabel) timerPanel.getClientProperty("blackTimer");
        JLabel whiteTimerLabel = (JLabel) timerPanel.getClientProperty("whiteTimer");

        analysisPanel = new AnalysisPanel();
        evalBar       = new EvalBar();

        leftPanel.add(timerPanel,    "PLAY");
        leftPanel.add(analysisPanel, "COACHING");

        // ---- Center Panel (Eval Bar + Board) ----
        board = new BoardPanel();
        board.setBorder(new LineBorder(new Color(60, 60, 80), 3));

        JPanel boardRow = new JPanel(new BorderLayout(6, 0));
        boardRow.setBackground(new Color(22, 22, 30));
        boardRow.add(evalBar, BorderLayout.WEST);
        boardRow.add(board,   BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(new Color(22, 22, 30));
        centerPanel.setBorder(new EmptyBorder(8, 0, 8, 0));
        centerPanel.add(boardRow);

        // ---- Status Bar ----
        JPanel statusBar = buildStatusBar();

        // ---- Toolbar ----
        JPanel toolbar = buildToolbar(blackTimerLabel, whiteTimerLabel);

        // ---- Timer Setup ----
        chessTimer = new ChessTimer(baseMinutes, incrementSeconds, board.getState());
        chessTimer.setWhiteLabelUpdater(whiteTimerLabel::setText);
        chessTimer.setBlackLabelUpdater(blackTimerLabel::setText);
        chessTimer.setOnTimeUp(() -> {
            String loser = board.getState().isWhiteToMove() ? "White" : "Black";
            JOptionPane.showMessageDialog(frame, loser + " ran out of time!");
            board.getState().setGameOver(true);
            setStatus("⏱ " + loser + " flagged!", new Color(220, 80, 80));
        });

        // ---- Move Listener (fires after every human or engine move) ----
        board.setOnMoveListener(move -> {
            // Snapshot the state NOW (before checkGameOver can mutate it)
            // board.getState() returns a live reference — we must copy it
            // so the engine task can't see a later isGameOver=true flag.
            GameState stateAfter = board.getState().copy();

            // Update timer
            chessTimer.addIncrement(move.piece.isWhite);
            chessTimer.setState(board.getState()); // timer tracks live state (game-over flag)

            // Append move to history panel
            String san = sanFormatter.toSAN(move, false, stateAfter.isGameOver());
            if (move.piece.isWhite) {
                moveHistoryArea.append(stateAfter.getFullMoveNumber() + ". " + san + " ");
            } else {
                moveHistoryArea.append(san + "\n");
            }
            // Auto-scroll move history
            moveHistoryArea.setCaretPosition(moveHistoryArea.getDocument().getLength());

            // If game is over, nothing more to do
            if (stateAfter.isGameOver()) {
                engineThinking = false;
                board.setInputEnabled(true);
                return;
            }

            boolean isEngineTurn = vsEngine && (stateAfter.isWhiteToMove() != playerIsWhite);

            // ---- Eval bar update (always, async) ----
            engineThread.submit(() -> {
                EvalBreakdown bd = searchEngine.evaluatePosition(stateAfter);
                SwingUtilities.invokeLater(() -> evalBar.update(bd));

                // Coaching analysis (only in coaching mode, and only for the human's move)
                if (coachingMode && !isEngineTurn) {
                    // stateBefore = the state that was current BEFORE this move
                    // We recover it via board.getPreviousState() which we added to BoardPanel
                    GameState stateBefore = board.getPreviousState();
                    if (stateBefore != null) {
                        analysisPanel.updateEval(bd);
                        CoachingAnalyzer.MoveAnalysis analysis = coachingAnalyzer.analyse(stateBefore, move);
                        analysisPanel.updateMoveQuality(analysis);
                    }
                }
            });

            // ---- Engine reply ----
            if (isEngineTurn) {
                triggerEngineMove(stateAfter);
            } else {
                // Human's turn
                engineThinking = false;
                board.setInputEnabled(true);
                setStatus("Your turn", new Color(100, 220, 130));
            }
        });

        // Fired on restart / undo / redo / load
        board.setOnStateChangedListener(() -> {
            cancelPendingEngine();
            chessTimer.setState(board.getState());
            rebuildMoveHistory();
            evalBar.reset();
            analysisPanel.reset();
            setStatus("Your turn", new Color(100, 220, 130));

            // If in vs-engine mode and it's now the engine's turn after undo, trigger engine
            GameState s = board.getState();
            if (vsEngine && !s.isGameOver() && (s.isWhiteToMove() != playerIsWhite)) {
                triggerEngineMove(s);
            }
        });

        chessTimer.start();

        // ---- Menu Bar ----
        JMenuBar menuBar = new JMenuBar();
        JMenu engineMenu = new JMenu("Engine");
        
        JMenuItem loadBookItem = new JMenuItem("Load Opening Book (PGN)...");
        loadBookItem.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select PGN File");
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    engine.OpeningBook.get().loadPGN(chooser.getSelectedFile());
                    JOptionPane.showMessageDialog(frame, "Opening book loaded successfully!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error loading book: " + ex.getMessage());
                }
            }
        });
        
        JMenuItem saveTTItem = new JMenuItem("Save Engine Memory (TT)...");
        saveTTItem.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save TT");
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    engine.ZobristHasher.get().saveToFile(chooser.getSelectedFile());
                    JOptionPane.showMessageDialog(frame, "Engine memory saved!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error saving memory: " + ex.getMessage());
                }
            }
        });
        
        JMenuItem loadTTItem = new JMenuItem("Load Engine Memory (TT)...");
        loadTTItem.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Load TT");
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    engine.ZobristHasher.get().loadFromFile(chooser.getSelectedFile());
                    JOptionPane.showMessageDialog(frame, "Engine memory loaded!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error loading memory: " + ex.getMessage());
                }
            }
        });

        engineMenu.add(loadBookItem);
        engineMenu.addSeparator();
        engineMenu.add(saveTTItem);
        engineMenu.add(loadTTItem);
        menuBar.add(engineMenu);
        frame.setJMenuBar(menuBar);

        frame.add(toolbar,     BorderLayout.NORTH);
        frame.add(leftPanel,   BorderLayout.WEST);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(rightPanel,  BorderLayout.EAST);
        frame.add(statusBar,   BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Initial status
        setStatus("Your turn", new Color(100, 220, 130));
    }

    // ======================================================================
    //  Engine Integration
    // ======================================================================

    /**
     * Submit an engine search asynchronously.
     * Locks the board, shows "thinking…", then applies the result on the EDT.
     */
    private void triggerEngineMove(GameState stateForEngine) {
        if (stateForEngine.isGameOver()) return;
        cancelPendingEngine();

        // Capture generation AFTER cancel so the new task gets a fresh stamp.
        final int myGeneration = ++engineGeneration;

        engineThinking = true;
        board.setInputEnabled(false);
        setStatus("⚙ Engine thinking…", new Color(255, 200, 60));

        pendingEngine = engineThread.submit(() -> {
            int depth = visualizationMode ? 4 : 8;

            SearchTreeRecorder localRecorder = null;
            if (visualizationMode) {
                localRecorder = new SearchTreeRecorder();
                searchEngine.setRecorder(localRecorder);
            } else {
                searchEngine.setRecorder(null);
            }

            Move engineMove = searchEngine.findBestMove(stateForEngine, depth, 8_000);

            if (visualizationMode && localRecorder != null) {
                localRecorder.stopRecording();
                SearchTreeRecorder.SearchNode rootNode = localRecorder.getRoot();
                SwingUtilities.invokeLater(() ->
                        analysisPanel.updateSearchTree(rootNode));
            }


            SwingUtilities.invokeLater(() -> {
                // Discard result if a newer engine task was started (cancelled search)
                if (myGeneration != engineGeneration) return;

                engineThinking = false;
                if (engineMove != null) {
                    board.applyEngineMove(engineMove);
                    // onMoveListener fires inside applyEngineMove → re-enables board
                } else {
                    board.setInputEnabled(true);
                    setStatus("Your turn", new Color(100, 220, 130));
                }
            });
        });
    }

    /** Cancel any in-progress engine search (called on undo/restart/load). */
    private void cancelPendingEngine() {
        if (pendingEngine != null && !pendingEngine.isDone()) {
            searchEngine.stop();
            pendingEngine.cancel(true);
            pendingEngine = null;
        }
        engineThinking = false;
        board.setInputEnabled(true);
    }

    // ======================================================================
    //  UI Builders
    // ======================================================================

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        bar.setBackground(new Color(18, 18, 25));
        statusLabel = new JLabel("Initialising…");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(160, 160, 180));
        bar.add(statusLabel);
        return bar;
    }

    private void setStatus(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        });
    }

    // ---- Toolbar ----

    private JPanel buildToolbar(JLabel blackTimerLabel, JLabel whiteTimerLabel) {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBackground(new Color(28, 28, 38));

        JButton btnRestart = toolbarBtn("⟳ Restart", new Color(70, 130, 180));
        JButton btnUndo    = toolbarBtn("← Undo",    new Color(75, 75, 95));
        JButton btnRedo    = toolbarBtn("→ Redo",    new Color(75, 75, 95));
        JButton btnSave    = toolbarBtn("💾 Save",   new Color(55, 105, 55));
        JButton btnLoad    = toolbarBtn("📂 Load",   new Color(100, 75, 25));

        // Toggle buttons
        JCheckBox btnCoaching = styleCheckBox(new JCheckBox("Coaching"));

        JCheckBox btnVsEngine = styleCheckBox(new JCheckBox("vs Engine"));
        btnVsEngine.setSelected(true);

        JCheckBox btnVizMode = styleCheckBox(new JCheckBox("Viz Mode"));

        // Colour chooser
        JCheckBox btnPlayBlack = styleCheckBox(new JCheckBox("Play as Black"));

        // Personality selector
        JComboBox<String> personalityBox = new JComboBox<>(
                new String[]{"Balanced", "Aggressive", "Positional", "Chaotic"});
        personalityBox.setBackground(new Color(45, 45, 60));
        personalityBox.setForeground(Color.WHITE);
        personalityBox.setFont(new Font("SansSerif", Font.PLAIN, 12));

        toolbar.add(btnRestart);
        toolbar.add(btnUndo);
        toolbar.add(btnRedo);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(btnSave);
        toolbar.add(btnLoad);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(btnVsEngine);
        toolbar.add(btnCoaching);
        toolbar.add(btnVizMode);
        toolbar.add(btnPlayBlack);
        toolbar.add(new JLabel("  🎭") {{ setForeground(new Color(160,160,180)); }});
        toolbar.add(personalityBox);

        // ---- Actions ----

        btnRestart.addActionListener(e -> {
            cancelPendingEngine();
            board.restart();
            evalBar.reset();
            // If engine plays White, trigger immediately after restart
            if (vsEngine && !playerIsWhite) {
                triggerEngineMove(board.getState());
            }
        });

        btnUndo.addActionListener(e -> {
            cancelPendingEngine();
            // Undo twice if vs engine (undo engine's reply too)
            board.undo();
            if (vsEngine) board.undo();
        });

        btnRedo.addActionListener(e -> {
            cancelPendingEngine();
            board.redo();
        });

        btnSave.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("game.fen"));
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    Files.writeString(chooser.getSelectedFile().toPath(),
                            io.FENParser.toFEN(board.getState()));
                    JOptionPane.showMessageDialog(frame, "Game saved!");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Save failed: " + ex.getMessage());
                }
            }
        });

        btnLoad.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    String fen = Files.readString(chooser.getSelectedFile().toPath()).trim();
                    cancelPendingEngine();
                    board.loadGame(fen);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Load failed: " + ex.getMessage());
                }
            }
        });

        btnCoaching.addActionListener(e -> {
            coachingMode = btnCoaching.isSelected();
            updateLeftPanel();
        });

        btnVsEngine.addActionListener(e -> {
            vsEngine = btnVsEngine.isSelected();
            if (!vsEngine) {
                cancelPendingEngine();
                setStatus("Human vs Human", new Color(160, 160, 200));
            }
        });

        btnVizMode.addActionListener(e -> {
            visualizationMode = btnVizMode.isSelected();
            updateLeftPanel();
        });

        btnPlayBlack.addActionListener(e -> {
            playerIsWhite = !btnPlayBlack.isSelected();
            cancelPendingEngine();
            board.restart();
            evalBar.reset();
            // If player chose black, engine plays White first
            if (vsEngine && !playerIsWhite) {
                triggerEngineMove(board.getState());
            }
        });

        personalityBox.addActionListener(e -> {
            switch ((String) personalityBox.getSelectedItem()) {
                case "Aggressive" -> searchEngine.setPersonality(Personality.aggressive());
                case "Positional"  -> searchEngine.setPersonality(Personality.positional());
                case "Chaotic"     -> searchEngine.setPersonality(Personality.chaotic());
                default            -> searchEngine.setPersonality(Personality.balanced());
            }
            engine.ZobristHasher.get().clearTT(); // Clear memory so new personality takes effect immediately
        });

        return toolbar;
    }

    // ---- Timer Panel ----

    private JPanel buildTimerPanel(int baseMinutes) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(22, 22, 30));
        panel.setBorder(new EmptyBorder(20, 10, 20, 10));

        String initialTime = String.format("%02d:00", baseMinutes);

        JLabel blackTimerLabel = new JLabel(initialTime);
        JPanel blackPanel = createPlayerPanel("Black ♟", blackTimerLabel, new Color(38, 38, 50), Color.WHITE);

        JLabel whiteTimerLabel = new JLabel(initialTime);
        JPanel whitePanel = createPlayerPanel("White ♙", whiteTimerLabel, new Color(228, 228, 232), Color.BLACK);

        panel.add(blackPanel, BorderLayout.NORTH);
        panel.add(whitePanel, BorderLayout.SOUTH);

        panel.putClientProperty("blackTimer", blackTimerLabel);
        panel.putClientProperty("whiteTimer", whiteTimerLabel);
        return panel;
    }

    // ---- Right Panel (Move History) ----

    private JPanel buildRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(new EmptyBorder(12, 8, 12, 12));
        rightPanel.setBackground(new Color(22, 22, 30));
        rightPanel.setPreferredSize(new Dimension(220, 700));

        JLabel title = new JLabel("Move History", SwingConstants.CENTER);
        title.setForeground(new Color(190, 190, 215));
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setBorder(new EmptyBorder(0, 0, 8, 0));
        rightPanel.add(title, BorderLayout.NORTH);

        moveHistoryArea = new JTextArea();
        moveHistoryArea.setEditable(false);
        moveHistoryArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        moveHistoryArea.setBackground(new Color(30, 30, 42));
        moveHistoryArea.setForeground(new Color(200, 200, 215));
        moveHistoryArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        moveHistoryArea.setLineWrap(true);
        moveHistoryArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(moveHistoryArea);
        scrollPane.setBorder(new LineBorder(new Color(52, 52, 72), 1));
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        JButton exportBtn = toolbarBtn("Export PGN", new Color(55, 95, 170));
        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(new Color(22, 22, 30));
        btnPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        btnPanel.add(exportBtn);
        rightPanel.add(btnPanel, BorderLayout.SOUTH);
        exportBtn.addActionListener(e -> PGNExporter.export(moveHistoryArea, frame));

        return rightPanel;
    }

    // ---- Move History rebuild (on undo/redo/restart) ----

    private void rebuildMoveHistory() {
        moveHistoryArea.setText("");
        int fullMove = 1;
        for (Move m : board.getState().getMoveHistory()) {
            String san = sanFormatter.toSAN(m, false, false);
            if (m.piece.isWhite) {
                moveHistoryArea.append(fullMove + ". " + san + " ");
            } else {
                moveHistoryArea.append(san + "\n");
                fullMove++;
            }
        }
        moveHistoryArea.setCaretPosition(moveHistoryArea.getDocument().getLength());
    }

    // ---- Helper: styled buttons ----

    private JButton toolbarBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 10, 6, 10));
        btn.setOpaque(true);
        return btn;
    }

    private void updateLeftPanel() {
        CardLayout cl = (CardLayout) leftPanel.getLayout();
        cl.show(leftPanel, (coachingMode || visualizationMode) ? "COACHING" : "PLAY");
    }

    private JCheckBox styleCheckBox(JCheckBox cb) {
        cb.setBackground(new Color(28, 28, 38));
        cb.setForeground(new Color(220, 220, 220));
        cb.setFont(new Font("SansSerif", Font.BOLD, 12));
        cb.setFocusPainted(false);
        cb.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return cb;
    }

    private static JPanel createPlayerPanel(String name, JLabel timerLabel, Color bgColor, Color fgColor) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(bgColor);
        panel.setBorder(new LineBorder(new Color(80, 80, 100), 2, true));
        panel.setPreferredSize(new Dimension(145, 105));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        nameLabel.setForeground(fgColor);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setBorder(new EmptyBorder(8, 0, 4, 0));

        timerLabel.setFont(new Font("Consolas", Font.BOLD, 26));
        timerLabel.setForeground(fgColor);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerLabel.setBorder(new EmptyBorder(0, 0, 8, 0));

        panel.add(nameLabel);
        panel.add(timerLabel);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(new Color(22, 22, 30));
        wrapper.add(panel);
        return wrapper;
    }
}
