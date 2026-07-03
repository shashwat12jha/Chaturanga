package gui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import model.GameState;
import model.Move;
import model.MoveType;
import model.Piece;
import model.PieceType;
import rules.DrawDetector;
import rules.MoveApplier;
import rules.MoveValidator;
import rules.MoveGenerator;
import rules.SANFormatter;
import io.FENParser;

public class BoardPanel extends JPanel {
    private final int tileSize = 85;
    private final int cols = 8;
    private final int rows = 8;
    
    private GameState state;
    private final PieceRenderer renderer;
    private final MoveValidator validator;
    private final MoveApplier applier;
    private final DrawDetector drawDetector;
    private final SANFormatter sanFormatter;
    private final MoveGenerator moveGenerator;
    private final analysis.RefutationEngine refutationEngine;
    
    private int selectedCol = -1;
    private int selectedRow = -1;
    private List<Move> legalMovesForSelected = null;
    
    // Drag and drop state
    private boolean isDragging = false;
    private int dragX = -1;
    private int dragY = -1;

    private java.util.Stack<GameState> history = new java.util.Stack<>();
    private java.util.Stack<GameState> future = new java.util.Stack<>();

    private java.util.function.Consumer<Move> onMoveListener;
    private Runnable onStateChangedListener;

    // Input lock — true = player can interact, false = engine is thinking
    private boolean inputEnabled = true;

    // The state BEFORE the last move — used by coaching analysis
    private GameState previousState = null;
    
