package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;

public class CardClicked implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		int handPosition = message.get("position").asInt();

		// Safety check: prevent clicking an invalid hand position
		if (handPosition < 1 || handPosition > gameState.humanPlayer.hand.size()) {
			return;
		}

		Card clickedCard = gameState.humanPlayer.hand.get(handPosition - 1);

		// [SC-204] Mana check interceptor
		if (gameState.humanPlayer.getMana() < clickedCard.getManacost()) {
			BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
			return;
		}
	}
}