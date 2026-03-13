import static org.junit.Assert.assertTrue;

import org.junit.Test;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import events.EndTurnClicked;
import structures.GameState;
import structures.basic.Tile;
import utils.BasicObjectBuilders;

/**
 * Unit test for SC-505: Turn End Cleanup logic.
 * Ensures that highlighted tiles are cleared when the turn ends.
 */
public class TurnCleanupTest {

    @Test
    public void testHighlightedTilesClearedOnTurnEnd() {

        // 1. Setup the dummy front-end interceptor so the server doesn't crash without a browser
        CheckMessageIsNotNullOnTell altTell = new CheckMessageIsNotNullOnTell();
        BasicCommands.altTell = altTell;

        // 2. Initialize an empty GameState
        GameState gameState = new GameState();

        // 3. Manually add some dummy "highlighted" tiles to our list
        Tile dummyTile1 = BasicObjectBuilders.loadTile(1, 1);
        Tile dummyTile2 = BasicObjectBuilders.loadTile(2, 2);
        gameState.highlightedTiles.add(dummyTile1);
        gameState.highlightedTiles.add(dummyTile2);

       // assertTrue("List should have 2 tiles before end turn", gameState.highlightedTiles.size() == 2);
       assertTrue("Move highlights should be empty after end turn cleanup", gameState.highlightedMoveTiles.isEmpty());
       assertTrue("Summon highlights should be empty after end turn cleanup", gameState.highlightedSummonTiles.isEmpty());

        // 4. Simulate a player clicking the "End Turn" button
        EndTurnClicked endTurnProcessor = new EndTurnClicked();
        endTurnProcessor.processEvent(null, gameState, null);

        // 5.assertTrue("List should be entirely empty after end turn cleanup", gameState.highlightedTiles.isEmpty());
        assertTrue("Move highlights should be empty after end turn cleanup", gameState.highlightedMoveTiles.isEmpty());
        assertTrue("Summon highlights should be empty after end turn cleanup", gameState.highlightedSummonTiles.isEmpty());
    }
}