package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;

/**
 * Indicates that the user has clicked an object on the game canvas, in this
 * case a card.
 * The event returns the position in the player's hand the card resides within.
 *
 * {
 * messageType = “cardClicked”
 * position = <hand index position [1-6]>
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

        // Clicking the already selected card to unselect it
        if (gameState.selectedHandPosition == handPosition && gameState.selectedCard == clickedCard) {
            System.out.println("[SC-201] duplicate click on already selected card ignored");
            gameState.clearCardSelection(out);
            gameState.clearMoveTileHighlights(out);
            gameState.clearSummonTileHighlights(out);
            gameState.clearSpellTileHighlights(out);
            gameState.selectedUnit = null;
            return;
        }

        // Switching to a new card to clear everything first
        gameState.clearCardSelection(out);

        // Also clear any movement highlights (unit and card selection are mutually
        // exclusive)
        gameState.clearMoveTileHighlights(out);
        gameState.selectedUnit = null;
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } // let queue being flushed to highlight tiles properly

        if (gameState.isSpellCard(clickedCard)) {
            if (manaCost > gameState.humanPlayer.getMana()) {
                if (out != null) {
                    BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
                }
                return;
            }

            // A Wraithling Swarm, no target needed, cast directly
            if ("Wraithling Swarm".equalsIgnoreCase(gameState.getCardName(clickedCard))) {
                gameState.humanPlayer.setMana(gameState.humanPlayer.getMana() - manaCost);
                if (out != null)
                    BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
                gameState.castWraithlingSwarm(out);
                gameState.humanPlayer.hand.remove(handPosition - 1);
                gameState.refreshHumanHandUI(out);
                return;
            }

            gameState.selectedCard = clickedCard;
            gameState.selectedHandPosition = handPosition;

            if (out != null) {
                BasicCommands.drawCard(out, clickedCard, handPosition, 1);
            }

            int validTargetCount = gameState.highlightValidSpellTargets(out, clickedCard);
            System.out.println("[SPELL] validTargetCount=" + validTargetCount);

            if (validTargetCount == 0) {
                if (out != null) {
                    BasicCommands.addPlayer1Notification(out, "No valid spell targets", 2);
                }
                gameState.clearCardSelection(out);
                return;

            }
            // Remove card from hand immediately on selection
            // gameState.humanPlayer.hand.remove(handPosition - 1);
            // gameState.refreshHumanHandUI(out);
            return;
        }

        if (manaCost > gameState.humanPlayer.getMana()) {
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            }
            return;
        }

        // Select this card and show summon highlights
        gameState.selectedCard = clickedCard;
        gameState.selectedHandPosition = handPosition;
        BasicCommands.drawCard(out, clickedCard, handPosition, 1);

        int validTargetCount = gameState.highlightValidSummonTiles(out);
        System.out.println("[SC-201] validTargetCount=" + validTargetCount);

        if (validTargetCount == 0) {
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "No valid summon tiles", 2);
            }
            gameState.clearCardSelection(out);
            return;
        }
    }
}
