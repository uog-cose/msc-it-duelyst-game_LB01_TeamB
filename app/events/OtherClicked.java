package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import structures.GameState;
import commands.BasicCommands;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case
 * somewhere that is not on a card tile or the end-turn button.
 * 
 * { 
 *   messageType = “otherClicked”
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class OtherClicked implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		if (!gameState.gameInitalised) {
			return;
		}

		if (gameState.humanAvatar != null) {
			System.out.println("[SC-403] Player clicked Surrender. Setting Avatar HP to 0.");
			
			gameState.setHealthForTarget(gameState.humanAvatar, 0); 

			gameState.syncPlayerStatsUI(out);

			BasicCommands.addPlayer1Notification(out, "You have surrendered!", 2);

			gameState.clearAllHighlights(out);
		}
		
	}

}


