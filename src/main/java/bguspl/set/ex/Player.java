package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.sleep;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    enum State{
        RUNNING,
        WAITING,
        POINT,
        PENALTY
    }

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    final private ConcurrentLinkedQueue<Integer> q;
    final private Dealer dealer;
    private volatile State freezeFlag;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        q = new ConcurrentLinkedQueue();
        this.dealer= dealer;
        freezeFlag = State.WAITING;
    }

    public void setRunningState(){
        synchronized (dealer) {
            freezeFlag = State.RUNNING;
        }
    }

    public void setWaitingState(){
        synchronized (dealer) {
            freezeFlag = State.WAITING;
        }
    }

    public int getQSize(){
        return q.size();
    }

    public State getFreezeFlag(){
        //for testing
        return freezeFlag;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            synchronized (dealer) {
                while (freezeFlag == State.WAITING) {
                    try {
                        dealer.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            switch (freezeFlag)
            {
                case RUNNING:
                    if (!q.isEmpty()){
                        Integer slot= q.remove();

                        if(table.slotToTokens[slot][id]==0 && table.slotToCard[slot] != -1 && table.countTokens(id) < 3) {
                            table.placeToken(id, slot);

                            if (table.countTokens(id) == 3) {
                                synchronized (dealer){
                                    freezeFlag = State.WAITING;
                                    dealer.playerRequests.add(this);
                                    dealer.notifyAll();
                                }
                            }
                        }
                        else if (table.slotToTokens[slot][id]==1) {
                            table.removeToken(id, slot);
                        }
                    }
                    else{
                        synchronized (this) {
                            try {
                                wait();
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                    break;
                case PENALTY:
                    env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
                    penaltyTimer();
                    break;
                case POINT:
                    env.ui.setFreeze(id, env.config.pointFreezeMillis);
                    pointTimer();
                    break;
                default:
                    break;
            }


        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                // TODO implement player key press simulator
                keyPressed((int)(Math.random()*env.config.tableSize));
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public synchronized void keyPressed(int slot) {
        // TODO implement
        notifyAll();
        if (q.size() < 3)
            q.add(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */

    public void point() {
        // TODO implement
        synchronized (dealer) {
            freezeFlag = State.POINT;
            dealer.notifyAll();
        }
        score++;
        env.ui.setScore(this.id, score);

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, score);
    }

    /**
     * Penalize a player and perform other related actions.
     */

    public void penalty() {
        // TODO implement
        synchronized (dealer) {
            freezeFlag = State.PENALTY;
            dealer.notifyAll();
        }
    }

    public int getScore() {
        return score;
    }

    private void pointTimer()
    {
        env.ui.setFreeze(this.id, env.config.pointFreezeMillis);
        try {
            sleep(800);
        } catch (InterruptedException e) {
        }
        env.ui.setFreeze(this.id, 0);
        freezeFlag = State.RUNNING;
        q.clear();
    }

    private void penaltyTimer()
    {
        long startTime = System.currentTimeMillis();
        long countDown = env.config.penaltyFreezeMillis;
        env.ui.setFreeze(this.id, env.config.penaltyFreezeMillis);
        while (countDown > 0)
        {
            try {
                sleep(800);
            } catch (InterruptedException e) {
            }

            countDown = env.config.penaltyFreezeMillis - (System.currentTimeMillis() - startTime);
            env.ui.setFreeze(this.id, countDown);
        }
        env.ui.setFreeze(this.id, 0);
        freezeFlag = State.RUNNING;
        q.clear();
    }
}
