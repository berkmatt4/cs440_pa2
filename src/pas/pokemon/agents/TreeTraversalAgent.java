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
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.utils.Pair;


import java.io.InputStream;
import java.io.OutputStream;
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
            List<MoveView> moves = new ArrayList<>();

            MoveView move [] = active.getMoveViews();
            System.out.println("Move array: "+ move);
            for (int i = 0; i < move.length; i++) {
                if (move[i] != null && move[i].getPP() != null) { // Check for null first!
                    if (move[i].getPP() > 0) {
                        moves.add(move[i]);
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
            if (depth >= maxDepth || state.isOver()) {
                return evaluateState(state);
            }

            List<MoveView> myMoves = getPossibleMoves(state, true);
            if (myMoves.isEmpty()) {
                return Double.NEGATIVE_INFINITY;
            }

            double maxValue = Double.NEGATIVE_INFINITY;
            for (MoveView myMove : myMoves) {
                double moveValue = 0.0;
                List<MoveView> opponentMoves = getPossibleMoves(state, false);
                int numOpponentMoves = opponentMoves.size();
                if (numOpponentMoves == 0) {
                    moveValue = evaluateState(state);
                } else {
                    for (MoveView opponentMove : opponentMoves) {
                        double opponentProb = 1.0 / numOpponentMoves;

                        int myPriority = myMove.getPriority();
                        int theirPriority = opponentMove.getPriority();

                        double orderProbAgentFirst = 0.0;
                        double orderProbOpponentFirst = 0.0;

                        if (myPriority > theirPriority) {
                            orderProbAgentFirst = 1.0;
                        } else if (myPriority < theirPriority) {
                            orderProbOpponentFirst = 1.0;
                        } else {
                            Pokemon.PokemonView myPoke = getMyTeamView(state).getActivePokemonView();
                            Pokemon.PokemonView theirPoke = getOpponentTeamView(state).getActivePokemonView();
                            int mySpeed = myPoke.getCurrentStat(Stat.SPD);
                            if (myPoke.getNonVolatileStatus() == NonVolatileStatus.PARALYSIS) {
                                mySpeed = (int) (mySpeed * 0.75);
                            }
                            int theirSpeed = theirPoke.getCurrentStat(Stat.SPD);
                            if (theirPoke.getNonVolatileStatus() == NonVolatileStatus.PARALYSIS) {
                                theirSpeed = (int) (theirSpeed * 0.75);
                            }

                            if (mySpeed > theirSpeed) {
                                orderProbAgentFirst = 1.0;
                            } else if (mySpeed < theirSpeed) {
                                orderProbOpponentFirst = 1.0;
                            } else {
                                orderProbAgentFirst = 0.5;
                                orderProbOpponentFirst = 0.5;
                            }
                        }

                        if (orderProbAgentFirst > 0) {
                            List<Pair<Double, BattleView>> myOutcomes = applyMove(state, myMove, true);
                            for (Pair<Double, BattleView> myOutcome : myOutcomes) {
                                BattleView afterMyMove = myOutcome.getSecond();
                                List<Pair<Double, BattleView>> theirOutcomes = applyMove(afterMyMove, opponentMove, false);
                                for (Pair<Double, BattleView> theirOutcome : theirOutcomes) {
                                    BattleView afterBothMoves = theirOutcome.getSecond();
                                    BattleView postTurn = applyPostTurnEffects(afterBothMoves);
                                    double value = calculateExpectedValue(postTurn, depth + 1);
                                    moveValue += opponentProb * orderProbAgentFirst * myOutcome.getFirst() * theirOutcome.getFirst() * value;
                                }
                            }
                        }

                        if (orderProbOpponentFirst > 0) {
                            List<Pair<Double, BattleView>> theirOutcomes = applyMove(state, opponentMove, false);
                            for (Pair<Double, BattleView> theirOutcome : theirOutcomes) {
                                BattleView afterTheirMove = theirOutcome.getSecond();
                                List<Pair<Double, BattleView>> myOutcomes = applyMove(afterTheirMove, myMove, true);
                                for (Pair<Double, BattleView> myOutcome : myOutcomes) {
                                    BattleView afterBothMoves = myOutcome.getSecond();
                                    BattleView postTurn = applyPostTurnEffects(afterBothMoves);
                                    double value = calculateExpectedValue(postTurn, depth + 1);
                                    moveValue += opponentProb * orderProbOpponentFirst * theirOutcome.getFirst() * myOutcome.getFirst() * value;
                                }
                            }
                        }
                    }
                }
                if (moveValue > maxValue) {
                    maxValue = moveValue;
                }
            }
            return maxValue;
        }

        private List<Pair<Double, BattleView>> applyMove(BattleView state, MoveView move, boolean isAgent) {
            TeamView team = isAgent ? getMyTeamView(state) : getOpponentTeamView(state);
            Pokemon.PokemonView pokemon = team.getActivePokemonView();
            List<Pair<Double, BattleView>> outcomes = new ArrayList<>();
            Battle stateNotView = new Battle(state);

            if (pokemon.getNonVolatileStatus() == NonVolatileStatus.SLEEP) {
                Battle awakeStateNotView = stateNotView.copy();
                BattleView awakeState = new BattleView(awakeStateNotView);

                TeamView awakeTeamView = isAgent ? getMyTeamView(awakeState) : getOpponentTeamView(awakeState);
                Team awakeTeam = new Team(awakeTeamView);
                awakeTeam.getActivePokemon().setNonVolatileStatus(NonVolatileStatus.NONE);
                List<Pair<Double, BattleView>> moveOutcomes = move.getPotentialEffects(awakeState, awakeTeam.getActivePokemonIdx(), getOpponentTeamView(state).getActivePokemonIdx());
                for (Pair<Double, BattleView> outcome : moveOutcomes) {
                    outcomes.add(new Pair<>(0.098 * outcome.getFirst(), outcome.getSecond()));
                }
                outcomes.add(new Pair<>(0.902, state));
            } else if (pokemon.getNonVolatileStatus() == NonVolatileStatus.PARALYSIS) {
                if (Math.random() < 0.25) {
                    outcomes.add(new Pair<>(1.0, state));
                } else {
                    outcomes.addAll(move.getPotentialEffects(state, getMyTeamView(state).getActivePokemonIdx(), getOpponentTeamView(state).getActivePokemonIdx()));
                }
            } else if (pokemon.getFlag(Flag.CONFUSED)) {
                Move selfDamageMove = new Move(
                    "SelfDamage", 
                    Type.NORMAL, 
                    Move.Category.PHYSICAL, 
                    40,       // basePower (Integer)
                    null,     // accuracy (Integer - null indicates it never misses)
                    Integer.MAX_VALUE,  // pp (Integer)
                    1,        // criticalHitRatio (int)
                    0         // priority (int)
                );
                List<Pair<Double, BattleView>> selfOutcomes = selfDamageMove.getView().getPotentialEffects(state, getMyTeamView(state).getActivePokemonIdx(), getMyTeamView(state).getActivePokemonIdx());
                for (Pair<Double, BattleView> outcome : selfOutcomes) {
                    outcomes.add(new Pair<>(0.5 * outcome.getFirst(), outcome.getSecond()));
                }
                List<Pair<Double, BattleView>> moveOutcomes = move.getPotentialEffects(state, getMyTeamView(state).getActivePokemonIdx(), getOpponentTeamView(state).getActivePokemonIdx());
                for (Pair<Double, BattleView> outcome : moveOutcomes) {
                    outcomes.add(new Pair<>(0.5 * outcome.getFirst(), outcome.getSecond()));
                }
            } else {
                outcomes.addAll(move.getPotentialEffects(state, getMyTeamView(state).getActivePokemonIdx(), getOpponentTeamView(state).getActivePokemonIdx()));
            }
            return outcomes;
        }

        private BattleView applyPostTurnEffects(BattleView state) {
            // Simplified post-turn effects: apply poison/burn damage, decrement status counters
            Battle stateNotView = new Battle(state);
            Battle newStateNotView = stateNotView.copy();
            BattleView newState = new BattleView(newStateNotView);
            applyPostTurnEffectsForTeam(newState, true);
            applyPostTurnEffectsForTeam(newState, false);
            return newState;
        }

        private void applyPostTurnEffectsForTeam(BattleView state, boolean isAgent) {
            TeamView team = isAgent ? getMyTeamView(state) : getOpponentTeamView(state);
            Team teamNotView = new Team(team);
            Pokemon.PokemonView pokemonView = team.getActivePokemonView();
            Pokemon pokemon = teamNotView.getActivePokemon();
            NonVolatileStatus status = pokemon.getNonVolatileStatus();

            if (status == NonVolatileStatus.POISON) {
                int damage = (int) (pokemon.getBaseStat(Stat.HP) / 8.0);
                pokemon.setCurrentStat(Stat.HP, pokemon.getCurrentStat(Stat.HP) - damage);
            } else if (status == NonVolatileStatus.BURN) {
                int damage = (int) (pokemon.getBaseStat(Stat.HP) / 8.0);
                pokemon.setCurrentStat(Stat.HP, pokemon.getCurrentStat(Stat.HP) - damage);
            } else if (status == NonVolatileStatus.TOXIC) {
                int toxicCount = pokemon.getNonVolatileStatusCounter(NonVolatileStatus.TOXIC);
                pokemon.setNonVolatileStatusCounter(NonVolatileStatus.TOXIC, toxicCount + 1);
                int damage = (int) (pokemon.getBaseStat(Stat.HP) * (toxicCount / 16.0));
                pokemon.setCurrentStat(Stat.HP, pokemon.getCurrentStat(Stat.HP) - damage);
            }

            if (pokemon.getNonVolatileStatus() == NonVolatileStatus.SLEEP){ //|| pokemon.getNonVolatileStatus() == NonVolatileStatus.FREEZE) {
                int turns = pokemon.getNonVolatileStatusCounter(NonVolatileStatus.SLEEP);
                if (turns > 0) {
                    pokemon.setNonVolatileStatusCounter(NonVolatileStatus.SLEEP, turns - 1);
                }
            }
            if (pokemon.getNonVolatileStatus() == NonVolatileStatus.FREEZE){ //|| pokemon.getNonVolatileStatus() == NonVolatileStatus.FREEZE) {
                int turns = pokemon.getNonVolatileStatusCounter(NonVolatileStatus.FREEZE);
                if (turns > 0) {
                    pokemon.setNonVolatileStatusCounter(NonVolatileStatus.FREEZE, turns - 1);
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
        public MoveView stochasticTreeSearch(BattleView rootView) //, int depth)
        {
            List<MoveView> myMoves = getPossibleMoves(rootView, true);
            MoveView bestMove = null;
            double bestValue = Double.NEGATIVE_INFINITY;

            for (MoveView move : myMoves) {
                double value = calculateExpectedValue(rootView, 0);
                if (value > bestValue) {
                    bestValue = value;
                    bestMove = move;
                }
            }
            return bestMove != null ? bestMove : myMoves.get(0);
        }

        @Override
        public Pair<MoveView, Long> call() throws Exception
        {
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
        this.maxDepth = 1000; // set this however you want
    }

    /**
     * Some constants
     */
    public int getMaxDepth() { return this.maxDepth; }
    public long getMaxThinkingTimePerMoveInMS() { return this.maxThinkingTimePerMoveInMS; }

    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        // TODO: replace me! This code calculates the first-available pokemon.
        // It is likely a good idea to expand a bunch of trees with different choices as the active pokemon on your
        // team, and see which pokemon is your best choice by comparing the values of the root nodes.

        TeamView myTeam = getMyTeamView(view);
        Team team = new Team(myTeam);
        for (int i = 0; i < team.getPokemon().size(); i++) {
            if (!myTeam.getPokemonView(i).hasFainted()) {
                return i;
            }
        }
        return null;
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
}
