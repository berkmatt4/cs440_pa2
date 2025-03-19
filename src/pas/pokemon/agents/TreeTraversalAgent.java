package src.pas.pokemon.agents;


// SYSTEM IMPORTS....feel free to add your own imports here! You may need/want to import more from the .jar!
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.Stat;
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
            for(int i = 0; i < move.length; i ++){
                if (move[i].getPP() > 0) {
                    moves.add(move[i]);
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

            int myAtkStage = myActive.getStageMultiplier(Stat.ATK);
            int theirDefStage = theirActive.getStageMultiplier(Stat.DEF);
            double statModifier = (myAtkStage - theirDefStage) * 0.1;

            double statusAdv = 0.0;
            if (theirActive.getStatus() == Status.POISON || theirActive.getStatus() == Status.BURN || theirActive.getStatus() == Status.TOXIC) {
                statusAdv += 0.2;
            }
            if (myActive.getStatus() == Status.SLEEP || myActive.getStatus() == Status.FREEZE) {
                statusAdv -= 0.2;
            }

            int myRemaining = myTeam.getPokemonCount() - myTeam.getFaintedCount();
            int theirRemaining = theirTeam.getPokemonCount() - theirTeam.getFaintedCount();
            double remainingDiff = (myRemaining - theirRemaining) * 0.5;

            return hpDiff + statModifier + statusAdv + remainingDiff;
        }

        private double calculateExpectedValue(BattleView state, int depth) {
            if (depth >= maxDepth || state.isGameOver()) {
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
                            int mySpeed = myPoke.getSpeed();
                            if (myPoke.getStatus() == Status.PARALYZE) {
                                mySpeed = (int) (mySpeed * 0.75);
                            }
                            int theirSpeed = theirPoke.getSpeed();
                            if (theirPoke.getStatus() == Status.PARALYZE) {
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

            if (pokemon.getStatus() == Status.SLEEP) {
                BattleView awakeState = state.copy();
                TeamView awakeTeam = isAgent ? getMyTeamView(awakeState) : getOpponentTeamView(awakeState);
                awakeTeam.getActivePokemonView().setStatus(Status.NONE);
                List<Pair<Double, BattleView>> moveOutcomes = move.getPotentialEffects(awakeState);
                for (Pair<Double, BattleView> outcome : moveOutcomes) {
                    outcomes.add(new Pair<>(0.098 * outcome.getFirst(), outcome.getSecond()));
                }
                outcomes.add(new Pair<>(0.902, state));
            } else if (pokemon.getStatus() == Status.PARALYZE) {
                if (Math.random() < 0.25) {
                    outcomes.add(new Pair<>(1.0, state));
                } else {
                    outcomes.addAll(move.getPotentialEffects(state));
                }
            } else if (pokemon.isConfused()) {
                Move selfDamageMove = new Move.Builder()
                    .name("SelfDamage")
                    .type(Type.NORMAL)
                    .category(Move.Category.PHYSICAL)
                    .basePower(40)
                    .accuracy(null)
                    .pp(Integer.MAX_VALUE)
                    .criticalHitRatio(1)
                    .priority(0)
                    .build();
                List<Pair<Double, BattleView>> selfOutcomes = selfDamageMove.getView().getPotentialEffects(state);
                for (Pair<Double, BattleView> outcome : selfOutcomes) {
                    outcomes.add(new Pair<>(0.5 * outcome.getFirst(), outcome.getSecond()));
                }
                List<Pair<Double, BattleView>> moveOutcomes = move.getPotentialEffects(state);
                for (Pair<Double, BattleView> outcome : moveOutcomes) {
                    outcomes.add(new Pair<>(0.5 * outcome.getFirst(), outcome.getSecond()));
                }
            } else {
                outcomes.addAll(move.getPotentialEffects(state));
            }
            return outcomes;
        }

        private BattleView applyPostTurnEffects(BattleView state) {
            // Simplified post-turn effects: apply poison/burn damage, decrement status counters
            BattleView newState = state.copy();
            applyPostTurnEffectsForTeam(newState, true);
            applyPostTurnEffectsForTeam(newState, false);
            return newState;
        }

        private void applyPostTurnEffectsForTeam(BattleView state, boolean isAgent) {
            TeamView team = isAgent ? getMyTeamView(state) : getOpponentTeamView(state);
            Pokemon.PokemonView pokemon = team.getActivePokemonView();
            Status status = pokemon.getStatus();

            if (status == Status.POISON) {
                int damage = (int) (pokemon.getMaxHP() / 8.0);
                pokemon.setCurrentHP(pokemon.getCurrentHP() - damage);
            } else if (status == Status.BURN) {
                int damage = (int) (pokemon.getMaxHP() / 8.0);
                pokemon.setCurrentHP(pokemon.getCurrentHP() - damage);
            } else if (status == Status.TOXIC) {
                int toxicCount = pokemon.getToxicCounter();
                int damage = (int) (pokemon.getMaxHP() * (toxicCount / 16.0));
                pokemon.setCurrentHP(pokemon.getCurrentHP() - damage);
                pokemon.setToxicCounter(toxicCount + 1);
            }

            if (pokemon.getStatus() == Status.SLEEP || pokemon.getStatus() == Status.FREEZE) {
                int turns = pokemon.getStatusTurns();
                if (turns > 0) {
                    pokemon.setStatusTurns(turns - 1);
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
        for (int i = 0; i < myTeam.getPokemonCount(); i++) {
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
