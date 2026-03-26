package structures;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;
import utils.BasicObjectBuilders;
import utils.OrderedCardLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all AI turn logic automatically.
 * No click events, all actions computed and executed via BasicCommands.
 */
public class AITurn {

    public void execute(ActorRef out, GameState gameState) {

        System.out.println("AI Turn starting-> turn=" + gameState.turnNumber);

        // Step 1: Play affordable creature cards
        playCards(out, gameState);

        // Step 2: Move units toward enemies
        moveUnits(out, gameState);

        // Step 3: Attack with units
        attackWithUnits(out, gameState);

        // Step 4: End AI turn
        endAITurn(out, gameState);

        System.out.println("AI Turn complete");
    }

    // STEP 1: Play Cards
    private void playCards(ActorRef out, GameState gameState) {
        List<Card> hand = new ArrayList<>(gameState.aiPlayer.hand);
        System.out.println("AI DEBUG hand size=" + hand.size());

        for (Card c : hand) {
            System.out.println("AI DEBUG card=" + gameState.getCardName(c)
                    + " isCreature=" + c.isCreature()
                    + " mana=" + gameState.getCardManaCost(c));
        }

        for (Card card : hand) {
            int manaCost = gameState.getCardManaCost(card);
            if (manaCost > gameState.aiPlayer.getMana())
                continue;
            // if (!card.isCreature())
            // continue; // spells I will implement later
            if (!card.isCreature()) {
                // Spell handling
                String spellName = gameState.getCardName(card);
                int manaCost2 = gameState.getCardManaCost(card);
                if (manaCost2 > gameState.aiPlayer.getMana())
                    continue;

                if ("Truestrike".equalsIgnoreCase(spellName)) {
                    // Lowest HP human unit dhundo
                    Unit target = findLowestHpHumanUnit(gameState);
                    if (target == null)
                        continue;

                    int newHp = gameState.getUnitHealth(target) - 2;
                    if (target == gameState.humanAvatar) {
                        gameState.humanPlayer.setHealth(newHp);
                        if (out != null) {
                            gameState.syncPlayerStatsUI(out);
                            BasicCommands.setUnitHealth(out, target, newHp);
                        }
                        if (newHp <= 0) {
                            gameState.endGame(out, "You Lose!");
                            return;
                        }
                    } else {
                        gameState.setUnitHealth(target, newHp);
                        if (out != null)
                            BasicCommands.setUnitHealth(out, target, newHp);
                        if (newHp <= 0) {
                            gameState.removeUnitFromBoard(target, out);
                            if (out != null)
                                BasicCommands.deleteUnit(out, target);
                        }
                    }
                    gameState.aiPlayer.setMana(gameState.aiPlayer.getMana() - manaCost2);
                    gameState.aiPlayer.hand.remove(card);
                    if (out != null) {
                        BasicCommands.addPlayer1Notification(out, "Enemy cast True Strike!", 2);
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        gameState.syncPlayerStatsUI(out);
                    }
                    System.out.println("AI SPELL True Strike cast");

                } else if ("Sundrop Elixir".equalsIgnoreCase(spellName)) {
                    // Lowest HP AI unit dhundo
                    Unit target = findLowestHpAIUnit(gameState);
                    if (target == null)
                        continue;

                    int currentHp = gameState.getUnitHealth(target);
                    int maxHp = gameState.unitMaxHealth.getOrDefault(target, currentHp);
                    int healedHp = Math.min(currentHp + 5, maxHp); // max health se zyada nahi

                    if (target == gameState.aiAvatar) {
                        gameState.aiPlayer.setHealth(healedHp);
                        if (out != null) {
                            gameState.syncPlayerStatsUI(out);
                            BasicCommands.setUnitHealth(out, target, healedHp);
                        }
                    } else {
                        gameState.setUnitHealth(target, healedHp);
                        if (out != null)
                            BasicCommands.setUnitHealth(out, target, healedHp);
                    }
                    gameState.aiPlayer.setMana(gameState.aiPlayer.getMana() - manaCost2);
                    gameState.aiPlayer.hand.remove(card);
                    if (out != null) {
                        BasicCommands.addPlayer1Notification(out, "Enemy cast Sundrop Elixir!", 2);
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        gameState.syncPlayerStatsUI(out);
                    }
                    System.out.println("AI SPELL Sundrop Elixir cast");
                }
                continue; // spell processed, next card
            }

            // Find a valid summon tile adjacent to AI units/avatar
            Tile summonTile = findAISummonTile(gameState);
            if (summonTile == null)
                continue;

            // Load and place unit
            String configPath = gameState.buildUnitConfigPath(card);
            Unit unit;
            try {
                unit = BasicObjectBuilders.loadUnit(configPath, gameState.nextUnitId++, Unit.class);
            } catch (Exception e) {
                System.out.println("[AI] Failed to load unit: " + configPath);
                continue;
            }

            summonTile.setUnit(unit);
            unit.setPositionByTile(summonTile);
            gameState.aiUnits.add(unit);
            gameState.registerSummonedUnit(unit, card);

            // Saving unit name for death watch
            gameState.unitNames.put(unit,
                    gameState.getCardName(card)
                            .toLowerCase()
                            .replace("'", "")
                            .replaceAll("[^a-z0-9]+", "_")
                            .replaceAll("^_+|_+$", ""));

            // Opening Gambit trigger for AI
            gameState.triggerOpeningGambit(out, unit, summonTile, false); // false = AI

            int hp = gameState.getCardHealth(card);
            int atk = gameState.getCardAttack(card);
            gameState.setUnitHealth(unit, hp);
            gameState.setUnitAttack(unit, atk);

            gameState.aiPlayer.setMana(gameState.aiPlayer.getMana() - manaCost);
            gameState.aiPlayer.hand.remove(card);

            if (out != null) {
                BasicCommands.drawUnit(out, unit, summonTile);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                BasicCommands.setUnitHealth(out, unit, hp);
                BasicCommands.setUnitAttack(out, unit, atk);
                gameState.syncPlayerStatsUI(out);
            }

            System.out.println("[AI] Summoned: " + gameState.getCardName(card)
                    + " at (" + summonTile.getTilex() + "," + summonTile.getTiley() + ")");
        }
    }

