package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;
import structures.basic.Tile; // Required to wipe the board tiles

/**
 * Indicates that the user has clicked an object on the game canvas, in this case
 * the end-turn button.
 * * {
 * messageType = “endTurnClicked”
 * }
 * * @author Dr. Richard McCreadie
 *
 */
public class EndTurnClicked implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		System.out.println("END TURN CLICKED: humanTurn was " + gameState.humanTurn + " turn=" + gameState.turnNumber);

		// ==========================================
		// [SC-505] Turn End Cleanup / State Reset
		// ==========================================
		// Wipe the board clean: loop through the 9x5 grid and remove all highlights (mode 0)
		for (int x = 0; x < gameState.board.length; x++) {
			for (int y = 0; y < gameState.board[x].length; y++) {
				Tile tile = gameState.board[x][y];
				if (tile != null) {
					// Mode 0 is the default unhighlighted state for tiles
					BasicCommands.drawTile(out, tile, 0);
				}
			}
		}
		// ==========================================

		// SC-107: Draw & Burn logic before switching turns
		if (gameState.humanTurn) {
			if (gameState.humanPlayer.deck != null && !gameState.humanPlayer.deck.isEmpty()) {
				Card drawnCard = gameState.humanPlayer.deck.remove(0);

				if (gameState.humanPlayer.hand.size() < 6) {
					gameState.humanPlayer.hand.add(drawnCard);
					int newPosition = gameState.humanPlayer.hand.size();
					BasicCommands.drawCard(out, drawnCard, newPosition, 0);
				} else {
					BasicCommands.addPlayer1Notification(out, "Hand Full! Card Burned!", 2);
				}
			} else {
				BasicCommands.addPlayer1Notification(out, "Deck is Empty!", 2);
			}
		}

		// SC-502: sequential event marker
		gameState.actionSeq++;

		// SC-105: switch active player
		gameState.humanTurn = !gameState.humanTurn;

		// advance turn counter only when human finishes
		if (gameState.humanTurn) {
			gameState.turnNumber++;
		}

		// SC-106: refresh mana at start of active player's turn
		int refreshedMana = Math.min(9, gameState.turnNumber + 1);

		if (gameState.humanTurn) {
			gameState.humanPlayer.setMana(refreshedMana);
		} else {
			gameState.aiPlayer.setMana(refreshedMana);
		}

		// SC-402: Use new utility to sync UI easily
		if (out != null) {
			gameState.syncPlayerStatsUI(out);
		}
	}

}