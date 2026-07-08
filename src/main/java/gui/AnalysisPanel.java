package gui;

import analysis.CoachingAnalyzer;
import engine.EvalBreakdown;
import engine.SearchTreeRecorder;
import engine.SearchTreeRecorder.SearchNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.util.List;

/**
 * The Analysis Panel shown on the left in Coaching Mode.
 *
 * Sections:
 *  1. Evaluation text (from EvalBreakdown.toExplainableString)
 *  2. Move classification badge (Brilliant / Blunder / etc.)
 *  3. Collapsible Search Tree view (JTree, only populated in Visualisation Mode)
 */
public class AnalysisPanel extends JPanel {

    // Eval summary
    private final JTextArea evalSummaryArea;

    // Move classification
    private final JLabel classificationLabel;
    private final JTextArea explanationArea;

    // Search tree
    private final DefaultTreeModel treeModel;
    private final JTree searchTree;
    private final JScrollPane treeScrollPane;

    private static final Color BG   = new Color(24, 24, 30);
    private static final Color CARD = new Color(35, 35, 45);
    private static final Color FG   = new Color(210, 210, 220);
    private static final Color MUTED = new Color(140, 140, 155);

    public AnalysisPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BG);
        setPreferredSize(new Dimension(240, 680));
        setBorder(new EmptyBorder(12, 10, 12, 10));

        // ---- Header ----
        JLabel header = new JLabel("Analysis");
        header.setFont(new Font("SansSerif", Font.BOLD, 16));
        header.setForeground(new Color(190, 160, 255));
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setBorder(new EmptyBorder(0, 0, 10, 0));
        add(header);

        // ---- Position Eval Card ----
        add(sectionLabel("Position"));
        evalSummaryArea = new JTextArea();
        evalSummaryArea.setEditable(false);
        evalSummaryArea.setLineWrap(true);
        evalSummaryArea.setWrapStyleWord(true);
        evalSummaryArea.setBackground(CARD);
        evalSummaryArea.setForeground(FG);
        evalSummaryArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        evalSummaryArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        evalSummaryArea.setText("Play a move to see analysis.");
        JScrollPane evalScroll = new JScrollPane(evalSummaryArea);
        evalScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 80), 1));
        evalScroll.setPreferredSize(new Dimension(220, 90));
        evalScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        evalScroll.setAlignmentX(LEFT_ALIGNMENT);
        add(evalScroll);

        add(Box.createVerticalStrut(10));

        // ---- Move Classification Card ----
        add(sectionLabel("Move Quality"));
        classificationLabel = new JLabel("—");
        classificationLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        classificationLabel.setForeground(new Color(200, 200, 200));
        classificationLabel.setAlignmentX(LEFT_ALIGNMENT);
        classificationLabel.setBorder(new EmptyBorder(5, 8, 2, 8));

        explanationArea = new JTextArea();
        explanationArea.setEditable(false);
        explanationArea.setLineWrap(true);
        explanationArea.setWrapStyleWord(true);
        explanationArea.setBackground(CARD);
        explanationArea.setForeground(FG);
        explanationArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
        explanationArea.setBorder(new EmptyBorder(6, 8, 8, 8));

        JPanel qualityCard = new JPanel();
        qualityCard.setLayout(new BoxLayout(qualityCard, BoxLayout.Y_AXIS));
        qualityCard.setBackground(CARD);
        qualityCard.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 80), 1));
        qualityCard.setAlignmentX(LEFT_ALIGNMENT);
        qualityCard.add(classificationLabel);
        qualityCard.add(explanationArea);
        qualityCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        add(qualityCard);

        add(Box.createVerticalStrut(10));

        // ---- Search Tree Section ----
        add(sectionLabel("Search Tree (Visualisation Mode)"));
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("No search yet");
        treeModel  = new DefaultTreeModel(rootNode);
        searchTree = new JTree(treeModel);
        searchTree.setBackground(CARD);
        searchTree.setForeground(FG);
        searchTree.setFont(new Font("Consolas", Font.PLAIN, 10));
        searchTree.setBorder(new EmptyBorder(4, 4, 4, 4));
        // Custom renderer to colour pruned nodes in red
        searchTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                    boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                setBackground(CARD);
                setBackgroundNonSelectionColor(CARD);
                setTextNonSelectionColor(FG);
                if (value instanceof DefaultMutableTreeNode node) {
                    if (node.getUserObject() instanceof SearchNode sn && sn.pruned) {
                        setForeground(new Color(255, 80, 80));
                    }
                }
                return this;
            }
        });

        treeScrollPane = new JScrollPane(searchTree);
        treeScrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 80), 1));
        treeScrollPane.setAlignmentX(LEFT_ALIGNMENT);
        treeScrollPane.setPreferredSize(new Dimension(220, 200));
        treeScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        add(treeScrollPane);
    }

    // ---- Public update methods ----

    /** Update the position evaluation text. Called after every move. */
    public void updateEval(EvalBreakdown breakdown) {
        SwingUtilities.invokeLater(() -> evalSummaryArea.setText(breakdown.toExplainableString()));
    }

    /** Show move quality from coaching analysis. */
    public void updateMoveQuality(CoachingAnalyzer.MoveAnalysis analysis) {
        SwingUtilities.invokeLater(() -> {
            classificationLabel.setText(analysis.classification.label);
            try {
                classificationLabel.setForeground(Color.decode(analysis.classification.color));
            } catch (NumberFormatException e) {
                classificationLabel.setForeground(FG);
            }
            explanationArea.setText(analysis.explanation);
        });
    }

    /** Populate the search tree from a recorded search. */
    public void updateSearchTree(SearchNode root) {
        SwingUtilities.invokeLater(() -> {
            if (root == null) {
                treeModel.setRoot(new DefaultMutableTreeNode("Book move (no search performed)"));
                treeModel.reload();
                return;
            }
            DefaultMutableTreeNode treeRoot = buildTreeNode(root);
            treeModel.setRoot(treeRoot);
            treeModel.reload();
            // Expand only first two levels
            for (int i = 0; i < Math.min(searchTree.getRowCount(), 20); i++) {
                searchTree.expandRow(i);
            }
        });
    }

    public void reset() {
        SwingUtilities.invokeLater(() -> {
            evalSummaryArea.setText("Play a move to see analysis.");
            classificationLabel.setText("—");
            classificationLabel.setForeground(new Color(200, 200, 200));
            explanationArea.setText("");
            treeModel.setRoot(new DefaultMutableTreeNode("No search yet"));
        });
    }

    // ---- Private helpers ----

    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text.toUpperCase());
        lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        lbl.setForeground(MUTED);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        lbl.setBorder(new EmptyBorder(0, 0, 4, 0));
        return lbl;
    }

    private DefaultMutableTreeNode buildTreeNode(SearchNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        treeNode.setUserObject(node.toSummary());
        for (SearchNode child : node.children) {
            treeNode.add(buildTreeNode(child));
        }
        return treeNode;
    }
}
