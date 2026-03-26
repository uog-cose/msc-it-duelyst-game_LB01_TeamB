package structures;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;
import utils.BasicObjectBuilders;

/**
 * SpellEngine — Step 4 of refactor plan.
 * Handles all spell logic:
 *   Highlight valid targets    (Dark Terminus, True Strike, Sundrop Elixir)
 *   Human spell cast           (called from TileClicked)
 *   AI spell cast              (called from AITurn)
 *
 * Responsibilities:
 *   - Target validation
 *   - Effect application (damage / kill / heal / wraithling spawn)
 *   - UI updates for the affected unit
 *
 * NOT responsible for:
 *   - Mana deduction          (caller handles this)
 *   - Hand removal            (caller handles this)
 *   - Card UI refresh         (caller handles this)
 *   - syncPlayerStatsUI       (caller handles this after cast)
 *
 * Callers:
 *   GameState  → highlightValidTargets  (thin delegate wrapper)
 *   TileClicked → castSpell             (human cast after tile click)
 *   AITurn      → aiCastSpell           (AI cast, returns true if cast happened)
 */
public class SpellEngine {

    // HIGHLIGHT VALID TARGETS
    // Called via GameState.highlightValidSpellTargets() wrapper.
    // Populates gs.highlightedSpellTiles and gs.spellTileGridByUiCoords.
    // Returns count of valid target tiles found.

