package events;


import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import structures.GameState;
import commands.BasicCommands;
import structures.basic.Card;
/**
 * Indicates that the user has clicked an object on the game canvas, in this case a card.
 * The event returns the position in the player's hand the card resides within.
 * 
 * { 
 *   messageType = “cardClicked”
 *   position = <hand index position [1-6]>
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class CardClicked implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		
		int handPosition = message.get("position").asInt();

		Card selectedCard = gameState.humanPlayer.hand.get(handPosition - 1);
		int cardCost = selectedCard.getManacost();

		boolean success = gameState.humanPlayer.spendMana(cardCost);

		if (!success) {
			BasicCommands.addPlayer1Notification(out, "Not enough mana!", 2);
			return;
		}
	}
}



