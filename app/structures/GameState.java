package structures;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import java.util.HashMap;
import java.util.Map;

/**
 * This class can be used to hold information about the on-going game.
 * Its created with the GameActor.
 * @author Dr. Richard McCreadie
 *
 */
public class GameState {

    public boolean gameInitalised = false;
    public boolean something = false;
    public long actionSeq = 0;
    public int turnNumber = 1;
    public boolean humanTurn = true;

    // Players (stats)
    public Player humanPlayer = new Player();
    public Player aiPlayer = new Player();

    // Avatars
    public Unit humanAvatar = null;
    public Unit aiAvatar = null;
    // Runtime HP tracking for non-avatar units (avatars use Player health)
    public Map<Unit, Integer> unitHealth = new HashMap<>();
    public Map<Unit, Integer> unitAttack = new HashMap<>();
    public Map<Unit, Boolean> unitHasAttackedThisTurn = new HashMap<>();
    public Map<Unit, Boolean> unitHasMovedThisTurn = new HashMap<>();
    // [SC-505] List to keep track of currently highlighted tiles on the board
    public List<Tile> highlightedTiles = new ArrayList<>();

    // Units currently owned by each side (excluding avatars)
    public List<Unit> humanUnits = new ArrayList<>();
    public List<Unit> aiUnits = new ArrayList<>();

    // Card-play state used for SC-201 / SC-205
    public Card selectedCard = null;
    public int selectedHandPosition = -1;
    public boolean[][] highlightedSummonTilesByUiCoords = new boolean[10][6];
    public int nextUnitId = 1000;

    // SC-302: Currently selected unit for movement (null if none selected)
    public Unit selectedUnit = null;

    // Highlight tracking for TWO separate lists, separate rules
  
    // SC-201 summon: tiles adjacent (8-way) to any friendly unit/avatar
    // Cleared when card is unselected or summon completes.
    public List<Tile> highlightedSummonTiles = new ArrayList<>();
 
    // SC-302 movement: 2-step cardinal + 1-step diagonal from selected unit
    // Cleared when unit is deselected or move completes.
    public List<Tile> highlightedMoveTiles = new ArrayList<>();
    
    // Spell target highlighting: tiles valid for the currently selected spell
    public List<Tile> highlightedSpellTiles = new ArrayList<>();
    public boolean[][] spellTileGridByUiCoords = new boolean[10][6];
 
    // Fast O(1) boolean grid for summon tile lookup during TileClicked validation
    public boolean[][] summonTileGrid = new boolean[9][5];
    public boolean[][] summonTileGridByUiCoords = new boolean[10][6];

    // 9x5 grid for the game board (SC-102)
    public Tile[][] board = new Tile[9][5];

    // initialize board array only (tiles will be created in Initialize.java)
    public void initBoardArray() {
        board = new Tile[9][5];
        summonTileGrid = new boolean[9][5];
        summonTileGridByUiCoords = new boolean[10][6];
        spellTileGridByUiCoords = new boolean[10][6];

        highlightedSummonTiles.clear();
        highlightedMoveTiles.clear();
        highlightedSpellTiles.clear();
        unitHealth.clear();
        unitAttack.clear();
        unitHasAttackedThisTurn.clear();

        selectedUnit = null;
        selectedCard = null;
        selectedHandPosition = -1;

    }

    // Check if the given coordinates are inside the board limits
    public boolean isWithinBoard(int x, int y) {
        return x >= 0 && x < 9 && y >= 0 && y < 5;
    }

    // Check if the tile at (x,y) is empty
    public boolean isTileFree(int x, int y) {
        if (!isWithinBoard(x, y)) {
            return false;
        }
        return board[x][y] != null && board[x][y].getUnit() == null;
    }

    // Get tile at coordinates
    public Tile getTile(int x, int y) {
        if (!isWithinBoard(x, y)) {
            return null;
        }
        return board[x][y];
    }

    public Tile findTileContainingUnit(Unit unit) {
        if (unit == null) return null;
        for (int x = 0; x < 9; x++)
            for (int y = 0; y < 5; y++)
                if (board[x][y] != null && board[x][y].getUnit() == unit)
                    return board[x][y];
        return null;
    }

