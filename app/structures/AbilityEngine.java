package structures;

import akka.actor.ActorRef;
import structures.basic.Tile;
import structures.basic.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * AbilityEngine — Step 3 of refactor plan.
 * Handles all unit ability triggers:
 *   Deathwatch  (Bad Omen, Shadow Watcher, Bloodmoon Priestess, Shadowdancer)
 *   Opening Gambit (Gloom Chaser, Nightsorrow Assassin, Silverguard Squire)
 *   Zeal        (Silverguard Knight)
 *   Wraithling Swarm spell cast
 *
 * GameState sirf state rakhta hai.
 * AbilityEngine sirf ability logic rakhta hai.
 *
 * Callers:
 *   CombatHandler  → triggerDeathwatch (after any unit dies)
 *   CombatHandler  → triggerZeal (after AI avatar takes damage)
 *   TileClicked    → triggerOpeningGambit (after human summon)
 *   AITurn         → triggerOpeningGambit (after AI summon)
 *   CardClicked    → castWraithlingSwarm
 *
 * GameState delegate methods are kept as one-line wrappers so existing
 * callers don't need to change until Step 5 cleanup.
 */
public class AbilityEngine {

    // DEATHWATCH, Triggered whenever ANY unit dies (called from CombatHandler / removeUnitFromBoard)

    public static void triggerDeathwatch(ActorRef out, GameState gs) {
        // Only human units have Deathwatch abilities in this ruleset
        List<Unit> snapshot = new ArrayList<>(gs.humanUnits);

        for (Unit unit : snapshot) {
            String name = gs.getUnitName(unit);
            if (name.isEmpty()) continue;

            switch (name.toLowerCase()) {

                case "bad_omen": {
                    int newAtk = gs.getUnitAttack(unit) + 1;
                    gs.setUnitAttack(unit, newAtk);
                    if (out != null) {
                        commands.BasicCommands.setUnitAttack(out, unit, newAtk);
                    }
                    System.out.println("[DEATHWATCH] Bad Omen +1 attack → " + newAtk);
                    break;
                }

                case "shadow_watcher": {
                    System.out.println("[DEBUG] Shadow Watcher triggered id=" + unit.getId());
                    int newAtk = gs.getUnitAttack(unit) + 1;
                    int newHp  = gs.getUnitHealth(unit) + 1;
                    gs.setUnitAttack(unit, newAtk);
                    gs.setUnitHealth(unit, newHp);
                    if (out != null) {
                        commands.BasicCommands.setUnitHealth(out, unit, newHp);
                        try { Thread.sleep(50); } catch (InterruptedException e) { e.printStackTrace(); }
                        commands.BasicCommands.setUnitAttack(out, unit, newAtk);
                    }
                    System.out.println("[DEATHWATCH] Shadow Watcher +1/+1");
                    break;
                }

                case "bloodmoon_priestess": {
                    Tile unitTile = gs.findTileContainingUnit(unit);
                    if (unitTile == null) continue;
                    Tile spawnTile = gs.getRandomAdjacentFreeTile(unitTile);
                    if (spawnTile == null) continue;
                    gs.spawnWraithling(out, spawnTile, true); // true = human side
                    System.out.println("[DEATHWATCH] Bloodmoon Priestess spawned Wraithling");
                    break;
                }

                case "shadowdancer": {
                    System.out.println("[DEBUG] Shadowdancer triggered id=" + unit.getId());
                    int aiHp   = gs.aiPlayer.getHealth() - 1;
                    int selfHp = gs.humanPlayer.getHealth() + 1;
                    gs.aiPlayer.setHealth(aiHp);
                    gs.humanPlayer.setHealth(selfHp);
                    if (out != null) {
                        commands.BasicCommands.setUnitHealth(out, gs.aiAvatar, aiHp);
                        commands.BasicCommands.setUnitHealth(out, gs.humanAvatar, selfHp);
                        gs.syncPlayerStatsUI(out);
                    }
                    System.out.println("[DEATHWATCH] Shadowdancer: 1 dmg AI + 1 heal human");
                    if (aiHp <= 0) {
                        gs.endGame(out, "You Win!");
                        return;
                    }
                    break;
                }

                default:
                    break;
            }
        }
    }

    // OPENING GAMBIT
    // Triggered immediately after a unit is summoned onto the board.
    // isHuman = true  -> human player summoned the unit
    // isHuman = false -> AI player summoned the unit

