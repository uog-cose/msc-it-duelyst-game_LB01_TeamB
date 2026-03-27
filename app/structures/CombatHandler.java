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

    private static void triggerHornOnAvatarHit(ActorRef out, GameState gs, Unit attacker, Unit defender) {
        if (attacker != gs.humanAvatar) {
            return;
        }
        if (gs.humanHornDurability <= 0) {
            return;
        }
        if (defender == null) {
            return;
        }

        Tile avatarTile = gs.findTileContainingUnit(attacker);
        if (avatarTile == null) {
            return;
        }

        Tile spawnTile = gs.getRandomAdjacentFreeTile(avatarTile);
        if (spawnTile == null) {
            System.out.println("[HORN] avatar dealt damage but no free adjacent tile for Wraithling");
            return;
        }

        gs.spawnWraithling(out, spawnTile, true);
        gs.humanHornDurability--;
        System.out.println("[HORN] triggered, spawned Wraithling, durability=" + gs.humanHornDurability);

        if (gs.humanHornDurability <= 0) {
            gs.humanHornDurability = 0;
            System.out.println("[HORN] expired");
        }
    }

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

        if (attackDmg > 0) {
            triggerHornOnAvatarHit(out, gs, attacker, defender);
        }

        // if (defender == gs.aiAvatar) {
        //     if (defenderHp <= 0) {
        //         // AI Avatar dead — You Win
        //         gs.aiPlayer.setHealth(0);
        //         if (out != null) {
        //             // gs.syncPlayerStatsUI(out);
        //             BasicCommands.setPlayer2Health(out, gs.aiPlayer);
        //             BasicCommands.setUnitHealth(out, defender, 0);
        //             BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.death);
        //             try { Thread.sleep(150); } catch (InterruptedException e) { e.printStackTrace(); }
        //             BasicCommands.deleteUnit(out, defender);
        //         }
        //         Tile defTile = gs.findTileContainingUnit(defender);
        //         if (defTile != null) defTile.setUnit(null);
        //         gs.aiUnits.remove(defender);
        //         gs.endGame(out, "You Win!");
        //         return;
        //     } else {
        //         // AI Avatar damaged but alive
        //         gs.aiPlayer.setHealth(defenderHp);
        //         if (out != null) {
        //             // gs.syncPlayerStatsUI(out);
        //             BasicCommands.setPlayer2Health(out, gs.aiPlayer);
        //             BasicCommands.setUnitHealth(out, defender, gs.aiPlayer.getHealth());
        //         }
        //         gs.triggerZeal(out); // Zeal trigger
        //     }
        // } else {
        //     // Regular unit
        //     gs.setUnitHealth(defender, defenderHp);
        //     if (out != null) BasicCommands.setUnitHealth(out, defender, defenderHp);
        //     if (defenderHp <= 0) {
        //         if (out != null) {
        //             BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.death);
        //             try { Thread.sleep(150); } catch (InterruptedException e) { e.printStackTrace(); }
        //             BasicCommands.deleteUnit(out, defender);
        //         }
        //         gs.removeUnitFromBoard(defender, out);
        //     }
        // }

        if (defender == gs.aiAvatar) {
            if (defenderHp <= 0) {
                gs.aiPlayer.setHealth(0);
                if (out != null) {
                    BasicCommands.setPlayer2Health(out, gs.aiPlayer);
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
                gs.aiPlayer.setHealth(defenderHp);
                if (out != null) {
                    BasicCommands.setPlayer2Health(out, gs.aiPlayer);
                    BasicCommands.setUnitHealth(out, defender, gs.aiPlayer.getHealth());
                }
                gs.triggerZeal(out);
            }
        } else if (defender == gs.humanAvatar) {
            // NEW BLOCK: human avatar is the defender (AI attacking human directly)
            if (defenderHp <= 0) {
                gs.humanPlayer.setHealth(0);
                if (out != null) {
                    BasicCommands.setPlayer1Health(out, gs.humanPlayer);
                    BasicCommands.setUnitHealth(out, defender, 0);
                }
                gs.endGame(out, "You Lose!");
                return;
            } else {
                gs.humanPlayer.setHealth(defenderHp);
                if (out != null) {
                    BasicCommands.setPlayer1Health(out, gs.humanPlayer);   // header
                    BasicCommands.setUnitHealth(out, defender, defenderHp); // board sprite
                }
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
        // boolean defenderAlive = (defender == gs.aiAvatar)
      
        //         ? gs.aiPlayer.getHealth() > 0
        //         : gs.getUnitHealth(defender) > 0;
        boolean defenderAlive;
if (defender == gs.aiAvatar) {
    defenderAlive = gs.aiPlayer.getHealth() > 0;
} else if (defender == gs.humanAvatar) {
    defenderAlive = gs.humanPlayer.getHealth() > 0;
} else {
    defenderAlive = gs.getUnitHealth(defender) > 0;
}

        System.out.println("[COMBAT] defenderAlive=" + defenderAlive + " defender==" + (defender == gs.aiAvatar ? "aiAvatar" : "unit"));

        Tile attackerTileAfterHit = gs.findTileContainingUnit(attacker);
        Tile defenderTileAfterHit = gs.findTileContainingUnit(defender);
        boolean counterAllowed = defenderAlive;

        if (counterAllowed && attackerTileAfterHit != null && defenderTileAfterHit != null) {
            int counterDx = Math.abs(attackerTileAfterHit.getTilex() - defenderTileAfterHit.getTilex());
            int counterDy = Math.abs(attackerTileAfterHit.getTiley() - defenderTileAfterHit.getTiley());
            counterAllowed = counterDx <= 1 && counterDy <= 1 && !(counterDx == 0 && counterDy == 0);
        }

        if (counterAllowed) {
            System.out.println("[COMBAT] Counter attack firing, counterDmg="
                    + gs.getUnitAttack(defender));
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
                System.out.println("[COMBAT] attackerHp=" + attackerHp + " humanPlayer.getHealth()=" + gs.humanPlayer.getHealth());
                if (out != null) {
                    // gs.syncPlayerStatsUI(out);
                    // BasicCommands.setPlayer1Health(out, gs.humanPlayer);
                    BasicCommands.setPlayer1Health(out, gs.humanPlayer);
                    try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }
                    // BasicCommands.setUnitHealth(out, attacker, gs.humanPlayer.getHealth());
                    BasicCommands.setUnitHealth(out, attacker, attackerHp);
                }
                if (attackerHp <= 0) {
                    gs.endGame(out, "You Lose!");
                    return;
                }
            } else if (attacker == gs.aiAvatar) {
                // AI Avatar counter takes damage (rare case)
                gs.aiPlayer.setHealth(attackerHp);
                if (out != null) {
                    // gs.syncPlayerStatsUI(out);

                    BasicCommands.setPlayer2Health(out, gs.aiPlayer);
                    BasicCommands.setUnitHealth(out, attacker, gs.aiPlayer.getHealth());
                }
                gs.triggerZeal(out);
                if (attackerHp <= 0) {
                    gs.endGame(out, "You Win!");
                    return;
                }
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

        if (defenderAlive && !counterAllowed) {
            System.out.println("[COMBAT] Counter attack skipped: defender not in counter range");
        }

        gs.markUnitAsAttacked(attacker);
        System.out.println("[COMBAT] executeAttack complete");
    }
}