    public BoardPanel() {
        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize));
        this.renderer = new PieceRenderer(tileSize);
        this.validator = new MoveValidator();
        this.applier = new MoveApplier();
        this.drawDetector = new DrawDetector();
        this.sanFormatter = new SANFormatter();
        this.moveGenerator = new MoveGenerator();
        this.refutationEngine = new analysis.RefutationEngine();
        this.state = FENParser.fromFEN(FENParser.STARTING_FEN);
        
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int col = e.getX() / tileSize;
                int row = e.getY() / tileSize;

                if (!inputEnabled || state.isGameOver()) return;
                
                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(col, row, e.getX(), e.getY());
                    return;
                }
                
                // If we click our own piece, we select it and start dragging
                Piece p = state.getPiece(col, row);
                if (p != null && p.isWhite == state.isWhiteToMove()) {
                    selectedCol = col;
                    selectedRow = row;
                    isDragging = true;
                    dragX = e.getX();
                    dragY = e.getY();
                    
                    // Fetch legal moves for highlights
                    legalMovesForSelected = moveGenerator.generateLegalMoves(state).stream()
                            .filter(m -> m.fromCol == selectedCol && m.fromRow == selectedRow)
                            .collect(Collectors.toList());
                            
                    repaint();
                } else if (selectedCol != -1) {
                    // Clicked somewhere else while a piece was selected (Click-to-move)
                    attemptMove(col, row);
                }
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDragging) {
                    dragX = e.getX();
                    dragY = e.getY();
                    repaint();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (isDragging) {
                    isDragging = false;
                    int col = e.getX() / tileSize;
                    int row = e.getY() / tileSize;
                    
                    if (col == selectedCol && row == selectedRow) {
                        // User just clicked without moving, leave it selected
                        repaint();
                    } else {
                        // User dragged to a new square
                        attemptMove(col, row);
                    }
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }
    
    private void handleRightClick(int col, int row, int mouseX, int mouseY) {
        if (selectedCol == -1 || selectedRow == -1 || legalMovesForSelected == null) return;
        
        Move candidate = null;
        for (Move m : legalMovesForSelected) {
            if (m.toCol == col && m.toRow == row) {
                candidate = m;
                break;
            }
        }
        
        if (candidate != null) {
            Move finalCandidate = candidate;
            JPopupMenu popup = new JPopupMenu();
            JMenuItem item = new JMenuItem("Analyzing 'Why Not This Move?'...");
            item.setEnabled(false);
            popup.add(item);
            popup.show(this, mouseX, mouseY);
            
            new Thread(() -> {
                analysis.RefutationEngine.Refutation ref = refutationEngine.refute(state, finalCandidate);
                SwingUtilities.invokeLater(() -> {
                    popup.setVisible(false);
                    JOptionPane.showMessageDialog(this, ref.explanation, "Why Not This Move?", JOptionPane.INFORMATION_MESSAGE);
                });
            }).start();
        }
    }
    
    private void attemptMove(int col, int row) {
        if (col < 0 || col > 7 || row < 0 || row > 7) {
            cancelSelection();
            return;
        }

        Piece p = state.getPiece(selectedCol, selectedRow);
        Piece cap = state.getPiece(col, row);
        
        // If clicking own piece again, just change selection
        if (cap != null && cap.isWhite == state.isWhiteToMove()) {
            selectedCol = col;
            selectedRow = row;
            legalMovesForSelected = moveGenerator.generateLegalMoves(state).stream()
                    .filter(m -> m.fromCol == selectedCol && m.fromRow == selectedRow)
                    .collect(Collectors.toList());
            repaint();
            return;
        }
        
        MoveType type = MoveType.NORMAL;
        PieceType promo = null;
        
        if (p.type == PieceType.KING && Math.abs(col - selectedCol) == 2) {
            type = col == 6 ? MoveType.KINGSIDE_CASTLE : MoveType.QUEENSIDE_CASTLE;
        } else if (p.type == PieceType.PAWN && col != selectedCol && cap == null) {
            type = MoveType.EN_PASSANT;
            cap = state.getPiece(col, selectedRow);
        } else if (p.type == PieceType.PAWN && (row == 0 || row == 7)) {
            type = MoveType.PROMOTION;
            String[] options = {"Queen", "Rook", "Bishop", "Knight"};
            int choice = JOptionPane.showOptionDialog(this, "Choose piece to promote to:",
                    "Pawn Promotion", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[0]);
            switch (choice) {
                case 1: promo = PieceType.ROOK; break;
                case 2: promo = PieceType.BISHOP; break;
                case 3: promo = PieceType.KNIGHT; break;
                default: promo = PieceType.QUEEN; break;
            }
        }
        
        Move move = new Move(selectedCol, selectedRow, col, row, p, cap, type, promo);
        
        if (validator.isLegal(move, state)) {
            previousState = state;   // snapshot BEFORE apply for coaching analysis
            history.push(state);
            future.clear();
            state = applier.apply(move, state);
            if (onMoveListener != null) {
                onMoveListener.accept(move);
            }
            checkGameOver();
            cancelSelection();
        } else {
            // Invalid move, keep selected if they might want to click somewhere else
            repaint();
        }
    }
    
    private void cancelSelection() {
        selectedCol = -1;
        selectedRow = -1;
        legalMovesForSelected = null;
        repaint();
    }
    
    public void setOnMoveListener(java.util.function.Consumer<Move> listener) {
        this.onMoveListener = listener;
    }
    
    public void setOnStateChangedListener(Runnable listener) {
        this.onStateChangedListener = listener;
    }
    
    public void undo() {
        if (!history.isEmpty()) {
            future.push(state);
            state = history.pop();
            cancelSelection();
            if (onStateChangedListener != null) onStateChangedListener.run();
        }
    }

    public void redo() {
        if (!future.isEmpty()) {
            history.push(state);
            state = future.pop();
            cancelSelection();
            if (onStateChangedListener != null) onStateChangedListener.run();
        }
    }

    public void restart() {
        history.clear();
        future.clear();
        state = FENParser.fromFEN(FENParser.STARTING_FEN);
        cancelSelection();
        if (onStateChangedListener != null) onStateChangedListener.run();
    }
    
    public void loadGame(String fen) {
        history.clear();
        future.clear();
        state = FENParser.fromFEN(fen);
        cancelSelection();
        if (onStateChangedListener != null) onStateChangedListener.run();
    }

    /** Called by the engine (from EDT) to apply a move without user interaction. */
    public void applyEngineMove(Move move) {
        previousState = state;   // snapshot before apply
        history.push(state);
        future.clear();
        state = applier.apply(move, state);
        if (onMoveListener != null) onMoveListener.accept(move);
        checkGameOver();
        repaint();
    }

    /** Enable or disable user input (used to lock board while engine thinks). */
    public void setInputEnabled(boolean enabled) {
        this.inputEnabled = enabled;
        repaint(); // redraw to show/hide thinking overlay
    }

    /** Returns the GameState snapshot just before the last applied move (for coaching). */
    public GameState getPreviousState() {
        return previousState;
    }
    
    public GameState getState() {
        return state;
    }
    
    private void checkGameOver() {
        if (moveGenerator.generateLegalMoves(state).isEmpty()) {
            state.setGameOver(true);
            boolean inCheck = new rules.CheckDetector().isKingInCheck(state.isWhiteToMove(), state);
            if (inCheck) {
                String winner = state.isWhiteToMove() ? "Black" : "White";
                JOptionPane.showMessageDialog(this, "Checkmate! " + winner + " Wins!");
            } else {
                JOptionPane.showMessageDialog(this, "Stalemate! It's a draw!");
            }
        } else if (drawDetector.isFiftyMoveRule(state)) {
            state.setGameOver(true);
            JOptionPane.showMessageDialog(this, "Draw by 50-move rule!");
        } else if (drawDetector.isThreefoldRepetition(state)) {
            state.setGameOver(true);
            JOptionPane.showMessageDialog(this, "Draw by Threefold Repetition!");
        } else if (drawDetector.isInsufficientMaterial(state)) {
            state.setGameOver(true);
            JOptionPane.showMessageDialog(this, "Draw by Insufficient Material!");
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                // Draw Board
                g2d.setColor((c + r) % 2 == 0 ? new Color(227, 198, 181) : new Color(157, 105, 53));
                g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
                
                // Highlight selected square
                if (c == selectedCol && r == selectedRow) {
                    g2d.setColor(new Color(255, 255, 100, 150));
                    g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
                }
                
                // Highlight legal moves
                if (legalMovesForSelected != null) {
                    for (Move m : legalMovesForSelected) {
                        if (m.toCol == c && m.toRow == r) {
                            if (state.getPiece(c, r) != null || m.type == MoveType.EN_PASSANT) {
                                // Draw capture ring
                                g2d.setColor(new Color(220, 50, 50, 150));
                                g2d.setStroke(new java.awt.BasicStroke(5));
                                g2d.drawOval(c * tileSize + 5, r * tileSize + 5, tileSize - 10, tileSize - 10);
                            } else {
                                // Draw normal move dot
                                g2d.setColor(new Color(50, 200, 50, 150));
                                int dotSize = tileSize / 3;
                                g2d.fillOval(c * tileSize + tileSize / 2 - dotSize / 2, 
                                             r * tileSize + tileSize / 2 - dotSize / 2, dotSize, dotSize);
                            }
                        }
                    }
                }
                
                // Draw piece, UNLESS it's the one being dragged
                Piece p = state.getPiece(c, r);
                if (p != null) {
                    if (!(isDragging && c == selectedCol && r == selectedRow)) {
                        renderer.drawPiece(g2d, p.type, p.isWhite, c * tileSize, r * tileSize);
                    }
                }
            }
        }

        // Draw the dragged piece on top of everything
        if (isDragging && selectedCol != -1 && selectedRow != -1) {
            Piece p = state.getPiece(selectedCol, selectedRow);
            if (p != null) {
                int drawX = dragX - tileSize / 2;
                int drawY = dragY - tileSize / 2;
                renderer.drawPiece(g2d, p.type, p.isWhite, drawX, drawY);
            }
        }

        // "Thinking" overlay: dim the board while engine is computing
        if (!inputEnabled) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
    }
}