    public static int highlightValidTargets(ActorRef out, GameState gs, Card spellCard) {
        gs.clearSpellTileHighlights(out);

        if (spellCard == null) return 0;

        String spellName = gs.getCardName(spellCard);
        int count = 0;

        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 5; y++) {
                Tile t = gs.board[x][y];
                if (t == null || t.getUnit() == null) continue;

                Unit u = t.getUnit();
                boolean valid = false;

                if ("Dark Terminus".equalsIgnoreCase(spellName)) {
                    // Enemy creatures only — NOT the avatar
                    valid = gs.aiUnits.contains(u);

                } else if ("True Strike".equalsIgnoreCase(spellName)) {
                    // All enemies — creatures + avatar
                    valid = gs.aiUnits.contains(u) || u == gs.aiAvatar;

                } else if ("Sundrop Elixir".equalsIgnoreCase(spellName)) {
                    // All friendlies — creatures + avatar
                    valid = gs.humanUnits.contains(u) || u == gs.humanAvatar;
                }

                if (valid) {
                    if (out != null) BasicCommands.drawTile(out, t, 1);
                    gs.highlightedSpellTiles.add(t);

                    int uiX = t.getTilex();
                    int uiY = t.getTiley();
                    if (uiX >= 0 && uiX < gs.spellTileGridByUiCoords.length
                            && uiY >= 0 && uiY < gs.spellTileGridByUiCoords[0].length) {
                        gs.spellTileGridByUiCoords[uiX][uiY] = true;
                    }
                    count++;
                }
            }
        }

        System.out.println("[SpellEngine] highlighted " + count + " targets for " + spellName);
        return count;
    }

    // HUMAN CAST
    // Called from TileClicked after validation (tile highlighted, unit present,
    // mana check already done by caller).
    // Applies the spell effect + updates the target unit's UI.
    // Does NOT deduct mana or remove card — caller does that.

    public static void castSpell(ActorRef out, GameState gs,
                                  String spellName, Unit targetUnit, Tile targetTile) {

        if ("Dark Terminus".equalsIgnoreCase(spellName)) {
            castDarkTerminus(out, gs, targetUnit, targetTile);

        } else if ("True Strike".equalsIgnoreCase(spellName)) {
            castTrueStrike(out, gs, targetUnit);

        } else if ("Sundrop Elixir".equalsIgnoreCase(spellName)) {
            castSundropElixir(out, gs, targetUnit);

        } else {
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "Spell not implemented: " + spellName, 2);
            }
            System.out.println("[SpellEngine] Unknown spell: " + spellName);
        }
    }

    // AI CAST
    // Called from AITurn.playCards() for each non-creature card.
    // Finds its own target, applies effect, updates UI.
    // Returns true  → spell was cast, caller should deduct mana + remove card.
    // Returns false → no valid target found, card skipped.

    public static boolean aiCastSpell(ActorRef out, GameState gs, Card card) {
        String spellName = gs.getCardName(card);

        if ("Truestrike".equalsIgnoreCase(spellName) || "True Strike".equalsIgnoreCase(spellName)) {
            Unit target = findAITrueStrikeTarget(gs);
            if (target == null) {
                System.out.println("[SpellEngine] AI True Strike: no valid target");
                return false;
            }
            castTrueStrike(out, gs, target);
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "Enemy cast True Strike!", 2);
                try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }
            }
            System.out.println("[SpellEngine] AI cast True Strike on " + gs.getUnitName(target));
            return true;

        } else if ("Sundrop Elixir".equalsIgnoreCase(spellName)) {
            Unit target = findAISundropElixirTarget(gs);
            if (target == null) {
                System.out.println("[SpellEngine] AI Sundrop Elixir: no valid target");
                return false;
            }
            castSundropElixir(out, gs, target);
            if (out != null) {
                BasicCommands.addPlayer1Notification(out, "Enemy cast Sundrop Elixir!", 2);
                try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }
            }
            System.out.println("[SpellEngine] AI cast Sundrop Elixir on " + gs.getUnitName(target));
            return true;
        }

        System.out.println("[SpellEngine] AI: unrecognised spell " + spellName);
        return false;
    }


    // PRIVATE — SPELL EFFECTS
    // Each method applies the effect and sends the UI update for the target.
    // Win/lose conditions checked here too (same as TileClicked did inline).

    /**
     * Dark Terminus: destroy target enemy creature, spawn Wraithling on its tile.
     * Note: avatar is not a valid target (enforced by highlightValidTargets).
     */
    private static void castDarkTerminus(ActorRef out, GameState gs,
                                          Unit targetUnit, Tile targetTile) {
        if (out != null) {
            BasicCommands.playUnitAnimation(out, targetUnit, UnitAnimationType.death);
            try { Thread.sleep(150); } catch (InterruptedException e) { e.printStackTrace(); }
        }
        gs.removeUnitFromBoard(targetUnit, out);
        if (out != null) BasicCommands.deleteUnit(out, targetUnit);

        // Wraithling spawns on the now-empty tile
        if (targetTile != null && targetTile.getUnit() == null) {
            Unit wraithling = BasicObjectBuilders.loadUnit(
                    utils.StaticConfFiles.wraithling, gs.nextUnitId++, Unit.class);
            targetTile.setUnit(wraithling);
            wraithling.setPositionByTile(targetTile);
            gs.humanUnits.add(wraithling);
            gs.setUnitHealth(wraithling, 1);
            gs.setUnitAttack(wraithling, 1);
            if (out != null) {
                BasicCommands.drawUnit(out, wraithling, targetTile);
                try { Thread.sleep(50); } catch (InterruptedException e) { e.printStackTrace(); }
                BasicCommands.setUnitHealth(out, wraithling, 1);
                BasicCommands.setUnitAttack(out, wraithling, 1);
            }
        }
        System.out.println("[SpellEngine] Dark Terminus: destroyed + spawned Wraithling");
    }

    /**
     * True Strike: deal 2 damage to target. Handles avatar and regular unit.
     * Triggers death + Deathwatch if unit drops to 0.
     * Triggers win condition if it was the AI avatar.
     */
    private static void castTrueStrike(ActorRef out, GameState gs, Unit targetUnit) {
        int newHp = gs.damageTarget(targetUnit, 2);

        // UI update
        if (targetUnit == gs.aiAvatar) {
            if (out != null) {
                gs.syncPlayerStatsUI(out);
                BasicCommands.setUnitHealth(out, targetUnit, gs.aiPlayer.getHealth());
            }
        } else if (targetUnit == gs.humanAvatar) {
            if (out != null) {
                gs.syncPlayerStatsUI(out);
                BasicCommands.setUnitHealth(out, targetUnit, gs.humanPlayer.getHealth());
            }
        } else {
            if (out != null) BasicCommands.setUnitHealth(out, targetUnit, newHp);
        }

        if (newHp <= 0) {
            if (out != null) {
                BasicCommands.playUnitAnimation(out, targetUnit, UnitAnimationType.death);
                try { Thread.sleep(150); } catch (InterruptedException e) { e.printStackTrace(); }
                BasicCommands.deleteUnit(out, targetUnit);
            }
            gs.removeUnitFromBoard(targetUnit, out);

            if (targetUnit == gs.aiAvatar) {
                gs.endGame(out, "You Win!");
            } else if (targetUnit == gs.humanAvatar) {
                gs.endGame(out, "You Lose!");
            }
        }
        System.out.println("[SpellEngine] True Strike: " + gs.getUnitName(targetUnit) + " → " + newHp + " hp");
    }

    /**
     * Sundrop Elixir: heal target by 5, capped at max health.
     * Handles avatar and regular unit.
     */
    private static void castSundropElixir(ActorRef out, GameState gs, Unit targetUnit) {
        gs.healTarget(targetUnit, 5);

        // UI update
        if (targetUnit == gs.humanAvatar) {
            if (out != null) {
                gs.syncPlayerStatsUI(out);
                BasicCommands.setUnitHealth(out, targetUnit, gs.humanPlayer.getHealth());
            }
        } else if (targetUnit == gs.aiAvatar) {
            if (out != null) {
                gs.syncPlayerStatsUI(out);
                BasicCommands.setUnitHealth(out, targetUnit, gs.aiPlayer.getHealth());
            }
        } else {
            if (out != null) {
                BasicCommands.setUnitHealth(out, targetUnit, gs.getUnitHealth(targetUnit));
            }
        }
        System.out.println("[SpellEngine] Sundrop Elixir: healed " + gs.getUnitName(targetUnit));
    }

    // PRIVATE — AI TARGET FINDERS

    /**
     * AI True Strike target: lowest HP human unit or avatar.
     * Prefers killing a unit over damaging the avatar (lowest HP wins).
     */
    private static Unit findAITrueStrikeTarget(GameState gs) {
        Unit target = null;
        int lowestHp = Integer.MAX_VALUE;

        // Check avatar
        int avatarHp = gs.humanPlayer.getHealth();
        if (avatarHp < lowestHp) {
            lowestHp = avatarHp;
            target = gs.humanAvatar;
        }

        // Check all human units
        for (Unit u : gs.humanUnits) {
            int hp = gs.getUnitHealth(u);
            if (hp < lowestHp) {
                lowestHp = hp;
                target = u;
            }
        }
        return target;
    }

    /**
     * AI Sundrop Elixir target: lowest HP AI unit or avatar,
     * but only if they are actually damaged (current < max health).
     * No point healing a full-health unit.
     */
    private static Unit findAISundropElixirTarget(GameState gs) {
        Unit target = null;
        int lowestHp = Integer.MAX_VALUE;

        // Check avatar
        int avatarHp = gs.aiPlayer.getHealth();
        int avatarMax = 20; // avatars start at 20, no permanent buff
        if (avatarHp < avatarMax && avatarHp < lowestHp) {
            lowestHp = avatarHp;
            target = gs.aiAvatar;
        }

        // Check all AI units
        for (Unit u : gs.aiUnits) {
            int currentHp = gs.getUnitHealth(u);
            int maxHp = gs.unitMaxHealth.getOrDefault(u, currentHp);
            if (currentHp < maxHp && currentHp < lowestHp) {
                lowestHp = currentHp;
                target = u;
            }
        }
        return target;
    }
}