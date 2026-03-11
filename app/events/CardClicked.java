package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
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
public class CardClicked implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        if (!gameState.gameInitalised || !gameState.humanTurn) {
            return;
        }

        int handPosition = message.get("position").asInt();
        System.out.println("[SC-201] cardclicked handPosition=" + handPosition
                + " handSize=" + gameState.humanPlayer.hand.size());

        if (handPosition < 1 || handPosition > gameState.humanPlayer.hand.size()) {
            return;
        }

        Card clickedCard = gameState.humanPlayer.hand.get(handPosition - 1);
        if (clickedCard == null) {
            return;
        }

        int manaCost = gameState.getCardManaCost(clickedCard);
        System.out.println("[SC-201] selected card=" + gameState.getCardName(clickedCard)
                + " manaCost=" + manaCost
                + " currentMana=" + gameState.humanPlayer.getMana());

        if (gameState.selectedHandPosition == handPosition && gameState.selectedCard == clickedCard) {
            System.out.println("[SC-201] duplicate click on already selected card ignored");
            return;
        }

        gameState.clearCardSelection(out);

        if (gameState.isSpellCard(clickedCard)) {
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "Spell cards are not part of SC-201", 2);
            }
            return;
        }

        if (manaCost > gameState.humanPlayer.getMana()) {
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            }
            return;
        }

        gameState.selectedCard = clickedCard;
        gameState.selectedHandPosition = handPosition;
        if (out != null) {
            BasicCommands.drawCard(out, clickedCard, handPosition, 1);
        }
        int validTargetCount = gameState.highlightValidSummonTiles(out);
        System.out.println("[SC-201] validTargetCount=" + validTargetCount);

        if (validTargetCount == 0) {
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "No valid summon tiles", 2);
            }
            gameState.clearCardSelection(out);
        }
    }
}