    // Lowest HP human unit (avatar included)
    private Unit findLowestHpHumanUnit(GameState gameState) {
        Unit target = null;
        int lowestHp = Integer.MAX_VALUE;

        // avatar check
        int avatarHp = gameState.humanPlayer.getHealth();
        if (avatarHp < lowestHp) {
            lowestHp = avatarHp;
            target = gameState.humanAvatar;
        }

        for (Unit u : gameState.humanUnits) {
            int hp = gameState.getUnitHealth(u);
            if (hp < lowestHp) {
                lowestHp = hp;
                target = u;
            }
        }
        return target;
    }

    // Lowest HP AI unit (avatar included)
    private Unit findLowestHpAIUnit(GameState gameState) {
        Unit target = null;
        int lowestHp = Integer.MAX_VALUE;

        // avatar check
        int avatarHp = gameState.aiPlayer.getHealth();
        if (avatarHp < lowestHp) {
            lowestHp = avatarHp;
            target = gameState.aiAvatar;
        }

        for (Unit u : gameState.aiUnits) {
            int hp = gameState.getUnitHealth(u);
            if (hp < lowestHp) {
                lowestHp = hp;
                target = u;
            }
        }
        return target;
    }

    // Find free tile adjacent to any AI unit or avatar
    private Tile findAISummonTile(GameState gameState) {
        List<Unit> aiUnits = new ArrayList<>(gameState.aiUnits);
        aiUnits.add(0, gameState.aiAvatar); // checking avatar too

        for (Unit unit : aiUnits) {
            Tile unitTile = gameState.findTileContainingUnit(unit);
            if (unitTile == null)
                continue;

            int ux = unitTile.getTilex();
            int uy = unitTile.getTiley();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0)
                        continue;
                    int nx = ux + dx;
                    int ny = uy + dy;
                    if (!gameState.isWithinBoard(nx, ny))
                        continue;
                    if (gameState.isTileFree(nx, ny)) {
                        return gameState.board[nx][ny];
                    }
                }
            }
        }
        return null;
    }

    // STEP 2: Move Units
    private void moveUnits(ActorRef out, GameState gameState) {
        List<Unit> toMove = new ArrayList<>(gameState.aiUnits);
        toMove.add(gameState.aiAvatar);

        for (Unit unit : toMove) {
            if (gameState.isSummoningSick(unit))
                continue;

            if (gameState.hasUnitAttacked(unit))
                continue;

            Tile unitTile = gameState.findTileContainingUnit(unit);
            if (unitTile == null)
                continue;

            // Already adjacent to enemy, no need to move
            if (hasAdjacentEnemy(unitTile, gameState))
                continue;

            // Find best tile to move toward nearest enemy
            Tile bestTile = findBestMoveTile(unit, unitTile, gameState);
            if (bestTile == null)
                continue;

            // Execute move
            unitTile.setUnit(null);
            bestTile.setUnit(unit);
            unit.setPositionByTile(bestTile);
            gameState.markUnitAsMoved(unit);

            if (out != null) {
                BasicCommands.moveUnitToTile(out, unit, bestTile);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("[AI] Moved unit to ("
                    + bestTile.getTilex() + "," + bestTile.getTiley() + ")");
        }
    }

    private boolean hasAdjacentEnemy(Tile tile, GameState gameState) {
        int tx = tile.getTilex();
        int ty = tile.getTiley();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0)
                    continue;
                int nx = tx + dx;
                int ny = ty + dy;
                if (!gameState.isWithinBoard(nx, ny))
                    continue;
                Tile t = gameState.board[nx][ny];
                if (t != null && t.getUnit() != null) {
                    Unit u = t.getUnit();
                    if (u == gameState.humanAvatar || gameState.humanUnits.contains(u)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Tile findBestMoveTile(Unit unit, Tile unitTile, GameState gameState) {
        // Find nearest human unit/avatar
        Unit nearestEnemy = findNearestEnemy(unitTile, gameState);
        if (nearestEnemy == null)
            return null;

        Tile enemyTile = gameState.findTileContainingUnit(nearestEnemy);
        if (enemyTile == null)
            return null;

        int ux = unitTile.getTilex();
        int uy = unitTile.getTiley();

        // Valid move offsets-> 2 cardinal, 1 diagonal
        int[][] offsets = {
                { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
                { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 },
                { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
        };

        Tile bestTile = null;
        double bestDist = Double.MAX_VALUE;

        for (int[] offset : offsets) {
            int nx = ux + offset[0];
            int ny = uy + offset[1];
            if (!gameState.isWithinBoard(nx, ny))
                continue;
            if (!gameState.isTileFree(nx, ny))
                continue;

            // Distance to enemy from this tile
            double dist = Math.sqrt(
                    Math.pow(nx - enemyTile.getTilex(), 2) +
                            Math.pow(ny - enemyTile.getTiley(), 2));

            if (dist < bestDist) {
                bestDist = dist;
                bestTile = gameState.board[nx][ny];
            }
        }

        return bestTile;
    }

    private Unit findNearestEnemy(Tile fromTile, GameState gameState) {
        Unit nearest = null;
        double minDist = Double.MAX_VALUE;

        List<Unit> enemies = new ArrayList<>(gameState.humanUnits);
        enemies.add(gameState.humanAvatar);

        for (Unit enemy : enemies) {
            Tile t = gameState.findTileContainingUnit(enemy);
            if (t == null)
                continue;

            double dist = Math.sqrt(
                    Math.pow(fromTile.getTilex() - t.getTilex(), 2) +
                            Math.pow(fromTile.getTiley() - t.getTiley(), 2));

            if (dist < minDist) {
                minDist = dist;
                nearest = enemy;
            }
        }
        return nearest;
    }

    // STEP 3: Attack
    private void attackWithUnits(ActorRef out, GameState gameState) {
        List<Unit> attackers = new ArrayList<>(gameState.aiUnits);
        attackers.add(gameState.aiAvatar);

        for (Unit attacker : attackers) {
            if (gameState.isSummoningSick(attacker))
                continue;

            if (gameState.hasUnitAttacked(attacker))
                continue;

            Tile attackerTile = gameState.findTileContainingUnit(attacker);
            if (attackerTile == null)
                continue;

            // Find adjacent enemy-> priority: human avatar first
            Unit target = findAttackTarget(attackerTile, gameState);
            if (target == null)
                continue;

            Tile targetTile = gameState.findTileContainingUnit(target);
            if (targetTile == null)
                continue;

            // Play animations
            if (out != null) {
                BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.attack);
                BasicCommands.playUnitAnimation(out, target, UnitAnimationType.hit);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Deal damage to target
            int attackDmg = gameState.getUnitAttack(attacker);
            int targetHp = gameState.getUnitHealth(target) - attackDmg;

            if (target == gameState.humanAvatar) {
                gameState.humanPlayer.setHealth(targetHp);
                if (out != null) {
                    gameState.syncPlayerStatsUI(out);
                    BasicCommands.setUnitHealth(out, target, gameState.humanPlayer.getHealth());
                }
                // Win condition-> human avatar dead
                if (targetHp <= 0) {
                    if (out != null) {
                        BasicCommands.addPlayer1Notification(out, "You Lose!", 5);
                    }
                    // gameState.gameInitalised = false;
                    gameState.endGame(out, "You Lose!");
                    return;
                }
            } else {
                gameState.setUnitHealth(target, targetHp);
                if (out != null) {
                    BasicCommands.setUnitHealth(out, target, targetHp);
                }
                if (targetHp <= 0) {
                    gameState.removeUnitFromBoard(target, out);
                    if (out != null) {
                        BasicCommands.playUnitAnimation(out, target, UnitAnimationType.death);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        BasicCommands.deleteUnit(out, target);
                    }
                }
            }

            // Counter attack
            boolean targetAlive = (target == gameState.humanAvatar)
                    ? gameState.humanPlayer.getHealth() > 0
                    : gameState.getUnitHealth(target) > 0;

            if (targetAlive) {
                int counterDmg = gameState.getUnitAttack(target);
                int attackerHp = gameState.getUnitHealth(attacker) - counterDmg;

                if (out != null) {
                    BasicCommands.playUnitAnimation(out, target, UnitAnimationType.attack);
                    BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.hit);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (attacker == gameState.aiAvatar) {
                    gameState.aiPlayer.setHealth(attackerHp);
                    if (out != null) {
                        gameState.syncPlayerStatsUI(out);
                        BasicCommands.setUnitHealth(out, attacker, gameState.aiPlayer.getHealth());
                    }
                    // Zeal trigger
                    gameState.triggerZeal(out);
                } else {
                    gameState.setUnitHealth(attacker, attackerHp);
                    if (out != null) {
                        BasicCommands.setUnitHealth(out, attacker, attackerHp);
                    }
                    if (attackerHp <= 0) {
                        gameState.removeUnitFromBoard(attacker, out);
                        if (out != null) {
                            BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.death);
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            BasicCommands.deleteUnit(out, attacker);
                        }
                    }
                }
            }

            gameState.markUnitAsAttacked(attacker);
            System.out.println("[AI] Attacked with unit");
        }
    }

    private Unit findAttackTarget(Tile attackerTile, GameState gameState) {
        int tx = attackerTile.getTilex();
        int ty = attackerTile.getTiley();

        // Priority 1: human avatar
        Tile avatarTile = gameState.findTileContainingUnit(gameState.humanAvatar);
        if (avatarTile != null) {
            int dx = Math.abs(tx - avatarTile.getTilex());
            int dy = Math.abs(ty - avatarTile.getTiley());
            if (dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0)) {
                return gameState.humanAvatar;
            }
        }

        // Priority 2: lowest HP human unit
        Unit weakest = null;
        int lowestHp = Integer.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0)
                    continue;
                int nx = tx + dx;
                int ny = ty + dy;
                if (!gameState.isWithinBoard(nx, ny))
                    continue;
                Tile t = gameState.board[nx][ny];
                if (t == null || t.getUnit() == null)
                    continue;
                Unit u = t.getUnit();
                if (u == gameState.humanAvatar || gameState.humanUnits.contains(u)) {
                    int hp = gameState.getUnitHealth(u);
                    if (hp < lowestHp) {
                        lowestHp = hp;
                        weakest = u;
                    }
                }
            }
        }
        return weakest;
    }

    // STEP 4: End AI Turn
    private void endAITurn(ActorRef out, GameState gameState) {
        // Draw card for AI
        if (gameState.aiPlayer.deck != null && !gameState.aiPlayer.deck.isEmpty()) {
            if (gameState.aiPlayer.hand.size() < 6) {
                Card drawn = gameState.aiPlayer.deck.remove(0);
                gameState.aiPlayer.hand.add(drawn);
            }
        }

        // Reset attack/move flags
        gameState.resetUnitAttackFlags();

        // Switch to human turn
        gameState.humanTurn = true;
        gameState.turnNumber++;

        // Refresh human mana
        int mana = Math.min(9, gameState.turnNumber + 1);
        gameState.humanPlayer.setMana(mana);

        // Draw card for human
        if (gameState.humanPlayer.deck != null && !gameState.humanPlayer.deck.isEmpty()) {
            if (gameState.humanPlayer.hand.size() < 6) {
                Card drawn = gameState.humanPlayer.deck.remove(0);
                gameState.humanPlayer.hand.add(drawn);
                int pos = gameState.humanPlayer.hand.size();
                if (out != null) {
                    BasicCommands.drawCard(out, drawn, pos, 0);
                }
            }
        }

        if (out != null) {
            gameState.syncPlayerStatsUI(out);
            BasicCommands.addPlayer1Notification(out, "Your Turn!", 2);
        }

        System.out.println("AI Turn ended-> human turn starts, turn=" + gameState.turnNumber);
    }
}