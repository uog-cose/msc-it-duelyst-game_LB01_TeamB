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

            //spell card handling SC-205 ro check if the selected card is a spell and handle accordingly,
            //placing this code before summon unit part as the highlighting logic for spell cards is different 
            //and I don't want to accidentally treat a spell card as a summon card. 
                if (gameState.isSpellCard(gameState.selectedCard)) {
            
                    boolean highlightedSpell = gameState.isHighlightedSpellTileByUiCoords(tilex, tiley);
                    if (!highlightedSpell) {
                        if (out != null) {
                            BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
                        }
                        return;
                    }
            
                    Tile targetTile = gameState.getTile(tilex, tiley);
                    if (targetTile == null || targetTile.getUnit() == null) {
                        if (out != null) {
                            BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
                        }
                        return;
                    }
            
                    Unit targetUnit = targetTile.getUnit();
                    String spellName = gameState.getCardName(gameState.selectedCard);
                    int manaCost = gameState.getCardManaCost(gameState.selectedCard);
            
                    if (manaCost > gameState.humanPlayer.getMana()) {
                        if (out != null) {
                            BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
                        }
                        gameState.clearCardSelection(out);
                        return;
                    }
            
                    if ("Dark Terminus".equalsIgnoreCase(spellName)) {
                        gameState.removeUnitFromBoard(targetUnit);
            
                    } else if ("True Strike".equalsIgnoreCase(spellName)) {
                        int newHp = gameState.damageTarget(targetUnit, 2);
                        if (newHp <= 0) {
                            gameState.removeUnitFromBoard(targetUnit);
                        }
            
                    } else if ("Sundrop Elixir".equalsIgnoreCase(spellName)) {
                        gameState.healTarget(targetUnit, 4);
            
                    } else {
                        if (out != null) {
                            BasicCommands.addPlayer1Notification(out, "Spell not implemented yet", 2);
                        }
                        gameState.clearCardSelection(out);
                        return;
                    }
            
                    gameState.humanPlayer.setMana(gameState.humanPlayer.getMana() - manaCost);
                    if (out != null) {
                        BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
                    }
            
                    // sync avatar/player HP if avatar was target
                    gameState.syncPlayerStatsUI(out);
            
                    gameState.humanPlayer.hand.remove(gameState.selectedHandPosition - 1);
                    gameState.refreshHumanHandUI(out);
            
                    gameState.clearCardSelection(out);
                    gameState.clearMoveTileHighlights(out);
                    gameState.selectedUnit = null;
                    gameState.actionSeq++;
            
                    System.out.println("[SPELL] cast success: " + spellName + " at (" + tilex + "," + tiley + ")");
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

            //to handle play spell health
            int summonedHp = gameState.getCardHealth(gameState.selectedCard);
            gameState.setUnitHealth(summonedUnit, summonedHp);
            System.out.println("[SC-201] summoned unit hp=" + summonedHp);
 
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

         // Start of movement handling logic here
         if (gameState.selectedUnit != null) {
            Tile clickedMoveTile = gameState.getTile(tilex, tiley);

            if (clickedMoveTile != null
                    && clickedMoveTile.getUnit() == null
                    && gameState.isHighlightedMoveTile(tilex, tiley)) {

                Tile currentTile = gameState.findTileContainingUnit(gameState.selectedUnit);
                if (currentTile != null) {
                    currentTile.setUnit(null);
                }

                clickedMoveTile.setUnit(gameState.selectedUnit);
                gameState.selectedUnit.setPositionByTile(clickedMoveTile);

                if (out != null) {
                    BasicCommands.moveUnitToTile(out, gameState.selectedUnit, clickedMoveTile);
                }

                System.out.println("[SC-301] moved unit to (" + tilex + "," + tiley + ")");

                gameState.clearMoveTileHighlights(out);
                gameState.selectedUnit = null;
                gameState.actionSeq++;
                return;
            }
        }
        // END of movememt handling logic

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