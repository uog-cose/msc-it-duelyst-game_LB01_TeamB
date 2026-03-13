package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;
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

        if (!gameState.isWithinBoard(tilex, tiley)) {
            System.out.println("[SC-205] invalid target: outside board");
            return;
        }

        if (gameState.selectedCard != null && gameState.selectedHandPosition >= 1) {

        System.out.println("[SC-201] selected card on tileclick="
                + gameState.getCardName(gameState.selectedCard)
                + " selectedHandPosition=" + gameState.selectedHandPosition);

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
    else {
            Tile clickedTile = gameState.getTile(tilex, tiley);
            Unit clickedUnit = clickedTile.getUnit();

            if (clickedUnit != null && gameState.isFriendlyUnit(clickedUnit)) {
                gameState.selectedUnit = clickedUnit;
                System.out.println("[SC-303] Selected friendly unit at " + tilex + "," + tiley);
                return;
            }
            
            if (clickedUnit != null && gameState.isEnemyUnit(clickedUnit) && gameState.selectedUnit != null) {
                Unit attacker = gameState.selectedUnit;
                Unit defender = clickedUnit;

                if (attacker.hasAttacked) {
                    BasicCommands.addPlayer1Notification(out, "Unit has already attacked!", 2);
                    return;
                }

                Tile attackerTile = gameState.findTileContainingUnit(attacker);
                int dx = Math.abs(attackerTile.getTilex() - tilex);
                int dy = Math.abs(attackerTile.getTiley() - tiley);

                if (dx <= 1 && dy <= 1) {
                    System.out.println("[SC-303] Attacking enemy!");

                    BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.attack);
                    BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.hit);

                    defender.health -= attacker.attack;
                    BasicCommands.setUnitHealth(out, defender, defender.health);
                    
                    if (defender == gameState.aiAvatar) {
                        gameState.aiPlayer.setHealth(defender.health);
                        gameState.syncPlayerStatsUI(out);
                    }

                    attacker.hasAttacked = true;
                    attacker.hasMoved = true; 

                    gameState.clearUnitSelection(out);
                    gameState.actionSeq++;
                } else {
                    BasicCommands.addPlayer1Notification(out, "Target is out of range!", 2);
                }
                return;
            }

            if (clickedUnit == null && gameState.selectedUnit != null) {
                Unit movingUnit = gameState.selectedUnit;

                if (movingUnit.hasMoved || movingUnit.hasAttacked) {
                    BasicCommands.addPlayer1Notification(out, "Unit cannot move!", 2);
                    return;
                }

                Tile startTile = gameState.findTileContainingUnit(movingUnit);
                int startX = startTile.getTilex();
                int startY = startTile.getTiley();

                boolean isValidMove = false;
                int[][] offsets = {
                        { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, 
                        { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 }, 
                        { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } 
                };
                
                for (int[] offset : offsets) {
                    if (startX + offset[0] == tilex && startY + offset[1] == tiley) {
                        isValidMove = true;
                        break;
                    }
                }

                if (isValidMove) {
                    System.out.println("[SC-301] Moving unit to " + tilex + "," + tiley);

                    BasicCommands.moveUnitToTile(out, movingUnit, clickedTile);

                    startTile.setUnit(null);
                    clickedTile.setUnit(movingUnit);
                    movingUnit.setPositionByTile(clickedTile);

                    movingUnit.hasMoved = true;

                    gameState.clearUnitSelection(out);
                    gameState.actionSeq++;
                } else {
                    BasicCommands.addPlayer1Notification(out, "Invalid move target!", 2);
                }
            }
        }
    }
}