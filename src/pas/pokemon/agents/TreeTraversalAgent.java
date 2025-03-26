package src.pas.pokemon.agents;


// SYSTEM IMPORTS....feel free to add your own imports here! You may need/want to import more from the .jar!
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.utils.Pair;
import src.pas.pokemon.agents.GameNode;
import src.pas.pokemon.agents.UtilityCalculator;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


// JAVA PROJECT IMPORTS


public class TreeTraversalAgent
    extends Agent
{

	private class StochasticTreeSearcher
        extends Object
        implements Callable<Pair<MoveView, Long> >  // so this object can be run in a background thread
	{

        // TODO: feel free to add any fields here! If you do, you should probably modify the constructor
        // of this class and add some getters for them. If the fields you add aren't final you should add setters too!
		private final BattleView rootView;
        private final int maxDepth;
        private final int myTeamIdx;

        // If you change the parameters of the constructor, you will also have to change
        // the getMove(...) method of TreeTraversalAgent!
		public StochasticTreeSearcher(BattleView rootView, int maxDepth, int myTeamIdx)
        {
            this.rootView = rootView;
            this.maxDepth = maxDepth;
            this.myTeamIdx = myTeamIdx;
        }

        // Getter methods. Since the default fields are declared final, we don't need setters
        // but if you make any fields that aren't final you should give them setters!
		public BattleView getRootView() { return this.rootView; }
        public int getMaxDepth() { return this.maxDepth; }
        public int getMyTeamIdx() { return this.myTeamIdx; }

        private List<MoveView> getPossibleMoves(BattleView state, boolean isAgent) {
            TeamView team = isAgent ? getMyTeamView(state) : getOpponentTeamView(state);
            Pokemon.PokemonView active = team.getActivePokemonView();

            if (active.hasFainted() || active.getCurrentStat(Stat.HP) <= 0){
                return new ArrayList<>();
            }

            List<MoveView> moves = new ArrayList<>();

            MoveView move [] = active.getMoveViews();
            System.out.println("Move array: "+ move);
            for (int i = 0; i < move.length; i++) {
                if (move[i] != null && move[i].getPP() != null) { // Check for null first!
                    if (move[i].getPP() > 0 && !(move[i].getName().equals("SelfDamage"))) {
                        moves.add(move[i]);
                        System.out.println("Selected Move: "+ move[i].getName());
                        System.out.println("Post-turn HP: "+ getMyTeamView(state).getActivePokemonView().getCurrentStat(Stat.HP));
                    }
                }
            }
            return moves;
        }

        private double evaluateState(BattleView state) {
            TeamView myTeam = getMyTeamView(state);
            TeamView theirTeam = getOpponentTeamView(state);
            Pokemon.PokemonView myActive = myTeam.getActivePokemonView();
            Pokemon.PokemonView theirActive = theirTeam.getActivePokemonView();

            double myHp = myActive.getCurrentStat(Stat.HP);
            double myMaxHp = myActive.getBaseStat(Stat.HP);
            double theirHp = theirActive.getCurrentStat(Stat.HP);
            double theirMaxHp = theirActive.getBaseStat(Stat.HP);

            double hpDiff = (myHp / myMaxHp) - (theirHp / theirMaxHp);

            int myAtkStage = myActive.getStatMultiplier(Stat.ATK);
            int theirDefStage = theirActive.getStatMultiplier(Stat.DEF);
            double statModifier = (myAtkStage - theirDefStage) * 0.1;

            double statusAdv = 0.0;
            if (theirActive.getNonVolatileStatus() == NonVolatileStatus.POISON || theirActive.getNonVolatileStatus() == NonVolatileStatus.BURN || theirActive.getNonVolatileStatus() == NonVolatileStatus.TOXIC) {
                statusAdv += 0.2;
            }
            if (myActive.getNonVolatileStatus() == NonVolatileStatus.SLEEP || myActive.getNonVolatileStatus() == NonVolatileStatus.FREEZE) {
                statusAdv -= 0.2;
            }

            Team myTeamNotView = new Team(myTeam);
            Team otherTeam = new Team(theirTeam);

            int myRemaining = myTeamNotView.getNumAlivePokemon();
            int theirRemaining = otherTeam.getNumAlivePokemon();
            double remainingDiff = (myRemaining - theirRemaining) * 0.5;

            return hpDiff + statModifier + statusAdv + remainingDiff;
        }

        private double calculateExpectedValue(BattleView state, int depth) {
            if (getMyTeamView(state).getActivePokemonView().hasFainted()) {
                return -1000.0; // Penalize fainted state heavily
            }

            if (depth >= maxDepth || state.isOver() || getMyTeamView(state).getActivePokemonView().hasFainted()) {
                return evaluateState(state);
            }
        
            List<MoveView> myMoves = getPossibleMoves(state, true);
            if (myMoves.isEmpty()) return Double.NEGATIVE_INFINITY;
        
            double maxValue = Double.NEGATIVE_INFINITY;
            for (MoveView myMove : myMoves) {
                double moveValue = 0.0;
                List<Pair<Double, BattleView>> agentOutcomes = applyMove(state, myMove, true);
                for (Pair<Double, BattleView> agentOutcome : agentOutcomes) {
                    BattleView afterAgentMove = agentOutcome.getSecond();
                    List<MoveView> opponentMoves = getPossibleMoves(afterAgentMove, false);
                    if (opponentMoves.isEmpty()) {
                        moveValue += agentOutcome.getFirst() * evaluateState(afterAgentMove);
                        continue;
                    }
                    double opponentValue = 0.0;
                    for (MoveView oppMove : opponentMoves) {
                        List<Pair<Double, BattleView>> oppOutcomes = applyMove(afterAgentMove, oppMove, false);
                        for (Pair<Double, BattleView> oppOutcome : oppOutcomes) {
                            BattleView afterOppMove = oppOutcome.getSecond();
                            BattleView postTurn = applyPostTurnEffects(afterOppMove);
                            double value = calculateExpectedValue(postTurn, depth + 1);
                            opponentValue += (1.0 / opponentMoves.size()) * oppOutcome.getFirst() * value;
                        }
                    }
                    moveValue += agentOutcome.getFirst() * opponentValue;
                }
                if (moveValue > maxValue) {
                    maxValue = moveValue;
                }
            }
            return maxValue;
        }

        private List<Pair<Double, BattleView>> applyMove(BattleView state, MoveView move, boolean isAgent) {
            // Clone the state before modification
            Battle clonedState = new Battle(state); // Use existing Battle constructor to copy
            BattleView clonedStateView = new BattleView(clonedState);
        
            TeamView team = isAgent ? getMyTeamView(clonedStateView) : getOpponentTeamView(clonedStateView);
            Pokemon.PokemonView active = team.getActivePokemonView();
        
            List<Pair<Double, BattleView>> outcomes = new ArrayList<>();
        
            // Handle status effects on the CLONED state
            if (active.getNonVolatileStatus() == NonVolatileStatus.SLEEP) {
                // Modify the cloned state, not the original
                Team clonedTeam = new Team(team);
                clonedTeam.getActivePokemon().setNonVolatileStatus(NonVolatileStatus.NONE);
                outcomes.addAll(move.getPotentialEffects(clonedStateView, clonedTeam.getActivePokemonIdx(), getOpponentTeamView(clonedStateView).getActivePokemonIdx()));
            } else {
                outcomes.addAll(move.getPotentialEffects(clonedStateView, team.getActivePokemonIdx(), getOpponentTeamView(clonedStateView).getActivePokemonIdx()));
            }
        
            return outcomes;
        }

        private BattleView applyPostTurnEffects(BattleView state) {
            
            Battle clonedState = new Battle(state);
            System.out.println("[DEBUG] Cloned turn (before): " + clonedState.getTurnNumber());
            
            applyPostTurnEffectsForTeam(clonedState, true);
            applyPostTurnEffectsForTeam(clonedState, false);
            
            clonedState.nextTurn(); // Advance the turn
            System.out.println("[DEBUG] Cloned turn (after): " + clonedState.getTurnNumber());
            
            return clonedState.getView();
        }

        private void applyPostTurnEffectsForTeam(Battle battle, boolean isAgent) {
            BattleView state = new BattleView(battle);
            TeamView teamView = isAgent ? getMyTeamView(state) : getOpponentTeamView(state);
            Team team = new Team(teamView);
            Pokemon activePokemon = team.getActivePokemon();
        
            NonVolatileStatus status = activePokemon.getNonVolatileStatus();
        
            // Apply status-based damage (POISON, BURN, TOXIC)
            switch (status) {
                case POISON:
                    int poisonDamage = (int) (activePokemon.getBaseStat(Stat.HP) / 8);
                    activePokemon.setCurrentStat(Stat.HP, activePokemon.getCurrentStat(Stat.HP) - poisonDamage);
                    break;
                case BURN:
                    int burnDamage = (int) (activePokemon.getBaseStat(Stat.HP) / 8);
                    activePokemon.setCurrentStat(Stat.HP, activePokemon.getCurrentStat(Stat.HP) - burnDamage);
                    break;
                case TOXIC:
                    int toxicCounter = activePokemon.getNonVolatileStatusCounter(NonVolatileStatus.TOXIC);
                    int toxicDamage = (int) (activePokemon.getBaseStat(Stat.HP) * (toxicCounter / 16.0));
                    activePokemon.setCurrentStat(Stat.HP, activePokemon.getCurrentStat(Stat.HP) - toxicDamage);
                    activePokemon.setNonVolatileStatusCounter(NonVolatileStatus.TOXIC, toxicCounter + 1);
                    break;
            }
        
            // Decrement status counters for SLEEP/FREEZE
            if (status == NonVolatileStatus.SLEEP || status == NonVolatileStatus.FREEZE) {
                int turns = activePokemon.getNonVolatileStatusCounter(status);
                if (turns > 0) {
                    activePokemon.setNonVolatileStatusCounter(status, turns - 1);
                }
            }
        }

		/**
		 * TODO: implement me!
		 * This method should perform your tree-search from the root of the entire tree.
         * You are welcome to add any extra parameters that you want! If you do, you will also have to change
         * The call method in this class!
		 * @param node the node to perform the search on (i.e. the root of the entire tree)
		 * @return The MoveView that your agent should execute
		 */

         public MoveView stochasticTreeSearch(BattleView rootView) {
            // Log initial state for debugging
            System.out.println("Starting stochastic tree search");          
            
            try {
                // Get our active Pokémon
                PokemonView activePokemon = rootView.getTeamView(this.getMyTeamIdx()).getActivePokemonView();
                
                // Get available moves
                List<MoveView> availableMoves = new ArrayList<>();
                
                // Try to get moves (may need to adjust method names)
                try {
                    MoveView[] moves = activePokemon.getMoveViews(); 
                    for (int j = 0; j < moves.length; j++){
                        availableMoves.add(moves[j]);
                        System.out.println("Found move: " + moves[j].getName());
                    }
                } catch (Exception e) {
                    System.out.println("Error getting moves: " + e.getMessage());
                }
                
                if (availableMoves.isEmpty()) {
                    System.out.println("No moves available!");
                    return null;
                }
                
                // For this minimal version, just return the first move
                return availableMoves.get(3);
                
            } catch (Exception e) {
                System.out.println("Exception in stochasticTreeSearch: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }


        @Override
        public Pair<MoveView, Long> call() throws Exception {
            double startTime = System.nanoTime();
            MoveView move = this.stochasticTreeSearch(this.getRootView());
            double endTime = System.nanoTime();
            return new Pair<MoveView, Long>(move, (long)((endTime-startTime)/1000000));
        }
		
	}

	private final int maxDepth;
    private long maxThinkingTimePerMoveInMS;

	public TreeTraversalAgent()
    {
        super();
        this.maxThinkingTimePerMoveInMS = 180000 * 2; // 6 min/move
        this.maxDepth = 5; // set this however you want
    }

    /**
     * Some constants
     */
    public int getMaxDepth() { return this.maxDepth; }
    public long getMaxThinkingTimePerMoveInMS() { return this.maxThinkingTimePerMoveInMS; }

    @Override
    public Integer chooseNextPokemon(BattleView view) {
        // Simply choose the first non-fainted Pokémon
        for (int idx = 0; idx < this.getMyTeamView(view).size(); ++idx) {
            if (!this.getMyTeamView(view).getPokemonView(idx).hasFainted()) {
                return idx;
            }
        }
        return null; // No available Pokémon
    }


    /**
     * This method is responsible for getting a move selected via the minimax algorithm.
     * There is some setup for this to work, namely making sure the agent doesn't run out of time.
     * Please do not modify.
     */
    @Override
    public MoveView getMove(BattleView battleView)
    {

        // will run the minimax algorithm in a background thread with a timeout
        ExecutorService backgroundThreadManager = Executors.newSingleThreadExecutor();

        // preallocate so we don't spend precious time doing it when we are recording duration
        MoveView move = null;
        long durationInMs = 0;

        // this obj will run in the background
        StochasticTreeSearcher searcherObject = new StochasticTreeSearcher(
            battleView,
            this.getMaxDepth(),
            this.getMyTeamIdx()
        );

        // submit the job
        Future<Pair<MoveView, Long> > future = backgroundThreadManager.submit(searcherObject);

        try
        {
            // set the timeout
            Pair<MoveView, Long> moveAndDuration = future.get(
                this.getMaxThinkingTimePerMoveInMS(),
                TimeUnit.MILLISECONDS
            );

            // if we get here the move was chosen quick enough! :)
            move = moveAndDuration.getFirst();
            durationInMs = moveAndDuration.getSecond();

            // convert the move into a text form (algebraic notation) and stream it somewhere
            // Streamer.getStreamer(this.getFilePath()).streamMove(move, Planner.getPlanner().getGame());
        } catch(TimeoutException e)
        {
            // timeout = out of time...you lose!
            System.err.println("Timeout!");
            System.err.println("Team [" + (this.getMyTeamIdx()+1) + " loses!");
            System.exit(-1);
        } catch(InterruptedException e)
        {
            e.printStackTrace();
            System.exit(-1);
        } catch(ExecutionException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        return move;
    }


    // Utility method for debugging
    private void printPokemonInfo(PokemonView pokemon) {
        try {
            System.out.println("Pokemon: " + pokemon.getName());
            System.out.println("  HP: " + pokemon.getBaseStat(Stat.HP) + "/ __"); //does not print MAX HP only current
            
            System.out.println("  Moves:");
            MoveView[] moves = pokemon.getMoveViews(); 
            for (int j = 0; j < moves.length; j++){
                System.out.println("    " + moves[j]); 
            }

        } catch (Exception e) {
            System.out.println("Error printing Pokemon info: " + e.getMessage());
        }
    }


}