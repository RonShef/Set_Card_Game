package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Thread.interrupted;
import static java.lang.Thread.sleep;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    private final Thread[] playerThreads;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime;
    private long startTime;
    protected ConcurrentLinkedQueue<Player> playerRequests;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        this.playerThreads = new Thread[players.length];
        reshuffleTime = env.config.turnTimeoutMillis;
        startTime = System.currentTimeMillis();
        playerRequests = new ConcurrentLinkedQueue<Player>();
    }


    //method is for testing only
    public int getDeckSize(){
        return deck.size();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        for( int i=0 ;i< players.length; i++){
            playerThreads[i] = new Thread(players[i], "player" + i);
            playerThreads[i].start();
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            runPlayers();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        for (Thread thread: playerThreads){
            try{
                thread.join();
            }catch (InterruptedException e){
            }
        }
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        startTime = System.currentTimeMillis();
        while (!terminate && System.currentTimeMillis() - startTime < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            assureSet();
            placeCardsOnTable();
            isTableClean();
        }
    }

    private void isTableClean()
    {
        boolean clean = true;
        for (int slot: table.slotToCard){
            if (slot != -1) {
                clean = false;
                break;
            }
        }

        if (clean)
            terminate = true;
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        synchronized (this){
            for (Player p: players){
                p.setRunningState();
                synchronized (p){
                    p.notifyAll();
                }
            }
            notifyAll();
        }

        for(int i=0 ; i< players.length; i++){
            players[i].terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(this.deck, 1).size() == 0;
    }

    /**
     * Checks if any cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(List<Integer> toRemove) {
        // TODO implement
        for (int card: toRemove){
            if(card != -1 && table.cardToSlot[card] !=-1)
                table.removeCard(table.cardToSlot[card]);
        }
    }

    private void assureSet()
    {
        //check Q, get player tokens and use func
        if (!playerRequests.isEmpty())
        {
            Player p= playerRequests.remove();
            if(checkTokens(p)){
                int[] tokens = new int[3];
                int idx = 0;
                boolean legal = false;
                for(int i = 0; i < table.playerTokensToSlots[p.id].length && !legal; i++) {
                    if (table.playerTokensToSlots[p.id][i] == 1){
                        tokens[idx] = table.slotToCard[i];
                        idx++;
                    }
                }
                legal = env.util.testSet(tokens);

                if (legal){
                    p.point();
                    ArrayList<Integer> list= new ArrayList<Integer>(tokens.length);
                    for( int  i = 0 ; i<tokens.length; i++){
                        list.add(tokens[i]);
                    }
                    removeCardsFromTable(list);
                    updateTimerDisplay(true);
                }
                else{
                    p.penalty();
                }
            }
            else{
                synchronized (this) {
                    p.setRunningState();
                    notifyAll();
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    //method is public for testing only
    public void placeCardsOnTable() {
        // TODO implement
        Collections.shuffle(deck);
        for(int i=0; i< table.slotToCard.length; i++){
            if(table.slotToCard[i]==-1 && !deck.isEmpty()) {
                int card = deck.remove(0);
                if(card!=-1)
                    table.placeCard(card, i);
            }
        }
    }
    private void runPlayers(){
        synchronized (this) {
            for (Player p : players) {
                p.setRunningState();
            }
            notifyAll();
        }
    }


    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
        // TODO implement
            try{
                wait(10);
            } catch (InterruptedException e) {
            }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        long timer;
        if(reset) {
            this.startTime = System.currentTimeMillis();
            timer = reshuffleTime;
        }
        else{
            //update timer
            timer = reshuffleTime - (System.currentTimeMillis() - this.startTime);
        }

        env.ui.setCountdown(timer, timer <= env.config.turnTimeoutWarningMillis);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        for( int i = 0 ;i< table.slotToCard.length; i++){
            int card = table.slotToCard[i];
            table.removeCard(i);
            if (card != -1)
                deck.add(card);
        }
        for (Player p : players) {
            synchronized (this) {
                p.setWaitingState();
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        List<Integer> options = new ArrayList<>();
        int maxScore = players[0].getScore();
        for(int i=1 ; i< players.length; i++){
            if(maxScore< players[i].getScore()){
                maxScore = players[i].getScore();
            }
        }
        for(int i=0 ; i<players.length; i++){
            if(players[i].getScore()== maxScore)
                options.add(players[i].id);
        }
        int[] winners = new int[options.size()];
        for(int  i =0 ; i< winners.length; i++){
            winners[i] = options.get(i);
        }
        env.ui.announceWinner(winners);
    }

    //the method is public for testing only
    public boolean checkTokens(Player p)
    {
        int count = 0;
        for( int i= 0 ;i< table.playerTokensToSlots[p.id].length; i++) {
            if (table.playerTokensToSlots[p.id][i] == 1){
                count++;
                if (table.slotToCard[i] == -1)
                    return false;
            }
        }
        return (count == 3);
    }
}
