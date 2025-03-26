
package src.pas.pokemon.agents;

import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.utils.Pair;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;

import java.util.ArrayList;
import java.util.List;

public class GameNode {
    // Node types
    public enum NodeType {
        MAX,     // Our turn
        MIN,     // Opponent's turn
        CHANCE   // Random event
    }

    // Basic fields
    private BattleView battleView;
    private NodeType type;
    private int depth;
    private MoveView lastMove;
    private double probability;
    private double utilityValue;
    private int myTeamIdx;

    // Constructor
    public GameNode(BattleView battleView, NodeType type, int depth, 
                   MoveView lastMove, double probability, int myTeamIdx) {
        this.battleView = battleView;
        this.type = type;
        this.depth = depth;
        this.lastMove = lastMove;
        this.probability = probability;
        this.myTeamIdx = myTeamIdx;
        this.utilityValue = 0.0;
    }

    // Getters
    public BattleView getBattleView() { return battleView; }
    public NodeType getType() { return type; }
    public int getDepth() { return depth; }
    public MoveView getLastMove() { return lastMove; }
    public double getProbability() { return probability; }
    public double getUtilityValue() { return utilityValue; }
    public int getMyTeamIdx() { return myTeamIdx; }

    // Setter
    public void setUtilityValue(double utilityValue) {
        this.utilityValue = utilityValue;
    }

    // Check if this is a terminal node
    public boolean isTerminal() {
        // Game is over if either team has no Pokémon left
        boolean ourTeamAllFainted = true;
        boolean theirTeamAllFainted = true;
        
        for (int i = 0; i < battleView.getTeamView(myTeamIdx).size(); i++) {
            if (!battleView.getTeamView(myTeamIdx).getPokemonView(i).hasFainted()) {
                ourTeamAllFainted = false;
                break;
            }
        }
        
        for (int i = 0; i < battleView.getTeamView(1 - myTeamIdx).size(); i++) {
            if (!battleView.getTeamView(1 - myTeamIdx).getPokemonView(i).hasFainted()) {
                theirTeamAllFainted = false;
                break;
            }
        }
        
        return ourTeamAllFainted || theirTeamAllFainted;
    }

    // Get children nodes (simplified version)
    public List<GameNode> getChildren() {
        List<GameNode> children = new ArrayList<>();
        
        if (isTerminal()) {
            return children;
        }
        
        // For simplicity, we'll just generate a few random children
        // In a real implementation, this would follow game rules
        if (type == NodeType.MAX) {
            // Our turn - generate MAX nodes for our moves
            List<MoveView> moves = battleView.getTeam1View().getActivePokemonView().getAvailableMoves();
            for (MoveView move : moves) {
                GameNode child = new GameNode(
                    battleView,
                    NodeType.MIN,
                    depth + 1,
                    move,
                    1.0,
                    myTeamIdx
                );
                children.add(child);
            }
        } else if (type == NodeType.MIN) {
            // Opponent's turn - generate MIN nodes for their moves
            List<MoveView> moves = battleView.getTeam2View().getActivePokemonView().getAvailableMoves();
            for (MoveView move : moves) {
                GameNode child = new GameNode(
                    battleView,
                    NodeType.MAX,
                    depth + 1,
                    move,
                    1.0,
                    myTeamIdx
                );
                children.add(child);
            }
        }
        
        return children;
    }
    

}