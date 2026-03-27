package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.CombatHandler;
import structures.GameState;
import structures.MovementEngine;
import structures.SpellEngine;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;

/**
 * Indicates that the user has clicked an object on the game canvas, in this
 * case a tile.
 *
 * {
 * messageType = "tileClicked"
 * tilex = <x index of the tile>
 * tiley = <y index of the tile>
 * }
 *
 * Step 2 Refactor:
 * Move execute logic -> MovementEngine.executeMove()
 * bestMoveTile finder -> MovementEngine.findBestApproachTile()
 * Highlight calls -> MovementEngine.highlightMoveRange() /
 * highlightAttackRange()
 * TileClicked is now pure routing only
 */
public class TileClicked implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        int tilex = message.get("tilex").asInt();
        int tiley = message.get("tiley").asInt();

        System.out.println("[TileClicked] (" + tilex + "," + tiley + ")");

        if (!gameState.gameInitalised || !gameState.humanTurn) {
            System.out.println("[TileClicked] ignored: game not initialised or not human turn");
            return;
        }
        // CASE 1: Card selected — spell cast OR unit summon
        // (Spell/summon logic unchanged — that's Step 4)
        if (gameState.selectedCard != null && gameState.selectedHandPosition >= 1) {

            if (!gameState.isWithinBoard(tilex, tiley)) {
                BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
                return;
            }

            // Spell handling
            if (gameState.isSpellCard(gameState.selectedCard)) {

                boolean highlightedSpell = gameState.isHighlightedSpellTileByUiCoords(tilex, tiley);
                if (!highlightedSpell) {
                    if (out != null)
                        BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
                    return;
                }

                Tile targetTile = gameState.getTile(tilex, tiley);
                if (targetTile == null || targetTile.getUnit() == null) {
                    if (out != null)
                        BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
                    return;
                }

                Unit targetUnit = targetTile.getUnit();
                String spellName = gameState.getCardName(gameState.selectedCard);
                int manaCost = gameState.getCardManaCost(gameState.selectedCard);

                if (manaCost > gameState.humanPlayer.getMana()) {
                    if (out != null)
                        BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
                    gameState.clearCardSelection(out);
                    return;
                }

                // Delegate effect to SpellEngine
                SpellEngine.castSpell(out, gameState, spellName, targetUnit, targetTile);

                // Routing concerns stay here
                gameState.humanPlayer.setMana(gameState.humanPlayer.getMana() - manaCost);
                if (out != null)
                    BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
                gameState.syncPlayerStatsUI(out);
                // gameState.humanPlayer.hand.remove(gameState.selectedHandPosition - 1);
                int removeIdx = gameState.humanPlayer.hand.indexOf(gameState.selectedCard);
                if (removeIdx >= 0) {
                    gameState.humanPlayer.hand.remove(removeIdx);
                }
                gameState.refreshHumanHandUI(out);
                gameState.clearCardSelection(out);
                gameState.clearMoveTileHighlights(out);
                gameState.selectedUnit = null;
                gameState.actionSeq++;
                System.out.println("[SPELL] cast: " + spellName + " at (" + tilex + "," + tiley + ")");
                return;
            }

            // Summon handling
            boolean highlighted = gameState.isHighlightedSummonTileByUiCoords(tilex, tiley);
            boolean free = gameState.isTileFree(tilex, tiley);

            if (!highlighted || !free) {
                BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
                return;
            }

            int manaCost = gameState.getCardManaCost(gameState.selectedCard);
            if (manaCost > gameState.humanPlayer.getMana()) {
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
            Unit summonedUnit;
            try {
                summonedUnit = BasicObjectBuilders.loadUnit(unitConfigPath, gameState.nextUnitId++, Unit.class);
            } catch (Exception e) {
                e.printStackTrace();
                BasicCommands.addPlayer1Notification(out, "Unable to load unit", 2);
                gameState.clearCardSelection(out);
                return;
            }

            targetTile.setUnit(summonedUnit);
            summonedUnit.setPositionByTile(targetTile);
            gameState.humanUnits.add(summonedUnit);

            gameState.unitNames.put(summonedUnit,
                    gameState.getCardName(gameState.selectedCard)
                            .toLowerCase().replace("'", "")
                            .replaceAll("[^a-z0-9]+", "_")
                            .replaceAll("^_+|_+$", ""));

            gameState.triggerOpeningGambit(out, summonedUnit, targetTile, true);

            int summonedHp = gameState.getCardHealth(gameState.selectedCard);
            int summonedAtk = gameState.getCardAttack(gameState.selectedCard);
            gameState.setUnitHealth(summonedUnit, summonedHp);
            gameState.setUnitAttack(summonedUnit, summonedAtk);

            gameState.humanPlayer.setMana(gameState.humanPlayer.getMana() - manaCost);
            if (out != null) {
                BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
                BasicCommands.drawUnit(out, summonedUnit, targetTile);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                BasicCommands.setUnitHealth(out, summonedUnit, summonedHp);
                BasicCommands.setUnitAttack(out, summonedUnit, summonedAtk);
            }

            // gameState.humanPlayer.hand.remove(gameState.selectedHandPosition - 1);
            int removeIdx = gameState.humanPlayer.hand.indexOf(gameState.selectedCard);
            if (removeIdx >= 0) {
                gameState.humanPlayer.hand.remove(removeIdx);
            }
            gameState.refreshHumanHandUI(out);
            gameState.clearCardSelection(out);
            gameState.clearMoveTileHighlights(out);
            gameState.selectedUnit = null;
            gameState.actionSeq++;

            System.out.println("[SC-201] summon success at (" + tilex + "," + tiley + ")");
            return;
        }

        // CASE 2: Unit selected + clicked a highlighted move tile
        // MovementEngine.executeMove()

        if (gameState.selectedUnit != null) {
            Tile clickedMoveTile = gameState.getTile(tilex, tiley);

            if (clickedMoveTile != null
                    && clickedMoveTile.getUnit() == null
                    && MovementEngine.isHighlightedMoveTile(gameState, tilex, tiley)) {

                // Delegate move to MovementEngine
                MovementEngine.executeMove(out, gameState, gameState.selectedUnit, clickedMoveTile);

                gameState.clearMoveTileHighlights(out);
                gameState.selectedUnit = null;
                gameState.actionSeq++;
                System.out.println("[SC-301] moved unit to (" + tilex + "," + tiley + ")");
                return;
            }
        }

        // CASE 3: No card — unit selection (SC-302)
        Tile clickedTile = gameState.getTile(tilex, tiley);

        if (clickedTile != null && clickedTile.getUnit() != null) {
            Unit clickedUnit = clickedTile.getUnit();
            boolean isFriendly = (clickedUnit == gameState.humanAvatar)
                    || gameState.humanUnits.contains(clickedUnit);

            if (isFriendly) {

                if (gameState.hasUnitAttacked(clickedUnit)) {
                    gameState.clearMoveTileHighlights(out);
                    gameState.selectedUnit = null;
                    if (out != null)
                        BasicCommands.addPlayer1Notification(out, "This unit cannot move after attacking", 2);
                    return;
                }

                if (gameState.hasUnitMoved(clickedUnit)) {
                    // Already moved — show attack-only highlights
                    if (gameState.selectedUnit == clickedUnit) {
                        gameState.clearMoveTileHighlights(out);
                        gameState.selectedUnit = null;
                    } else {
                        gameState.clearMoveTileHighlights(out);
                        gameState.selectedUnit = clickedUnit;

                        // Delegate to MovementEngine for attack-only highlights
                        int enemyCount = MovementEngine.highlightAttackRange(out, gameState, clickedUnit);
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
                    // Toggle off
                    gameState.clearMoveTileHighlights(out);
                    gameState.selectedUnit = null;
                } else {
                    // Select new unit + show move range
                    gameState.selectedUnit = clickedUnit;
                    // Delegate to MovementEngine
                    MovementEngine.highlightMoveRange(out, gameState, tilex, tiley);
                }
                return;
            }
        }

        // CASE 4: Attack enemy with selected unit (SC-303)
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
                System.out.println("[SC-303] direct attack at (" + tilex + "," + tiley + ")");

            } else {
                // Not adjacent — need to move first then attack
                if (gameState.hasUnitMoved(attacker)) {
                    BasicCommands.addPlayer1Notification(out, "Target is out of range!", 2);
                    return;
                }

                // Delegate approach tile finding to MovementEngine
                Tile bestMoveTile = MovementEngine.findBestApproachTile(gameState, tilex, tiley);

                if (bestMoveTile == null) {
                    BasicCommands.addPlayer1Notification(out, "Target is out of range!", 2);
                    return;
                }

                // Store pending attack — fires in UnitStopped after animation
                gameState.pendingAttacker = attacker;
                gameState.pendingDefender = defender;

                // Delegate move to MovementEngine
                MovementEngine.executeMove(out, gameState, attacker, bestMoveTile);

                gameState.clearMoveTileHighlights(out);
                gameState.selectedUnit = null;
                gameState.actionSeq++;
                System.out.println("[SC-303] move triggered, attack pending UnitStopped");
            }
            return;
        }

        // Clicked empty/irrelevant tile — clear everything
        gameState.clearMoveTileHighlights(out);
        gameState.selectedUnit = null;
    }
}