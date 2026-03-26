package structures;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;

/**
 * Handles all combat logic — attack, counter-attack, death, win/lose
 */
public class CombatHandler {

    public static void executeAttack(ActorRef out, GameState gs, Unit attacker, Unit defender) {

        // Attack animations
        if (out != null) {
            BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.attack);
            BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.hit);
        }

        //PHASE 1: Attacker hits Defender
        int attackDmg = gs.getUnitAttack(attacker);
        int defenderHp = gs.getUnitHealth(defender) - attackDmg;
        defenderHp = Math.max(0, defenderHp);

        if (defender == gs.aiAvatar) {
            if (defenderHp <= 0) {
                // AI Avatar dead — You Win
                gs.aiPlayer.setHealth(0);
                if (out != null) {
                    gs.syncPlayerStatsUI(out);
                    BasicCommands.setUnitHealth(out, defender, 0);
                    BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.death);
                    try { Thread.sleep(150); } catch (InterruptedException e) { e.printStackTrace(); }
                    BasicCommands.deleteUnit(out, defender);
                }
                Tile defTile = gs.findTileContainingUnit(defender);
                if (defTile != null) defTile.setUnit(null);
                gs.aiUnits.remove(defender);
                gs.endGame(out, "You Win!");
                return;
            } else {
                // AI Avatar damaged but alive
                gs.aiPlayer.setHealth(defenderHp);
                if (out != null) {
                    gs.syncPlayerStatsUI(out);
                    BasicCommands.setUnitHealth(out, defender, gs.aiPlayer.getHealth());
                }
                gs.triggerZeal(out); // Zeal trigger
            }
        } else {
            // Regular unit
            gs.setUnitHealth(defender, defenderHp);
            if (out != null) BasicCommands.setUnitHealth(out, defender, defenderHp);
            if (defenderHp <= 0) {
                if (out != null) {
                    BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.death);
                    try { Thread.sleep(150); } catch (InterruptedException e) { e.printStackTrace(); }
                    BasicCommands.deleteUnit(out, defender);
                }
                gs.removeUnitFromBoard(defender, out);
            }
        }

        // PHASE 2: Counter Attack
        boolean defenderAlive = (defender == gs.aiAvatar)
                ? gs.aiPlayer.getHealth() > 0
                : gs.getUnitHealth(defender) > 0;

        if (defenderAlive) {
            int counterDmg = gs.getUnitAttack(defender);
            int attackerHp = gs.getUnitHealth(attacker) - counterDmg;
            attackerHp = Math.max(0, attackerHp);

            if (out != null) {
                BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.attack);
                BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.hit);
            }

            if (attacker == gs.humanAvatar) {
                // Human Avatar takes damage
                gs.humanPlayer.setHealth(attackerHp);
                if (out != null) {
                    gs.syncPlayerStatsUI(out);
                    BasicCommands.setUnitHealth(out, attacker, gs.humanPlayer.getHealth());
                }
                if (attackerHp <= 0) {
                    gs.endGame(out, "You Lose!");
                    return;
                }
            } else if (attacker == gs.aiAvatar) {
                // AI Avatar counter takes damage (rare case)
                gs.aiPlayer.setHealth(attackerHp);
                if (out != null) {
                    gs.syncPlayerStatsUI(out);
                    BasicCommands.setUnitHealth(out, attacker, gs.aiPlayer.getHealth());
                }
                gs.triggerZeal(out);
            } else {
                // Regular unit counter
                gs.setUnitHealth(attacker, attackerHp);
                if (out != null) BasicCommands.setUnitHealth(out, attacker, attackerHp);
                if (attackerHp <= 0) {
                    if (out != null) {
                        BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.death);
                        try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }
                        BasicCommands.deleteUnit(out, attacker);
                    }
                    gs.removeUnitFromBoard(attacker, out);
                }
            }
        }

        gs.markUnitAsAttacked(attacker);
        System.out.println("[COMBAT] executeAttack complete");
    }
}