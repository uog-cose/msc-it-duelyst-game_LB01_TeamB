package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case a tile.
 * The event returns the x (horizontal) and y (vertical) indices of the tile that was
 * clicked. Tile indices start at 1.
 *
 * {
 *   messageType = “tileClicked”
 *   tilex = <x index of the tile>
 *   tiley = <y index of the tile>
 * }
 *
 * @author Dr. Richard McCreadie
 *
 */
public class TileClicked implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        int rawTilex = message.get("tilex").asInt();
        int rawTiley = message.get("tiley").asInt();

        int tilex = rawTilex;
        int tiley = rawTiley;

        System.out.println("[SC-201] tileclicked raw=(" + rawTilex + "," + rawTiley
                + ") zeroBased=(" + tilex + "," + tiley + ")");

        if (!gameState.gameInitalised || !gameState.humanTurn) {
            System.out.println("[SC-201] tileclick ignored: game not initialised or not human turn");
            return;
        }

		if (gameState.selectedCard != null && gameState.selectedHandPosition >= 1) {
 
            if (!gameState.isWithinBoard(tilex, tiley)) {
                System.out.println("[SC-205] invalid target: outside board");
                BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
                return;
            }
			boolean highlighted = gameState.isHighlightedSummonTileByUiCoords(tilex, tiley);
            boolean free = gameState.isTileFree(tilex, tiley);
 
            System.out.println("summon check — highlighted=" + highlighted + " free=" + free);
 
            if (!highlighted || !free) {
                System.out.println("[SC-205] invalid target: not highlighted or not free");
                BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
                return;
            }

			int manaCost = gameState.getCardManaCost(gameState.selectedCard);
            if (manaCost > gameState.humanPlayer.getMana()) {
                System.out.println("[SC-205] summon blocked: not enough mana");
                BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
                gameState.clearCardSelection(out);
                return;
            }

			Tile targetTile = gameState.getTile(tilex, tiley);
            if (targetTile == null) {
                BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
                return;
            }
 
            String unitConfigPath = gameState.buildUnitConfigPath(gameState.selectedCard);
            System.out.println("[SC-201] loading unit from " + unitConfigPath);
 
            Unit summonedUnit;
            try {
                summonedUnit = BasicObjectBuilders.loadUnit(
                        unitConfigPath, gameState.nextUnitId++, Unit.class);
            } catch (Exception e) {
                e.printStackTrace();
                BasicCommands.addPlayer1Notification(out, "Unable to load unit", 2);
                gameState.clearCardSelection(out);
                return;
            }

			targetTile.setUnit(summonedUnit);
            summonedUnit.setPositionByTile(targetTile);
            gameState.humanUnits.add(summonedUnit);
 
            gameState.humanPlayer.setMana(gameState.humanPlayer.getMana() - manaCost);
            BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
            BasicCommands.drawUnit(out, summonedUnit, targetTile);
 
            gameState.humanPlayer.hand.remove(gameState.selectedHandPosition - 1);
            gameState.refreshHumanHandUI(out);
 
            gameState.clearCardSelection(out);
            gameState.clearMoveTileHighlights(out);
            gameState.selectedUnit = null;
            gameState.actionSeq++;
 
            System.out.println("[SC-201] summon success at (" + tilex + "," + tiley
                    + ") mana=" + gameState.humanPlayer.getMana()
                    + " handSize=" + gameState.humanPlayer.hand.size());
            return;

		}

        // CASE 2: No card selected to handle unit selection (SC-302)
        Tile clickedTile = gameState.getTile(tilex, tiley);
 
        if (clickedTile != null && clickedTile.getUnit() != null) {
            Unit clickedUnit = clickedTile.getUnit();
            boolean isFriendly = (clickedUnit == gameState.humanAvatar)
                    || gameState.humanUnits.contains(clickedUnit);
 
            if (isFriendly) {
                if (gameState.selectedUnit == clickedUnit) {
                    // Same unit clicked again to toggle off
                    System.out.println("[SC-302] deselecting unit (toggle off)");
                    gameState.clearMoveTileHighlights(out);
                    gameState.selectedUnit = null;
                } else {
                    // New unit selected to clear old highlights, show new range
                    System.out.println("[SC-302] selecting unit at (" + tilex + "," + tiley + ")");
                    gameState.selectedUnit = clickedUnit;
                    gameState.highlightValidMoveTiles(out, tilex, tiley);
                }
                return;
            }
        }

		// Clicked empty or enemy tile with no card to clear everything
        System.out.println("[TileClicked] clearing selection");
        gameState.clearMoveTileHighlights(out);
        gameState.selectedUnit = null;
    }
}