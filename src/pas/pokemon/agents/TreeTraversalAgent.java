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
            PokemonView activePokemon = state.getTeamView(isAgent ? myTeamIdx : 1 - myTeamIdx).getActivePokemonView();
            
            List<MoveView> validMoves = new ArrayList<>();
            for (MoveView move : activePokemon.getMoveViews()) {
                if (move != null && move.getPP() > 0) {
                    validMoves.add(move);
                }
            }
            
            return validMoves;
        }

        private double evaluateStateRecursively(BattleView state, int depth) {
            // Base cases
            if (depth <= 0 || state.isOver()) {
                return UtilityCalculator.calculateUtility(state, myTeamIdx);
            }
            
            try {
                // Get possible moves for the opponent
                List<MoveView> opponentMoves = getPossibleMoves(state, false);
                
                // If no moves available, return current state utility
                if (opponentMoves.isEmpty()) {
                    return UtilityCalculator.calculateUtility(state, myTeamIdx);
                }
                
                // Initialize worst-case utility
                double worstCaseUtility = Double.POSITIVE_INFINITY;
                
                // Limit the number of moves to evaluate to prevent excessive computation
                int movesToEvaluate = Math.min(opponentMoves.size(), 3);
                
                for (int i = 0; i < movesToEvaluate; i++) {
                    MoveView oppMove = opponentMoves.get(i);
                    
                    // Get potential outcomes of the opponent's move
                    List<Pair<Double, BattleView>> opponentOutcomes = simulateMoveOutcomes(state, oppMove);
                    
                    // If no outcomes, skip this move
                    if (opponentOutcomes.isEmpty()) {
                        continue;
                    }
                    
                    // Evaluate each outcome
                    for (Pair<Double, BattleView> outcome : opponentOutcomes) {
                        // Limit recursive depth
                        double stateUtility = evaluateStateRecursively(outcome.getSecond(), depth - 2);
                        
                        // Update worst-case utility
                        worstCaseUtility = Math.min(worstCaseUtility, stateUtility);
                    }
                }
                
                return worstCaseUtility;
            } catch (Exception e) {
                // Fallback to basic utility calculation if something goes wrong
                System.err.println("Error in state evaluation: " + e.getMessage());
                return UtilityCalculator.calculateUtility(state, myTeamIdx);
            }
        }

        private double calculatePotentialDamage(BattleView state, MoveView move) {
            PokemonView myPokemon = state.getTeamView(myTeamIdx).getActivePokemonView();
            PokemonView enemyPokemon = state.getTeamView(1 - myTeamIdx).getActivePokemonView();
            
            double basePower = 0;
            // Consider base power, attack/defense stats, and type effectiveness
            if(move.getPower() != null){
                basePower = move.getPower();
            }
            double attackStat = myPokemon.getCurrentStat(Stat.ATK);
            double defenseStat = enemyPokemon.getCurrentStat(Stat.DEF);
            
            return basePower * (attackStat / defenseStat);
        }

        private List<Pair<Double, BattleView>> simulateMoveOutcomes(BattleView state, MoveView move) {
            try {
                // Use boolean team identification
                int myActiveIdx = state.getTeamView(0).getActivePokemonIdx();
                int opponentActiveIdx = state.getTeamView(1).getActivePokemonIdx();
                
                // Validate indices
                if (myActiveIdx < 0 || opponentActiveIdx < 0) {
                   //System.err.println("Invalid active Pokemon indices");
                    return new ArrayList<>();
                }
                
                // Fallback to standard move potential effects
                return move.getPotentialEffects(
                    state, 
                    myActiveIdx, 
                    opponentActiveIdx
                );
            } catch (Exception e) {
                //System.err.println("Critical error in simulateMoveOutcomes: " + e.getMessage());
                //e.printStackTrace();
                return new ArrayList<>();
            }
        }

        private double calculateMoveExpectedValue(BattleView state, MoveView move) {
            // Consider multiple factors in move evaluation
            //System.out.println("Pre potential damage");
            double damageScore = calculatePotentialDamage(state, move);
            //System.out.println("Post potential damage");
            
            // Simulate move outcomes and calculate expected utility
            //System.out.println("Pre simulate outcomes");
            List<Pair<Double, BattleView>> potentialOutcomes = simulateMoveOutcomes(state, move);
            //System.out.println("Post simulate outcomes");
            
            double expectedUtility = 0.0;
            for (Pair<Double, BattleView> outcome : potentialOutcomes) {
                double probability = outcome.getFirst();
                BattleView resultState = outcome.getSecond();
                
                // Recursively evaluate state to look ahead
              //  System.out.println("Pre evaluate recursivel");
                double stateUtility = evaluateStateRecursively(resultState, maxDepth - 1);
              //  System.out.println("Post evaluate recursively");

                expectedUtility += probability * (stateUtility +  
                    damageScore);
            }
            
            return expectedUtility;
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
            //System.out.println("Before get possible moves");
            List<MoveView> availableMoves = getPossibleMoves(rootView, true);
            //System.out.println("After get possible moves");
            
            if (availableMoves.isEmpty()) {
                return null;
            }

            // Advanced move selection using expected value calculation
            MoveView bestMove = null;
            double bestExpectedValue = Double.NEGATIVE_INFINITY;

            for (MoveView move : availableMoves) {
              //  System.out.println("Before calculate expected value");
                double expectedValue = calculateMoveExpectedValue(rootView, move);
               // System.out.println("After calculate expected value");
                if (expectedValue > bestExpectedValue) {
                    bestExpectedValue = expectedValue;
                    bestMove = move;
                }
            }

            return bestMove != null ? bestMove : availableMoves.get(0);
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