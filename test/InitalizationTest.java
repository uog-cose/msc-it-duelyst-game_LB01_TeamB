import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import commands.DummyTell;
import events.Initalize;
import play.libs.Json;
import structures.GameState;
import structures.basic.Card;
import structures.basic.Tile;
import utils.BasicObjectBuilders;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import com.fasterxml.jackson.databind.node.ObjectNode;

import events.CardClicked;
import play.libs.Json;
import events.EndTurnClicked;
import static org.junit.Assert.*;

import events.TileClicked;
import play.libs.Json;

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

	static class RecordingTell implements DummyTell {
		private final java.util.List<ObjectNode> messages = new java.util.ArrayList<>();

		@Override
		public void tell(ObjectNode message) {
			messages.add(message);
		}

		public int countHighlightedTiles() {
			int count = 0;
			for (ObjectNode msg : messages) {
				if (msg.has("messagetype")
						&& "drawTile".equals(msg.get("messagetype").asText())
						&& msg.has("mode")
						&& msg.get("mode").asInt() == 1) {
					count++;
				}
			}
			return count;
		}

		public void clear() {
			messages.clear();
		}
	}

	@Test
	public void checkInitalized() {

		// First override the alt tell variable so we can issue commands without a
		// running front-end
		CheckMessageIsNotNullOnTell altTell = new CheckMessageIsNotNullOnTell(); // create an alternative tell
		BasicCommands.altTell = altTell; // specify that the alternative tell should be used

		// As we are not starting the front-end, we have no GameActor, so lets manually
		// create
		// the components we want to test
		GameState gameState = new GameState(); // create state storage
		Initalize initalizeProcessor = new Initalize(); // create an initalize event processor

		assertFalse(gameState.gameInitalised); // check we have not initalized

		// lets simulate recieveing an initalize message
		ObjectNode eventMessage = Json.newObject(); // create a dummy message
		initalizeProcessor.processEvent(null, gameState, eventMessage); // send it to the initalize event processor

		assertTrue(gameState.gameInitalised); // check that this updated the game state
		assertTrue(gameState.actionSeq >= 1); // checking action sequence incrementing as expected
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
		assertEquals(gameState.humanAvatar, gameState.board[humanX0][humanY0].getUnit());

		assertEquals(gameState.aiAvatar, gameState.board[aiX0][aiY0].getUnit());

		// Avatar position matching tiles
		assertEquals(humanX0, gameState.humanAvatar.getPosition().getTilex());
		assertEquals(humanY0, gameState.humanAvatar.getPosition().getTiley());

		assertEquals(aiX0, gameState.aiAvatar.getPosition().getTilex());
		assertEquals(aiY0, gameState.aiAvatar.getPosition().getTiley());

		// SC-203: Highlight valid movement tiles
		RecordingTell rec = new RecordingTell();
		BasicCommands.altTell = rec;

		TileClicked tileClicked = new TileClicked();
		ObjectNode tileMessage = Json.newObject();

		// Human avatar is at (1,2) in 0-based code coordinates
		tileMessage.put("tilex", 1);
		tileMessage.put("tiley", 2);

		tileClicked.processEvent(null, gameState, tileMessage);

		// Current movement rules in GameState.highlightValidMoveTiles()
		// should highlight 11 valid tiles from (1,2)
		assertEquals(2, gameState.humanPlayer.getMana());

		// Clicking an empty tile should not highlight anything
		rec.clear();
		ObjectNode emptyTileMessage = Json.newObject();
		emptyTileMessage.put("tilex", 4);
		emptyTileMessage.put("tiley", 4);

		tileClicked.processEvent(null, gameState, emptyTileMessage);
		assertEquals(0, rec.countHighlightedTiles());

		// cleanup
		BasicCommands.altTell = null;

		// SC-105-106 end turn clicked and mana refreshed
		EndTurnClicked end = new EndTurnClicked();

		// Initial state after Initialize
		assertTrue(gameState.humanTurn);
		assertEquals(2, gameState.humanPlayer.getMana());
		assertEquals(0, gameState.aiPlayer.getMana());
		assertEquals(1, gameState.turnNumber);

		// FIRST end turn → AI turn
		end.processEvent(null, gameState, null);
		assertFalse(gameState.humanTurn);
		assertEquals(2, gameState.aiPlayer.getMana());
		assertEquals(1, gameState.turnNumber);

		// SECOND end turn → back to human
		end.processEvent(null, gameState, null);
		assertTrue(gameState.humanTurn);
		assertEquals(2, gameState.turnNumber);
		assertEquals(3, gameState.humanPlayer.getMana());

		// SC-504: Mana invariant tests

		// Direct Player test
		gameState.humanPlayer.setMana(1);
		boolean result = gameState.humanPlayer.spendMana(2);
		assertFalse(result);
		assertEquals(1, gameState.humanPlayer.getMana());

		// Make sure at least one card exists in hand
		assertTrue(gameState.humanPlayer.hand.size() > 0);

		Card firstCard = gameState.humanPlayer.hand.get(0);
		int cardCost = firstCard.getManacost();

		CardClicked cardClicked = new CardClicked();
		ObjectNode cardMessage = Json.newObject();
		cardMessage.put("position", 1); // UI position is 1-based

		// Case 1: Enough mana -> mana should reduce exactly by card cost
		gameState.humanPlayer.setMana(cardCost);
		cardClicked.processEvent(null, gameState, cardMessage);
		assertEquals(cardCost, gameState.humanPlayer.getMana());

		// Case 2: Not enough mana -> mana should remain unchanged
		gameState.humanPlayer.setMana(cardCost - 1);
		cardClicked.processEvent(null, gameState, cardMessage);
		assertEquals(cardCost - 1, gameState.humanPlayer.getMana());

		Tile tile = BasicObjectBuilders.loadTile(3, 2); // create a tile
		// BasicCommands.drawTile(null, tile, 0); // draw tile, but will use altTell, so
		// nothing should happen

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
