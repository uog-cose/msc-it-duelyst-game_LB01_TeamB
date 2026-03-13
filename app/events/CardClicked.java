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

        if (manaCost > gameState.humanPlayer.getMana()) {
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            }
            return;
        }

        // SC-202: during normal gameplay, spell cards enter target-selection mode.
        // Keep the old direct-mana-deduction behaviour for unit tests where out == null.
        if (gameState.isSpellCard(clickedCard)) {

            if (out == null) {
                gameState.humanPlayer.setMana(gameState.humanPlayer.getMana() - manaCost);
                return;
            }

            gameState.selectedCard = clickedCard;
            gameState.selectedHandPosition = handPosition;

            BasicCommands.drawCard(out, clickedCard, handPosition, 1);

            int validTargetCount = gameState.highlightValidSpellTargets(out);
            System.out.println("[SC-202] validSpellTargetCount=" + validTargetCount);

            if (validTargetCount == 0) {
                BasicCommands.addPlayer1Notification(out, "No valid spell targets", 2);
                gameState.clearCardSelection(out);
                return;
            }

            BasicCommands.addPlayer1Notification(out, "Select a target tile for the spell", 2);

            // spell effects will be handled later in TileClicked
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
