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

        if (gameState.selectedCard == null || gameState.selectedHandPosition < 1) {
            Tile clickedTile = gameState.getTile(tilex, tiley);

            if (clickedTile != null && clickedTile.getUnit() != null) {
                Unit clickedUnit = clickedTile.getUnit();

                // allow movement highlight for human-owned unit / avatar
                if (clickedUnit == gameState.humanAvatar || gameState.humanUnits.contains(clickedUnit)) {
                    gameState.highlightValidMoveTiles(out, tilex, tiley);
                    return;
                }
            }

            System.out.println("[SC-201] tileclick ignored: no selected card");
            return;
        }

        // SC-202: handle spell card target click
        if (gameState.isSpellCard(gameState.selectedCard)) {

            System.out.println("[SC-202] spell card clicked target tile");

            if (!gameState.isHighlightedSummonTileByUiCoords(rawTilex, rawTiley)) {
                BasicCommands.addPlayer1Notification(out, "Invalid spell target", 2);
                return;
            }

            Tile targetTile = gameState.getTile(tilex, tiley);

            if (targetTile == null) {
                BasicCommands.addPlayer1Notification(out, "Invalid spell target", 2);
                return;
            }

            Unit targetUnit = targetTile.getUnit();

            String spellName = gameState.getCardName(gameState.selectedCard);

            // Example: Dark Terminus destroys enemy unit
            if ("Dark Terminus".equalsIgnoreCase(spellName)) {

                if (targetUnit == null || !gameState.aiUnits.contains(targetUnit)) {
                    BasicCommands.addPlayer1Notification(out, "Invalid spell target", 2);
                    return;
                }

                targetTile.setUnit(null);
                gameState.aiUnits.remove(targetUnit);

                BasicCommands.deleteUnit(out, targetUnit);
            } else {
                BasicCommands.addPlayer1Notification(out, "Spell not implemented yet", 2);
                gameState.clearCardSelection(out);
                return;
            }

            int manaCost = gameState.getCardManaCost(gameState.selectedCard);

            gameState.humanPlayer.setMana(
                    gameState.humanPlayer.getMana() - manaCost
            );

            BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);

            gameState.humanPlayer.hand.remove(gameState.selectedHandPosition - 1);

            gameState.refreshHumanHandUI(out);

            gameState.clearCardSelection(out);

            System.out.println("[SC-202] spell cast success");

            return;
        }

        System.out.println("[SC-201] selected card on tileclick="
                + gameState.getCardName(gameState.selectedCard)
                + " selectedHandPosition=" + gameState.selectedHandPosition);

        if (!gameState.isWithinBoard(tilex, tiley)) {
            System.out.println("[SC-205] invalid target: outside board");
            BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
            return;
        }

        boolean highlighted = gameState.isHighlightedSummonTileByUiCoords(rawTilex, rawTiley);
        boolean free = gameState.isTileFree(tilex, tiley);

        System.out.println("[SC-201] isHighlightedByUiCoords=" + highlighted + " isTileFree=" + free);

        if (!highlighted || !free) {
            System.out.println("[SC-205] invalid target: not highlighted or not free");
            BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
            return;
        }

        int manaCost = gameState.getCardManaCost(gameState.selectedCard);
        System.out.println("[SC-201] tile summon manaCost=" + manaCost
                + " currentMana=" + gameState.humanPlayer.getMana());

        if (manaCost > gameState.humanPlayer.getMana()) {
            System.out.println("[SC-205] summon blocked: not enough mana");
            BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            gameState.clearCardSelection(out);
            return;
        }

        Tile targetTile = gameState.getTile(tilex, tiley);
        if (targetTile == null) {
            System.out.println("[SC-205] invalid target: target tile null");
            BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
            return;
        }

        Unit summonedUnit;
        String unitConfigPath = gameState.buildUnitConfigPath(gameState.selectedCard);
        System.out.println("[SC-201] loading unit from " + unitConfigPath);

        try {
            summonedUnit = BasicObjectBuilders.loadUnit(
                    unitConfigPath,
                    gameState.nextUnitId++,
                    Unit.class);
        } catch (Exception e) {
            System.out.println("[SC-201] failed to load unit for selected card");
            e.printStackTrace();
            BasicCommands.addPlayer1Notification(out, "Unable to load unit for selected card", 2);
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

        gameState.clearHighlightedTiles(out);
        gameState.selectedCard = null;
        gameState.selectedHandPosition = -1;
        gameState.actionSeq++;

        System.out.println("[SC-201] summon success at (" + tilex + "," + tiley
                + ") remainingMana=" + gameState.humanPlayer.getMana()
                + " handSize=" + gameState.humanPlayer.hand.size());
    }
}