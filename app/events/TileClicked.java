package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import structures.CombatHandler;
import java.util.ArrayList;

/**
 * Indicates that the user has clicked an object on the game canvas, in this
 * case a tile.
 * The event returns the x (horizontal) and y (vertical) indices of the tile
 * that was
 * clicked. Tile indices start at 1.
 *
 * {
 * messageType = “tileClicked”
 * tilex = <x index of the tile>
 * tiley = <y index of the tile>
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

            // spell card handling SC-205 ro check if the selected card is a spell and
            // handle accordingly,
            // placing this code before summon unit part as the highlighting logic for spell
            // cards is different
            // and I don't want to accidentally treat a spell card as a summon card.
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

                    if (out != null) {
                        BasicCommands.playUnitAnimation(out, targetUnit, structures.basic.UnitAnimationType.death);
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    gameState.removeUnitFromBoard(targetUnit, out);

                    if (out != null) {
                        BasicCommands.deleteUnit(out, targetUnit);
                    }

                    // SC-WIN: Dark Terminus destroys AI avatar thus HUMAN WINS
                    if (targetUnit == gameState.aiAvatar) {
                        BasicCommands.addPlayer1Notification(out, "You Win!", 5);
                        // gameState.gameInitalised = false;
                        gameState.endGame(out, "You Win!");
                        return;
                    }

                    // Wraithling spawn on destroyed unit's tile
                    Tile spawnTile = gameState.getTile(tilex, tiley);
                    if (spawnTile != null && spawnTile.getUnit() == null) {
                        Unit wraithling = BasicObjectBuilders.loadUnit(
                                utils.StaticConfFiles.wraithling, gameState.nextUnitId++, Unit.class);
                        spawnTile.setUnit(wraithling);
                        wraithling.setPositionByTile(spawnTile);
                        gameState.humanUnits.add(wraithling);
                        gameState.setUnitHealth(wraithling, 1);
                        gameState.setUnitAttack(wraithling, 1);
                        if (out != null) {
                            BasicCommands.drawUnit(out, wraithling, spawnTile);
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            BasicCommands.setUnitHealth(out, wraithling, 1);
                            BasicCommands.setUnitAttack(out, wraithling, 1);
                        }
                    }

                } else if ("True Strike".equalsIgnoreCase(spellName)) {
                    int newHp = gameState.damageTarget(targetUnit, 2);
                    if (newHp <= 0) {

                        if (out != null) {
                            BasicCommands.playUnitAnimation(out, targetUnit, structures.basic.UnitAnimationType.death);
                            try {
                                Thread.sleep(150);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        gameState.removeUnitFromBoard(targetUnit, out);

                        if (out != null) {
                            BasicCommands.deleteUnit(out, targetUnit);
                        }

                        // SC-WIN: True Strike destroys AI avatar - HUMAN WINS
                        if (targetUnit == gameState.aiAvatar) {
                            BasicCommands.addPlayer1Notification(out, "You Win!", 5);
                            // gameState.gameInitalised = false;
                            gameState.endGame(out, "You Win!");
                            return;
                        }
                    }

                } else if ("Sundrop Elixir".equalsIgnoreCase(spellName)) {
                    gameState.healTarget(targetUnit, 5);

                } else {
                    if (out != null) {
                        BasicCommands.addPlayer1Notification(out, "Spell not implemented yet", 2);
                    }
                    gameState.clearCardSelection(out);
                    return;
                }
                // sending updated health to UI after applying spell effects,
                if (out != null && targetUnit != gameState.humanAvatar && targetUnit != gameState.aiAvatar) {
                    BasicCommands.setUnitHealth(out, targetUnit, gameState.getUnitHealth(targetUnit));
                }

                gameState.humanPlayer.setMana(gameState.humanPlayer.getMana() - manaCost);
                if (out != null) {
                    BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
                }

                // sync avatar/player HP if avatar was target
                gameState.syncPlayerStatsUI(out);

                // updating health stats after spell and attack
                if (out != null) {
                    if (targetUnit == gameState.humanAvatar) {
                        BasicCommands.setUnitHealth(out, targetUnit, gameState.humanPlayer.getHealth());
                        BasicCommands.setUnitAttack(out, targetUnit, gameState.getUnitAttack(targetUnit));
                    } else if (targetUnit == gameState.aiAvatar) {
                        BasicCommands.setUnitHealth(out, targetUnit, gameState.aiPlayer.getHealth());
                        BasicCommands.setUnitAttack(out, targetUnit, gameState.getUnitAttack(targetUnit));
                    }
                }

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

            // Saving unit name for deathwatch
            gameState.unitNames.put(summonedUnit,
                    gameState.getCardName(gameState.selectedCard)
                            .toLowerCase()
                            .replace("'", "")
                            .replaceAll("[^a-z0-9]+", "_")
                            .replaceAll("^_+|_+$", ""));

            // Adding Opening Gambit trigger
            gameState.triggerOpeningGambit(out, summonedUnit, targetTile, true);

            // to handle play spell health
            int summonedHp = gameState.getCardHealth(gameState.selectedCard);
            gameState.setUnitHealth(summonedUnit, summonedHp);
            int summonedAtk = gameState.getCardAttack(gameState.selectedCard);
            gameState.setUnitAttack(summonedUnit, summonedAtk);
            System.out.println("summoned unit hp and attack" + summonedHp + summonedAtk);

            gameState.humanPlayer.setMana(gameState.humanPlayer.getMana() - manaCost);
            if (out != null) {
                BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
                BasicCommands.drawUnit(out, summonedUnit, targetTile);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } // to show summon unit health
                BasicCommands.setUnitHealth(out, summonedUnit, summonedHp);
                BasicCommands.setUnitAttack(out, summonedUnit, summonedAtk);
            }

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

                gameState.markUnitAsMoved(gameState.selectedUnit);
                gameState.clearMoveTileHighlights(out);
                gameState.selectedUnit = null;
                // gameState.highlightValidAttackTiles(out, gameState.selectedUnit);
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

                if (gameState.hasUnitAttacked(clickedUnit)) {
                    System.out.println("[SC-305] unit already attacked, movement blocked");
                    gameState.clearMoveTileHighlights(out);
                    gameState.selectedUnit = null;

                    if (out != null) {
                        BasicCommands.addPlayer1Notification(out, "This unit cannot move after attacking", 2);
                    }
                    return;
                }

                if (gameState.hasUnitMoved(clickedUnit)) {
                    // moved already — select for attack only, no move highlights
                    if (gameState.selectedUnit == clickedUnit) {
                        gameState.clearMoveTileHighlights(out);
                        gameState.selectedUnit = null;
                    } else {
                        gameState.clearMoveTileHighlights(out);
                        gameState.selectedUnit = clickedUnit; // intentionally no highlightValidMoveTiles

                        int enemyCount = gameState.highlightValidAttackTiles(out, clickedUnit);
                        if (enemyCount == 0) {
                            BasicCommands.addPlayer1Notification(out, "Moved: No enemies in range", 2);
                            gameState.selectedUnit = null;
                        } else {
                            BasicCommands.addPlayer1Notification(out, "Unit has already moved", 2);
                        }
                    }
                    return;
                }

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

        // CASE 3: Attack enemy with selected unit/avatar SC-303
        // CASE 3: Attack enemy with selected unit/avatar SC-303
        if (clickedTile != null
                && clickedTile.getUnit() != null
                && gameState.selectedUnit != null
                && gameState.isEnemyUnit(clickedTile.getUnit())) {

            Unit attacker = gameState.selectedUnit;
            Unit defender = clickedTile.getUnit();

            Tile attackerTile = gameState.findTileContainingUnit(attacker);
            if (attackerTile == null)
                return;

            if (gameState.hasUnitAttacked(attacker)) {
                BasicCommands.addPlayer1Notification(out, "Unit already attacked!", 2);
                return;
            }

            int dx = Math.abs(attackerTile.getTilex() - tilex);
            int dy = Math.abs(attackerTile.getTiley() - tiley);

            if (dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0)) {
                // Direct adjacent attack
                CombatHandler.executeAttack(out, gameState, attacker, defender);
                gameState.clearMoveTileHighlights(out);
                gameState.selectedUnit = null;
                gameState.actionSeq++;
                System.out.println("[SC-303] attack success at (" + tilex + "," + tiley + ")");

            } else {
                // Not adjacent — store pending attack, move karo
                if (gameState.hasUnitMoved(attacker)) {
                    BasicCommands.addPlayer1Notification(out, "Target is out of range!", 2);
                    return;
                }
            
                // Best adjacent free tile dhundo
                Tile bestMoveTile = null;
                int bestDist = Integer.MAX_VALUE;
                for (Tile moveTile : new ArrayList<>(gameState.highlightedMoveTiles)) {
                    if (moveTile.getUnit() != null) continue;
                    int mdx = Math.abs(moveTile.getTilex() - tilex);
                    int mdy = Math.abs(moveTile.getTiley() - tiley);
                    if (mdx <= 1 && mdy <= 1 && !(mdx == 0 && mdy == 0)) {
                        int dist = mdx + mdy;
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestMoveTile = moveTile;
                        }
                    }
                }
            
                if (bestMoveTile == null) {
                    BasicCommands.addPlayer1Notification(out, "Target is out of range!", 2);
                    return;
                }
            
                // Pending attack store karo — UnitStopped me trigger hoga
                gameState.pendingAttacker = attacker;
                gameState.pendingDefender = defender;
            
                // Move karo — NO sleep
                Tile currentTile = gameState.findTileContainingUnit(attacker);
                if (currentTile != null) currentTile.setUnit(null);
                bestMoveTile.setUnit(attacker);
                attacker.setPositionByTile(bestMoveTile);
                gameState.markUnitAsMoved(attacker);
                if (out != null) {
                    BasicCommands.moveUnitToTile(out, attacker, bestMoveTile);
                }
                gameState.clearMoveTileHighlights(out);
                gameState.selectedUnit = null;
                gameState.actionSeq++;
                System.out.println("[SC-303] move triggered, attack pending UnitStopped");
            }
            return;
        }

        // Clicked empty or enemy tile with no card to clear everything
        System.out.println("[TileClicked] clearing selection");
        gameState.clearMoveTileHighlights(out);
        gameState.selectedUnit = null;
    }
}