    public boolean isEnemyUnit(Unit unit) {
        return unit == aiAvatar || aiUnits.contains(unit);
    }

    // SC-302: Movement highlighting
    // Rules: up to 2 tiles in cardinal directions, 1 tile diagonal
    public void highlightValidMoveTiles(ActorRef out, int startX, int startY) {
        // Always clear previous move highlights before showing new ones
        clearMoveTileHighlights(out);
 
        if (board == null) return;
 
        int[][] offsets = {
            // cardinal 1 step
            {  1,  0 }, { -1,  0 }, {  0,  1 }, {  0, -1 },
            // cardinal 2 steps
            {  2,  0 }, { -2,  0 }, {  0,  2 }, {  0, -2 },
            // diagonal 1 step only
            {  1,  1 }, {  1, -1 }, { -1,  1 }, { -1, -1 }
        };
 
        for (int[] offset : offsets) {
            int nx = startX + offset[0];
            int ny = startY + offset[1];
            if (!isWithinBoard(nx, ny)) continue;
        
            Tile t = board[nx][ny];
            if (t == null) continue;
        
            if (isTileFree(nx, ny)) {
                BasicCommands.drawTile(out, t, 1); // grey- move tile
                highlightedMoveTiles.add(t);
            } else if (t.getUnit() != null && isEnemyUnit(t.getUnit())) {
                BasicCommands.drawTile(out, t, 2); // red-  enemy in range
                highlightedMoveTiles.add(t);
            }
        }
        System.out.println("[SC-302] Move tiles highlighted: " + highlightedMoveTiles.size()
                + " from (" + startX + "," + startY + ")");
    }

