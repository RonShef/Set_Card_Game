package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Dealer dealer;
    @Mock
    protected ConcurrentLinkedQueue<Player> playerRequests;

    private Table table;
    @Mock
    private Env env;

    private Player[] players;

    private volatile boolean terminate;

    private long reshuffleTime;

    private long startTime;
    @Mock
    private Logger logger;
    @Mock
    private Util util;
    @Mock
    private UserInterface ui;

    @BeforeEach
    void setUp() {
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        table = new Table(env);
        Player player0 = new Player(env, dealer, table, 0, false);
        players = new Player[1];
        players[0] = player0;
        dealer= new Dealer(env, table, players);

    }


    @Test
    void checkTokensGood()
    {
        table.placeToken(0, 0);
        table.placeToken(0,1);
        assertEquals(false, dealer.checkTokens(players[0]));
    }

    @Test
    void checkTokensBad()
    {
        assertThrows(ArrayIndexOutOfBoundsException.class, ()->{dealer.checkTokens(players[1]);});
    }

    @Test
    void checkPlaceCards()
    {
        int oldSize = dealer.getDeckSize();
        dealer.placeCardsOnTable();
        assertEquals(oldSize-12, dealer.getDeckSize());
    }

}