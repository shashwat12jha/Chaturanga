package engine;

import model.Move;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the nodes visited during a search for use in the Search Tree Visualizer.
 * Each node stores its depth, score, alpha/beta bounds, whether it was pruned, and children.
 */
public class SearchTreeRecorder {

    public static class SearchNode {
        public final String moveLabel;    // e.g. "e2-e4" or "root"
        public final int depth;
        public final int alpha;
        public final int beta;
        public int score;                 // Set after evaluation
        public boolean pruned;
        public final List<SearchNode> children = new ArrayList<>();

        public SearchNode(String moveLabel, int depth, int alpha, int beta) {
            this.moveLabel = moveLabel;
            this.depth     = depth;
            this.alpha     = alpha;
            this.beta      = beta;
            this.score     = 0;
            this.pruned    = false;
        }

        public String toSummary() {
            return String.format("[d%d] %s  score=%+d  α=%+d  β=%+d%s",
                    depth, moveLabel, score, alpha, beta, pruned ? "  [PRUNED]" : "");
        }
    }

    private SearchNode root;
    private final java.util.Deque<SearchNode> stack = new java.util.ArrayDeque<>();
    private boolean recording;

    public void startRecording(String rootLabel, int alpha, int beta) {
        root = new SearchNode(rootLabel, 0, alpha, beta);
        stack.clear();
        stack.push(root);
        recording = true;
    }

    public void stopRecording() {
        recording = false;
    }

    public boolean isRecording() {
        return recording;
    }

    /** Called when the engine enters a new node. */
    public void enterNode(Move move, int depth, int alpha, int beta) {
        if (!recording) return;
        String label = moveToLabel(move);
        SearchNode node = new SearchNode(label, depth, alpha, beta);
        if (!stack.isEmpty()) {
            stack.peek().children.add(node);
        }
        stack.push(node);
    }

    /** Called when the engine exits a node with its computed score. */
    public void exitNode(int score, boolean pruned) {
        if (!recording || stack.size() <= 1) return; // Never pop root
        SearchNode node = stack.pop();
        node.score  = score;
        node.pruned = pruned;
    }

    public SearchNode getRoot() {
        return root;
    }

    private String moveToLabel(Move move) {
        if (move == null) return "null";
        char fromFile = (char)('a' + move.fromCol);
        char toFile   = (char)('a' + move.toCol);
        int fromRank  = 8 - move.fromRow;
        int toRank    = 8 - move.toRow;
        return "" + fromFile + fromRank + "-" + toFile + toRank;
    }
}
