package structures;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Tile;
import structures.basic.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * MovementEngine — handles all movement logic:
 * - Highlight valid move tiles (grey) and attack tiles (red)
 * - Execute a move (update board state + send command)
 * - Find best approach tile for move+attack
 * - AI helpers: hasAdjacentEnemy, findBestMoveTile, findNearestEnemy
 *
 * GameState sirf state rakhta hai.
 * TileClicked aur AITurn sirf routing karte hain.
 * Actual kaam yahan hota hai.
 */
public class MovementEngine {

    // HIGHLIGHT METHODS

    /**
     * SC-302: Highlight valid move tiles (grey) from a unit's position.
     * Also highlights adjacent enemies (red) from each reachable tile.
     *
     * Rules:
     * - Cardinal: up to 2 steps (intermediate must be free)
     * - Diagonal: 1 step only
     *
     * NOTE: Enemy tiles go into highlightedMoveTiles list too (for clearing),
     * but are NOT considered valid move destinations — only grey tiles are.
     */
    public static void highlightMoveRange(ActorRef out, GameState gs, int startX, int startY) {
        // Always clear previous highlights first
        clearMoveHighlights(out, gs);

        if (gs.board == null) return;

        int[][] offsets = {
            // cardinal 1-step
            {1,0}, {-1,0}, {0,1}, {0,-1},
            // cardinal 2-step
            {2,0}, {-2,0}, {0,2}, {0,-2},
            // diagonal 1-step
            {1,1}, {1,-1}, {-1,1}, {-1,-1}
        };

        for (int[] offset : offsets) {
            int nx = startX + offset[0];
            int ny = startY + offset[1];

            if (!gs.isWithinBoard(nx, ny)) continue;

            // 2-step cardinal: intermediate tile must be free
            if (Math.abs(offset[0]) == 2 || Math.abs(offset[1]) == 2) {
                int midX = startX + offset[0] / 2;
                int midY = startY + offset[1] / 2;
                if (!gs.isTileFree(midX, midY)) continue;
            }

            Tile t = gs.board[nx][ny];
            if (t == null) continue;

            if (gs.isTileFree(nx, ny)) {
                // Grey: valid move destination
                if (out != null) BasicCommands.drawTile(out, t, 1);
                gs.highlightedMoveTiles.add(t);

                // Also highlight adjacent enemies from this potential move tile (red)
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        int ex = nx + dx;
                        int ey = ny + dy;
                        if (!gs.isWithinBoard(ex, ey)) continue;
                        Tile et = gs.board[ex][ey];
                        if (et == null || et.getUnit() == null) continue;
                        if (!gs.isEnemyUnit(et.getUnit())) continue;
                        if (!gs.highlightedMoveTiles.contains(et)) {
                            if (out != null) BasicCommands.drawTile(out, et, 2);
                            gs.highlightedMoveTiles.add(et);
                        }
                    }
                }

            } else if (t.getUnit() != null && gs.isEnemyUnit(t.getUnit())) {
                // Red: directly reachable enemy tile (unit is 1 step away)
                if (!gs.highlightedMoveTiles.contains(t)) {
                    if (out != null) BasicCommands.drawTile(out, t, 2);
                    gs.highlightedMoveTiles.add(t);
                }
            }
        }

        System.out.println("[MovementEngine] highlighted=" + gs.highlightedMoveTiles.size()
            + " from (" + startX + "," + startY + ")");
    }

    /**
     * SC-302 (attack-only mode): Unit has already moved this turn.
     * Only highlight adjacent enemies in red (no grey move tiles).
     * Results go into highlightedMoveTiles so clearMoveHighlights() covers them.
     *
     * @return count of adjacent enemies found
     */
    public static int highlightAttackRange(ActorRef out, GameState gs, Unit unit) {
        Tile unitTile = gs.findTileContainingUnit(unit);
        if (unitTile == null) return 0;

        int ux = unitTile.getTilex();
        int uy = unitTile.getTiley();
        int count = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = ux + dx;
                int ny = uy + dy;
                if (!gs.isWithinBoard(nx, ny)) continue;
                Tile t = gs.board[nx][ny];
                if (t != null && t.getUnit() != null && gs.isEnemyUnit(t.getUnit())) {
                    if (out != null) BasicCommands.drawTile(out, t, 2);
                    gs.highlightedMoveTiles.add(t);
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Clear all highlighted move/attack tiles and reset the list.
     * Thread.sleep(5) per tile to avoid WebSocket buffer overflow.
     */
    public static void clearMoveHighlights(ActorRef out, GameState gs) {
        for (Tile t : gs.highlightedMoveTiles) {
            if (out != null) BasicCommands.drawTile(out, t, 0);
            try { Thread.sleep(5); } catch (InterruptedException e) { e.printStackTrace(); }
        }
        gs.highlightedMoveTiles.clear();
    }

    /**
     * Check if a given tile coordinate is in the current move highlight list.
     */
    public static boolean isHighlightedMoveTile(GameState gs, int x, int y) {
        if (!gs.isWithinBoard(x, y)) return false;
        Tile t = gs.board[x][y];
        return t != null && gs.highlightedMoveTiles.contains(t);
    }

    // EXECUTE MOVE

    /**
     * Execute a move: update board state + send moveUnitToTile command.
     * Marks unit as moved. Does NOT clear highlights — caller handles that.
     */
    public static void executeMove(ActorRef out, GameState gs, Unit unit, Tile targetTile) {
        Tile currentTile = gs.findTileContainingUnit(unit);
        if (currentTile != null) currentTile.setUnit(null);

        targetTile.setUnit(unit);
        unit.setPositionByTile(targetTile);
        gs.markUnitAsMoved(unit);

        if (out != null) {
            BasicCommands.moveUnitToTile(out, unit, targetTile);
        }

        System.out.println("[MovementEngine] executeMove to ("
            + targetTile.getTilex() + "," + targetTile.getTiley() + ")");
    }

    // APPROACH TILE FINDER (move+attack)

    /**
     * SC-303: Find the best adjacent free tile to approach a defender for attack.
     * Searches through highlightedMoveTiles to find a tile adjacent to the defender.
     *
     * @param gs         GameState
     * @param defenderTilex  target tile x
     * @param defenderTiley  target tile y
     * @return best Tile to move to, or null if none available
     */
    public static Tile findBestApproachTile(GameState gs, int defenderTilex, int defenderTiley) {
        Tile bestTile = null;
        int bestDist = Integer.MAX_VALUE;

        for (Tile moveTile : new ArrayList<>(gs.highlightedMoveTiles)) {
            if (moveTile.getUnit() != null) continue; // occupied tiles (red) skip karo

            int mdx = Math.abs(moveTile.getTilex() - defenderTilex);
            int mdy = Math.abs(moveTile.getTiley() - defenderTiley);

            // Must be adjacent (8-way) to defender
            if (mdx <= 1 && mdy <= 1 && !(mdx == 0 && mdy == 0)) {
                int dist = mdx + mdy;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestTile = moveTile;
                }
            }
        }
        return bestTile;
    }

    // AI MOVEMENT HELPERS (moved from AITurn)

    /**
     * Check if any human unit/avatar is adjacent (8-way) to the given tile.
     * AI uses this to skip moving units that are already in attack range.
     */
    public static boolean hasAdjacentEnemy(GameState gs, Tile tile) {
        int tx = tile.getTilex();
        int ty = tile.getTiley();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = tx + dx;
                int ny = ty + dy;
                if (!gs.isWithinBoard(nx, ny)) continue;
                Tile t = gs.board[nx][ny];
                if (t != null && t.getUnit() != null) {
                    Unit u = t.getUnit();
                    if (u == gs.humanAvatar || gs.humanUnits.contains(u)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Find the nearest human unit/avatar to a given tile (Euclidean distance).
     */
    public static Unit findNearestEnemy(GameState gs, Tile fromTile) {
        Unit nearest = null;
        double minDist = Double.MAX_VALUE;

        List<Unit> enemies = new ArrayList<>(gs.humanUnits);
        enemies.add(gs.humanAvatar);

        for (Unit enemy : enemies) {
            Tile t = gs.findTileContainingUnit(enemy);
            if (t == null) continue;
            double dist = Math.sqrt(
                Math.pow(fromTile.getTilex() - t.getTilex(), 2) +
                Math.pow(fromTile.getTiley() - t.getTiley(), 2)
            );
            if (dist < minDist) {
                minDist = dist;
                nearest = enemy;
            }
        }
        return nearest;
    }

    /**
     * Find the best tile to move toward the nearest enemy.
     * Uses same offset rules as highlightMoveRange (2 cardinal, 1 diagonal).
     * No intermediate tile check here — AI doesn't need visual highlights.
     */
    public static Tile findBestAIMoveTile(GameState gs, Unit unit, Tile unitTile) {
        Unit nearestEnemy = findNearestEnemy(gs, unitTile);
        if (nearestEnemy == null) return null;

        Tile enemyTile = gs.findTileContainingUnit(nearestEnemy);
        if (enemyTile == null) return null;

        int ux = unitTile.getTilex();
        int uy = unitTile.getTiley();

        int[][] offsets = {
            {1,0}, {-1,0}, {0,1}, {0,-1},
            {2,0}, {-2,0}, {0,2}, {0,-2},
            {1,1}, {1,-1}, {-1,1}, {-1,-1}
        };

        Tile bestTile = null;
        double bestDist = Double.MAX_VALUE;

        for (int[] offset : offsets) {
            int nx = ux + offset[0];
            int ny = uy + offset[1];
            if (!gs.isWithinBoard(nx, ny)) continue;
            if (!gs.isTileFree(nx, ny)) continue;

            double dist = Math.sqrt(
                Math.pow(nx - enemyTile.getTilex(), 2) +
                Math.pow(ny - enemyTile.getTiley(), 2)
            );
            if (dist < bestDist) {
                bestDist = dist;
                bestTile = gs.board[nx][ny];
            }
        }
        return bestTile;
    }
}