    public int highlightValidAttackTiles(ActorRef out, Unit unit) {
        Tile unitTile = findTileContainingUnit(unit);
        if (unitTile == null) return 0;
    
        int ux = unitTile.getTilex();
        int uy = unitTile.getTiley();
        int count = 0;
    
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = ux + dx;
                int ny = uy + dy;
                if (!isWithinBoard(nx, ny)) continue;
    
                Tile t = board[nx][ny];
                if (t != null && t.getUnit() != null && isEnemyUnit(t.getUnit())) {
                    BasicCommands.drawTile(out, t, 2); //only red for enemy tile
                    highlightedMoveTiles.add(t);
                    count++;
                }
            }
        }
        return count;
    }

    // Clear only movement range highlights 
    public void clearMoveTileHighlights(ActorRef out) {
        for (Tile t : highlightedMoveTiles) {
            if (out != null) {
                BasicCommands.drawTile(out, t, 0);
            }
            try { Thread.sleep(5); } catch (InterruptedException e) { e.printStackTrace(); }
        }
        highlightedMoveTiles.clear();
    }

    public boolean isHighlightedMoveTile(int x, int y) {
        if (!isWithinBoard(x, y)) return false;
    
        Tile t = board[x][y];
        if (t == null) return false;
    
        return highlightedMoveTiles.contains(t);
    }

    // SC-201: Summon tile highlighting
    // Rules: 8-way adjacent to ALL friendly units + avatar, free tiles only
 
    public int highlightValidSummonTiles(ActorRef out) {
        // Clear previous summon highlights before showing new ones
        clearSummonTileHighlights(out);
 
        int count = 0;
        count += highlightAdjacentFreeTilesAroundUnit(out, humanAvatar);
        for (Unit unit : humanUnits) {
            count += highlightAdjacentFreeTilesAroundUnit(out, unit);
        }
        System.out.println("SC-201 Summon tiles highlighted: " + count);
        return count;
    }

    private int highlightAdjacentFreeTilesAroundUnit(ActorRef out, Unit unit) {
        if (unit == null) return 0;
 
        // Find unit position on board
        int originX = -1, originY = -1;
        outer:
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 5; y++) {
                if (board[x][y] != null && board[x][y].getUnit() == unit) {
                    originX = x;
                    originY = y;
                    break outer;
                }
            }
        }
        if (originX == -1) return 0;
 
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = originX + dx;
                int ny = originY + dy;
 
                if (!isWithinBoard(nx, ny)) continue;
                if (summonTileGrid[nx][ny]) continue; // already highlighted, skip duplicate
                if (!isTileFree(nx, ny)) continue;
 
                Tile t = board[nx][ny];
                BasicCommands.drawTile(out, t, 1);
                highlightedSummonTiles.add(t);
                summonTileGrid[nx][ny] = true;
 
                int uiX = t.getTilex();
                int uiY = t.getTiley();
                if (uiX >= 0 && uiX < summonTileGridByUiCoords.length
                        && uiY >= 0 && uiY < summonTileGridByUiCoords[0].length) {
                    summonTileGridByUiCoords[uiX][uiY] = true;
                }
                System.out.println("[SC-201] summon highlight board=("
                        + nx + "," + ny + ") ui=(" + uiX + "," + uiY + ")");
                count++;
            }
        }
        return count;
    }

    //Clear only summon highlights 
     public void clearSummonTileHighlights(ActorRef out) {
        for (Tile t : highlightedSummonTiles) {
            if (out != null) {
                BasicCommands.drawTile(out, t, 0);
            }
            try { Thread.sleep(5); } catch (InterruptedException e) { e.printStackTrace(); }
        }
        highlightedSummonTiles.clear();
        summonTileGrid = new boolean[9][5];
        summonTileGridByUiCoords = new boolean[10][6];
    }

    public int highlightValidSpellTargets(ActorRef out, Card spellCard) {
        clearSpellTileHighlights(out);
    
        if (spellCard == null) return 0;
    
        String spellName = getCardName(spellCard);
        int count = 0;
    
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 5; y++) {
                Tile t = board[x][y];
                if (t == null || t.getUnit() == null) continue;
    
                Unit u = t.getUnit();
                boolean valid = false;
    
                if ("Dark Terminus".equalsIgnoreCase(spellName)) {
                    valid = aiUnits.contains(u); // only enemy creatures, not on  avatar
                } else if ("True Strike".equalsIgnoreCase(spellName)) {
                    valid = aiUnits.contains(u) || u == aiAvatar; // all enemy -> creatures + avatar
                } else if ("Sundrop Elixir".equalsIgnoreCase(spellName)) {
                    valid = humanUnits.contains(u) || u == humanAvatar; // all friendly 
                }
    
                if (valid) {
                    if (out != null) {
                        BasicCommands.drawTile(out, t, 1);
                    }
                    highlightedSpellTiles.add(t);
    
                    int uiX = t.getTilex();
                    int uiY = t.getTiley();
                    if (uiX >= 0 && uiX < spellTileGridByUiCoords.length
                            && uiY >= 0 && uiY < spellTileGridByUiCoords[0].length) {
                        spellTileGridByUiCoords[uiX][uiY] = true;
                    }
                    count++;
                }
            }
        }
    
        return count;
    }
    
    public void clearSpellTileHighlights(ActorRef out) {
        for (Tile t : highlightedSpellTiles) {
            if (out != null) {
                BasicCommands.drawTile(out, t, 0);
            }
            try { Thread.sleep(5); } catch (InterruptedException e) { e.printStackTrace(); }
        }
        highlightedSpellTiles.clear();
        spellTileGridByUiCoords = new boolean[10][6];
    }
    
    public boolean isHighlightedSpellTileByUiCoords(int rawTilex, int rawTiley) {
        return rawTilex >= 0
                && rawTilex < spellTileGridByUiCoords.length
                && rawTiley >= 0
                && rawTiley < spellTileGridByUiCoords[0].length
                && spellTileGridByUiCoords[rawTilex][rawTiley];
    }
    
    public void setUnitHealth(Unit unit, int hp) {
        if (unit != null) {
            unitHealth.put(unit, hp);
        }
    }
    
    public int getUnitHealth(Unit unit) {
        if (unit == null) return 0;
    
        if (unit == humanAvatar) return humanPlayer.getHealth();
        if (unit == aiAvatar) return aiPlayer.getHealth();
    
        Integer hp = unitHealth.get(unit);
        return hp == null ? 0 : hp;
    }

    public void setUnitAttack(Unit unit, int attack) {
        if (unit != null) {
            unitAttack.put(unit, attack);
        }
    }

    public int getUnitAttack(Unit unit) {
        if (unit == null) return 0;
    
        if (unit == humanAvatar) return 2;
        if (unit == aiAvatar) return 2;
    
        Integer atk = unitAttack.get(unit);
        return atk == null ? 0 : atk;
    }

    public int getCardAttack(Card card) {
        if (card == null) return 0;
    
        try {
            Method getter = card.getClass().getMethod("getBigCard");
            Object bigCard = getter.invoke(card);
            if (bigCard != null) {
                try {
                    Method attackGetter = bigCard.getClass().getMethod("getAttack");
                    Object value = attackGetter.invoke(bigCard);
                    if (value instanceof Number) return ((Number) value).intValue();
                } catch (Exception ignored) {}
    
                try {
                    Field attackField = bigCard.getClass().getField("attack");
                    Object value = attackField.get(bigCard);
                    if (value instanceof Number) return ((Number) value).intValue();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    
        return 0;
    }
    
    public void setHealthForTarget(Unit unit, int hp) {
        if (unit == null) return;
    
        if (unit == humanAvatar) {
            humanPlayer.setHealth(hp);
        } else if (unit == aiAvatar) {
            aiPlayer.setHealth(hp);
        } else {
            unitHealth.put(unit, hp);
        }
    }
    
    public int damageTarget(Unit unit, int amount) {
        int newHp = getUnitHealth(unit) - amount;
        setHealthForTarget(unit, newHp);
        return newHp;
    }
    
    public int healTarget(Unit unit, int amount) {
        int newHp = getUnitHealth(unit) + amount;
        setHealthForTarget(unit, newHp);
        return newHp;
    }
    
    public void removeUnitFromBoard(Unit unit) {
        if (unit == null) return;
    
        Tile tile = findTileContainingUnit(unit);
        if (tile != null) {
            tile.setUnit(null);
        }
    
        humanUnits.remove(unit);
        aiUnits.remove(unit);
        unitHealth.remove(unit);
        unitAttack.remove(unit);

    }
    
    public int getCardHealth(Card card) {
        if (card == null) return 0;
    
        try {
            Method getter = card.getClass().getMethod("getBigCard");
            Object bigCard = getter.invoke(card);
            if (bigCard != null) {
                try {
                    Method healthGetter = bigCard.getClass().getMethod("getHealth");
                    Object value = healthGetter.invoke(bigCard);
                    if (value instanceof Number) return ((Number) value).intValue();
                } catch (Exception ignored) {}
    
                try {
                    Field healthField = bigCard.getClass().getField("health");
                    Object value = healthField.get(bigCard);
                    if (value instanceof Number) return ((Number) value).intValue();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    
        try {
            Method getter = card.getClass().getMethod("getHealth");
            Object value = getter.invoke(card);
            if (value instanceof Number) return ((Number) value).intValue();
        } catch (Exception ignored) {}
    
        try {
            Field field = card.getClass().getField("health");
            Object value = field.get(card);
            if (value instanceof Number) return ((Number) value).intValue();
        } catch (Exception ignored) {}
    
        return 0;
    }
 
    // Combined clear to use when switching contexts entirely
    // e.g. end turn, game reset
    public boolean hasUnitAttacked(Unit unit) {
        return unit != null && Boolean.TRUE.equals(unitHasAttackedThisTurn.get(unit));
    }
    
    public void markUnitAsAttacked(Unit unit) {
        if (unit != null) {
            unitHasAttackedThisTurn.put(unit, true);
        }
    }
    
    public boolean hasUnitMoved(Unit unit) {
        return Boolean.TRUE.equals(unitHasMovedThisTurn.get(unit));
    }
    
    public void markUnitAsMoved(Unit unit) {
        if (unit != null) unitHasMovedThisTurn.put(unit, true);
    }

    public void resetUnitAttackFlags() {
        unitHasAttackedThisTurn.clear();
        unitHasMovedThisTurn.clear(); 
    }

    // Clears BOTH move and summon highlights + resets unit/card selection
    public void clearAllHighlights(ActorRef out) {
        clearMoveTileHighlights(out);
        clearSummonTileHighlights(out);
        clearSpellTileHighlights(out);
    }

    public boolean isHighlightedSummonTileByUiCoords(int rawTilex, int rawTiley) {
        return rawTilex >= 0
                && rawTilex < summonTileGridByUiCoords.length
                && rawTiley >= 0
                && rawTiley < summonTileGridByUiCoords[0].length
                && summonTileGridByUiCoords[rawTilex][rawTiley];
    }

    // Card selection state
 
    public void clearCardSelection(ActorRef out) {
        if (selectedCard != null && selectedHandPosition >= 1) {
            if (out != null) {
                BasicCommands.drawCard(out, selectedCard, selectedHandPosition, 0);
            }
        }
        clearSummonTileHighlights(out);
        clearSpellTileHighlights(out);
        selectedCard = null;
        selectedHandPosition = -1;
    }

   

    // SC-402: Sync UI stats for both players
    public void syncPlayerStatsUI(ActorRef out) {
        if (out != null) {
        BasicCommands.setPlayer1Health(out, this.humanPlayer);
        BasicCommands.setPlayer1Mana(out, this.humanPlayer);
        BasicCommands.setPlayer2Health(out, this.aiPlayer);
        BasicCommands.setPlayer2Mana(out, this.aiPlayer);
        }
    }

    public void refreshHumanHandUI(ActorRef out) {
        for (int pos = 1; pos <= 6; pos++) {
            BasicCommands.deleteCard(out, pos);
        }
        for (int i = 0; i < humanPlayer.hand.size() && i < 6; i++) {
            BasicCommands.drawCard(out, humanPlayer.hand.get(i), i + 1, 0);
        }
    }

    public boolean isSpellCard(Card card) {
        String name = getCardName(card);
        return "Horn of the Forsaken".equalsIgnoreCase(name)
                || "Wraithling Swarm".equalsIgnoreCase(name)
                || "Dark Terminus".equalsIgnoreCase(name)
                || "Sundrop Elixir".equalsIgnoreCase(name)
                || "True Strike".equalsIgnoreCase(name)
                || "Beam Shock".equalsIgnoreCase(name);
    }

    public int getCardManaCost(Card card) {
        if (card == null) {
            return Integer.MAX_VALUE;
        }

        try {
            Method getter = card.getClass().getMethod("getManacost");
            Object value = getter.invoke(card);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception ignored) {
        }

        try {
            Field field = card.getClass().getField("manacost");
            Object value = field.get(card);
            if (value instanceof Number)  return ((Number) value).intValue();
        } catch (Exception ignored) {}

        return Integer.MAX_VALUE;
    }

    public String getCardName(Card card) {
        if (card == null) {
            return "";
        }

        try {
            Method getter = card.getClass().getMethod("getCardname");
            Object value = getter.invoke(card);
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {
        }

        try {
            Field field = card.getClass().getField("cardname");
            Object value = field.get(card);
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    public void endGame(ActorRef out, String message) {
        if (out != null) {
            BasicCommands.addPlayer1Notification(out, message, 5);
            try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }
        }
        
        // Remove all units from board 
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 5; y++) {
                Tile t = board[x][y];
                if (t != null && t.getUnit() != null) {
                    if (out != null) {
                        BasicCommands.deleteUnit(out, t.getUnit());
                    }
                    t.setUnit(null);
                }
            }
        }
        
        // Clear all highlights 
        clearAllHighlights(out);
        
        // Game lock 
        gameInitalised = false;
        
        if (out != null) {
            BasicCommands.addPlayer1Notification(out, "Refresh to play again!", 5);
        }
    }

    public String buildUnitConfigPath(Card card) {
        String cardName = getCardName(card).toLowerCase()
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return "conf/gameconfs/units/" + cardName + ".json";
    }
}