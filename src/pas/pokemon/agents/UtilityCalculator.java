package src.pas.pokemon.agents;

import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Stat;

public class UtilityCalculator {
    
    /**
     * Calculate the utility value of a battle state
     * This is a simplified version focusing on HP difference
     */
    public static double calculateUtility(BattleView battleView, int myTeamIdx) {
        // If terminal state, check winner
        if (isGameOver(battleView)) {
            if (getWinner(battleView) == myTeamIdx) {
                return 10000.0; // We won
            } else {
                return -10000.0; // We lost
            }
        }
        
        // Simple heuristic based on HP difference
        double myHP = getTotalTeamHP(battleView, myTeamIdx);
        double opponentHP = getTotalTeamHP(battleView, 1 - myTeamIdx);
        
        return myHP - opponentHP;
    }
    
    // Helper methods
    
    private static boolean isGameOver(BattleView battleView) {
        boolean team0AllFainted = allPokemonFainted(battleView, 0);
        boolean team1AllFainted = allPokemonFainted(battleView, 1);
        return team0AllFainted || team1AllFainted;
    }
    
    private static int getWinner(BattleView battleView) {
        boolean team0AllFainted = allPokemonFainted(battleView, 0);
        boolean team1AllFainted = allPokemonFainted(battleView, 1);
        
        if (team0AllFainted) return 1;
        if (team1AllFainted) return 0;
        return -1; // No winner yet
    }
    
    private static boolean allPokemonFainted(BattleView battleView, int teamIdx) {
        for (int i = 0; i < battleView.getTeamView(teamIdx).size(); i++) {
            if (!battleView.getTeamView(teamIdx).getPokemonView(i).hasFainted()) {
                return false;
            }
        }
        return true;
    }
    
    private static double getTotalTeamHP(BattleView battleView, int teamIdx) {
        double totalHP = 0;
        
        for (int i = 0; i < battleView.getTeamView(teamIdx).size(); i++) {
            PokemonView pokemon = battleView.getTeamView(teamIdx).getPokemonView(i);
            
            // Try different methods for getting HP
            try {
                // Add current HP
                totalHP += pokemon.getBaseStat(Stat.HP); // Change method name if needed
            } catch (Exception e) {
                // If that fails, try an alternative
                System.out.println("Could not get Pokémon HP");
            }
        }
        
        return totalHP;
    }
}