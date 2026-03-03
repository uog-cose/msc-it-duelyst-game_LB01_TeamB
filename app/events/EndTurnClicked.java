package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case
 * the end-turn button.
 * 
 * { 
 *   messageType = “endTurnClicked”
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class EndTurnClicked implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		

		System.out.println("END TURN CLICKED: humanTurn was " + gameState.humanTurn + " turn=" + gameState.turnNumber);
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
			if (out != null) {
				BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
			}
		} else {
			gameState.aiPlayer.setMana(refreshedMana);
			if (out != null) {
				BasicCommands.setPlayer2Mana(out, gameState.aiPlayer);
			}
		}
	}

}