    public static void triggerOpeningGambit(ActorRef out, GameState gs,
                                             Unit summoned, Tile summonedTile,
                                             boolean isHuman) {
        String name = gs.getUnitName(summoned);
        if (name.isEmpty()) return;

        switch (name.toLowerCase()) {

            case "gloom_chaser": {
                // Spawn a Wraithling directly behind the summoned unit
                // Human -> x-1 (left), AI -> x+1 (right)
                int behindX = isHuman
                        ? summonedTile.getTilex() - 1
                        : summonedTile.getTilex() + 1;
                int behindY = summonedTile.getTiley();

                if (gs.isWithinBoard(behindX, behindY) && gs.isTileFree(behindX, behindY)) {
                    gs.spawnWraithling(out, gs.board[behindX][behindY], isHuman);
                    System.out.println("[OPENING GAMBIT] Gloom Chaser spawned Wraithling behind");
                } else {
                    System.out.println("[OPENING GAMBIT] Gloom Chaser: space occupied, no effect");
                }
                break;
            }

            case "nightsorrow_assassin": {
                // Destroy the first adjacent enemy unit that is below max health
                int tx = summonedTile.getTilex();
                int ty = summonedTile.getTiley();

                outer:
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = tx + dx;
                        int ny = ty + dy;
                        if (!gs.isWithinBoard(nx, ny)) continue;

                        Tile t = gs.board[nx][ny];
                        if (t == null || t.getUnit() == null) continue;

                        Unit target = t.getUnit();
                        boolean isEnemy = isHuman
                                ? (target == gs.aiAvatar  || gs.aiUnits.contains(target))
                                : (target == gs.humanAvatar || gs.humanUnits.contains(target));

                        if (!isEnemy) continue;

                        int currentHp = gs.getUnitHealth(target);
                        int maxHp     = gs.unitMaxHealth.getOrDefault(target, currentHp);

                        if (currentHp < maxHp) {
                            if (out != null) {
                                commands.BasicCommands.playUnitAnimation(
                                        out, target, structures.basic.UnitAnimationType.death);
                                try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }
                                commands.BasicCommands.deleteUnit(out, target);
                            }
                            gs.removeUnitFromBoard(target, out);
                            System.out.println("[OPENING GAMBIT] Nightsorrow Assassin destroyed damaged enemy");
                            break outer;
                        }
                    }
                }
                System.out.println("[OPENING GAMBIT] Nightsorrow Assassin: no damaged enemy adjacent");
                break;
            }

            case "silverguard_squire": {
                // +1/+1 to the allied unit directly in front OR behind the owning avatar
                Unit ownerAvatar = isHuman ? gs.humanAvatar : gs.aiAvatar;
                Tile avatarTile  = gs.findTileContainingUnit(ownerAvatar);
                if (avatarTile == null) break;

                int ax = avatarTile.getTilex();
                int ay = avatarTile.getTiley();
                int[] checkX = { ax - 1, ax + 1 };

                for (int nx : checkX) {
                    if (!gs.isWithinBoard(nx, ay)) continue;
                    Tile t = gs.board[nx][ay];
                    if (t == null || t.getUnit() == null) continue;

                    Unit target = t.getUnit();
                    boolean isAlly = isHuman
                            ? gs.humanUnits.contains(target)
                            : gs.aiUnits.contains(target);
                    if (!isAlly) continue;

                    int newAtk = gs.getUnitAttack(target) + 1;
                    int newHp  = gs.getUnitHealth(target) + 1;
                    gs.unitMaxHealth.put(target,
                            gs.unitMaxHealth.getOrDefault(target, newHp - 1) + 1);
                    gs.setUnitAttack(target, newAtk);
                    gs.setUnitHealth(target, newHp);

                    if (out != null) {
                        commands.BasicCommands.setUnitAttack(out, target, newAtk);
                        try { Thread.sleep(50); } catch (InterruptedException e) { e.printStackTrace(); }
                        commands.BasicCommands.setUnitHealth(out, target, newHp);
                    }
                    System.out.println("[OPENING GAMBIT] Silverguard Squire +1/+1 to "
                            + gs.getUnitName(target));
                }
                break;
            }

            default:
                break;
        }
    }

    // ZEAL
    // Triggered whenever the AI avatar takes damage.
    // Silverguard Knight gains +2 attack permanently.

    public static void triggerZeal(ActorRef out, GameState gs) {
        List<Unit> snapshot = new ArrayList<>(gs.aiUnits);
        for (Unit unit : snapshot) {
            String name = gs.getUnitName(unit);
            if ("silverguard_knight".equalsIgnoreCase(name)) {
                int newAtk = gs.getUnitAttack(unit) + 2;
                gs.setUnitAttack(unit, newAtk);
                if (out != null) {
                    commands.BasicCommands.setUnitAttack(out, unit, newAtk);
                }
                System.out.println("[ZEAL] Silverguard Knight +2 attack → " + newAtk);
            }
        }
    }

    // WRAITHLING SWARM (spell — human only)
    // Summons 3 Wraithlings on free tiles adjacent to friendly units/avatar.
    // Priority: avatar's adjacent tiles first, then other friendly units.

    public static void castWraithlingSwarm(ActorRef out, GameState gs) {
        int summoned = 0;

        List<Unit> friendlies = new ArrayList<>(gs.humanUnits);
        friendlies.add(0, gs.humanAvatar); // avatar tiles checked first

        outer:
        for (Unit friendly : friendlies) {
            Tile ft = gs.findTileContainingUnit(friendly);
            if (ft == null) continue;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (summoned >= 3) break outer;
                    if (dx == 0 && dy == 0) continue;

                    int nx = ft.getTilex() + dx;
                    int ny = ft.getTiley() + dy;
                    if (!gs.isWithinBoard(nx, ny)) continue;
                    if (!gs.isTileFree(nx, ny)) continue;

                    gs.spawnWraithling(out, gs.board[nx][ny], true);
                    summoned++;
                }
            }
        }

        if (summoned == 0 && out != null) {
            commands.BasicCommands.addPlayer1Notification(out, "No space for Wraithlings!", 2);
        }
        System.out.println("[WRAITHLING SWARM] spawned " + summoned + " wraithlings");
    }
}