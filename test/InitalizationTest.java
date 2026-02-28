import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import events.Initalize;
import play.libs.Json;
import structures.GameState;
import structures.basic.Tile;
import utils.BasicObjectBuilders;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;


/**
 * This is an example of a JUnit test. In this case, we want to be able to test
 * the logic
 * of our system without needing to actually start the web server. We do this by
 * overriding
 * the altTell method in BasicCommands, which means whenever a command would
 * normally be sent
 * to the front-end it is instead discarded. We can manually simulate messages
 * coming from the
 * front-end by calling the processEvent method on the appropriate event
 * processor.
 * 
 * @author Richard
 *
 */
public class InitalizationTest {

	/**
	 * This test simply checks that a boolean vairable is set in GameState when we
	 * call the
	 * initalize method for illustration.
	 */
	@Test
	public void checkInitalized() {

		// First override the alt tell variable so we can issue commands without a running front-end
		CheckMessageIsNotNullOnTell altTell = new CheckMessageIsNotNullOnTell(); // create an alternative tell
		BasicCommands.altTell = altTell; // specify that the alternative tell should be used

		// As we are not starting the front-end, we have no GameActor, so lets manually create
		// the components we want to test
		GameState gameState = new GameState(); // create state storage
		Initalize initalizeProcessor = new Initalize(); // create an initalize event processor

		assertFalse(gameState.gameInitalised); // check we have not initalized

		// lets simulate recieveing an initalize message
		ObjectNode eventMessage = Json.newObject(); // create a dummy message
		initalizeProcessor.processEvent(null, gameState, eventMessage); // send it to the initalize event processor

		assertTrue(gameState.gameInitalised); // check that this updated the game state
		assertTrue(gameState.actionSeq >= 1); //checking action sequence incrementing as expected
		// [SC-102] Check that the 9x5 board was created and filled with Tiles
		assertTrue(gameState.board != null);
		assertTrue(gameState.board.length == 9);
		assertTrue(gameState.board[0].length == 5);

		// SC-103: Avatar + HP unit tests

		// HP must be 20
		assertEquals(20, gameState.humanPlayer.getHealth());
		assertEquals(20, gameState.aiPlayer.getHealth());

		// Avatars must exist
		assertNotNull(gameState.humanAvatar);
		assertNotNull(gameState.aiAvatar);

		// 1-based rule: Human at (2,3)
		int humanX0 = 2 - 1; // 1
		int humanY0 = 3 - 1; // 2

		// Mirror across 9x5 (0-based)
		int aiX0 = 8 - humanX0;
		int aiY0 = 4 - humanY0;

		// Tiles exist
		assertNotNull(gameState.board[humanX0][humanY0]);
		assertNotNull(gameState.board[aiX0][aiY0]);

		// Tiles containing avatars
		assertEquals(gameState.humanAvatar,gameState.board[humanX0][humanY0].getUnit());

		assertEquals(gameState.aiAvatar,gameState.board[aiX0][aiY0].getUnit());

		// Avatar position matching tiles
		assertEquals(humanX0, gameState.humanAvatar.getPosition().getTilex());
		assertEquals(humanY0,gameState.humanAvatar.getPosition().getTiley());

		assertEquals(aiX0,gameState.aiAvatar.getPosition().getTilex());
		assertEquals(aiY0,gameState.aiAvatar.getPosition().getTiley());

		
		Tile tile = BasicObjectBuilders.loadTile(3, 2); // create a tile
		BasicCommands.drawTile(null, tile, 0); // draw tile, but will use altTell, so nothing should happen

	}
    @Test
    public void gameState_should_persist_across_multiple_event_processors() {

        CheckMessageIsNotNullOnTell altTell = new CheckMessageIsNotNullOnTell();
        BasicCommands.altTell = altTell;

        GameState gameState = new GameState();
        assertNotNull(gameState);

        GameState firstRef = gameState;

        ObjectNode heartbeatMsg = Json.newObject();
        new events.Heartbeat().processEvent(null, gameState, heartbeatMsg);

        ObjectNode otherClickedMsg = Json.newObject();
        new events.OtherClicked().processEvent(null, gameState, otherClickedMsg);

        assertSame(firstRef, gameState);
    }

	
}
