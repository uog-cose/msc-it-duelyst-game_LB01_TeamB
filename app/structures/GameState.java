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

    // [SC-505] List to keep track of currently highlighted tiles on the board
    public List<Tile> highlightedTiles = new ArrayList<>();

    // Units currently owned by each side (excluding avatars)
    public List<Unit> humanUnits = new ArrayList<>();
    public List<Unit> aiUnits = new ArrayList<>();

    // Card-play state used for SC-201 / SC-205
    public Card selectedCard = null;
    public int selectedHandPosition = -1;
    public boolean[][] highlightedSummonTiles = new boolean[9][5];
    public boolean[][] highlightedSummonTilesByUiCoords = new boolean[10][6];
    public int nextUnitId = 1000;

    // 9x5 grid for the game board (SC-102)
    public Tile[][] board = new Tile[9][5];

    // initialize board array only (tiles will be created in Initialize.java)
    public void initBoardArray() {
        board = new Tile[9][5];
        highlightedSummonTiles = new boolean[9][5];
        highlightedSummonTilesByUiCoords = new boolean[10][6];
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

    // SC-203: Highlight valid movement tiles for a unit
    public void highlightValidMoveTiles(ActorRef out, int startX, int startY) {
        if (board == null)
            return;

        // Movement rules:
        // - 2 tiles cardinal directions
        // - 1 tile diagonally
        int[][] offsets = {
                { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
                { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 },
                { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
        };

        for (int[] offset : offsets) {
            int nx = startX + offset[0];
            int ny = startY + offset[1];

            if (isWithinBoard(nx, ny) && isTileFree(nx, ny)) {
                BasicCommands.drawTile(out, board[nx][ny], 1);
            }
        }
    }

    // SC-402: Sync UI stats for both players
    public void syncPlayerStatsUI(ActorRef out) {
        BasicCommands.setPlayer1Health(out, this.humanPlayer);
        BasicCommands.setPlayer1Mana(out, this.humanPlayer);
        BasicCommands.setPlayer2Health(out, this.aiPlayer);
        BasicCommands.setPlayer2Mana(out, this.aiPlayer);
    }

    public Tile findTileContainingUnit(Unit unit) {
        if (unit == null) {
            return null;
        }
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 5; y++) {
                if (board[x][y] != null && board[x][y].getUnit() == unit) {
                    return board[x][y];
                }
            }
        }
        return null;
    }

    public void clearHighlightedTiles(ActorRef out) {
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 5; y++) {
                if (highlightedSummonTiles[x][y] && board[x][y] != null) {
                    BasicCommands.drawTile(out, board[x][y], 0);
                }
                highlightedSummonTiles[x][y] = false;
            }
        }
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 6; y++) {
                highlightedSummonTilesByUiCoords[x][y] = false;
            }
        }
    }

    public void clearCardSelection(ActorRef out) {
        if (selectedCard != null && selectedHandPosition >= 1) {
            BasicCommands.drawCard(out, selectedCard, selectedHandPosition, 0);
        }
        clearHighlightedTiles(out);
        selectedCard = null;
        selectedHandPosition = -1;
    }

    public int highlightValidSummonTiles(ActorRef out) {
        clearHighlightedTiles(out);

        int count = 0;
        count += highlightAdjacentFreeTilesAroundUnit(out, humanAvatar);
        for (Unit unit : humanUnits) {
            count += highlightAdjacentFreeTilesAroundUnit(out, unit);
        }
        return count;
    }

    public int highlightValidSpellTargets(ActorRef out) {
        clearHighlightedTiles(out);

        int count = 0;
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 5; y++) {
                Tile tile = board[x][y];
                if (tile == null || tile.getUnit() == null) {
                    continue;
                }

                Unit unit = tile.getUnit();

                if (aiUnits.contains(unit) && !highlightedSummonTiles[x][y]) {
                    highlightedSummonTiles[x][y] = true;
                    BasicCommands.drawTile(out, tile, 1);

                    int uiX = tile.getTilex();
                    int uiY = tile.getTiley();

                    if (uiX >= 0 && uiX < highlightedSummonTilesByUiCoords.length
                            && uiY >= 0 && uiY < highlightedSummonTilesByUiCoords[0].length) {
                        highlightedSummonTilesByUiCoords[uiX][uiY] = true;
                    }

                    System.out.println("[SC-202] highlight spell target board=(" + x + "," + y + ") ui=(" + uiX + "," + uiY + ")");
                    count++;
                }
            }
        }
        return count;
    }

    private int highlightAdjacentFreeTilesAroundUnit(ActorRef out, Unit unit) {
        Tile origin = findTileContainingUnit(unit);
        if (origin == null) {
            return 0;
        }

        int originX = -1;
        int originY = -1;

        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 5; y++) {
                if (board[x][y] == origin) {
                    originX = x;
                    originY = y;
                    break;
                }
            }
            if (originX != -1) {
                break;
            }
        }

        if (originX == -1 || originY == -1) {
            return 0;
        }

        int count = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }

                int nx = originX + dx;
                int ny = originY + dy;

                if (isTileFree(nx, ny) && !highlightedSummonTiles[nx][ny]) {
                    highlightedSummonTiles[nx][ny] = true;

                    Tile highlightedTile = board[nx][ny];
                    BasicCommands.drawTile(out, highlightedTile, 1);

                    int uiX = highlightedTile.getTilex();
                    int uiY = highlightedTile.getTiley();

                    if (uiX >= 0 && uiX < highlightedSummonTilesByUiCoords.length
                            && uiY >= 0 && uiY < highlightedSummonTilesByUiCoords[0].length) {
                        highlightedSummonTilesByUiCoords[uiX][uiY] = true;
                    }

                    System.out.println("[SC-201] highlight board=(" + nx + "," + ny + ") ui=(" + uiX + "," + uiY + ")");
                    count++;
                }
            }
        }

        return count;
    }

    public boolean isHighlightedSummonTile(int x, int y) {
        return isWithinBoard(x, y) && highlightedSummonTiles[x][y];
    }

    public boolean isHighlightedSummonTileByUiCoords(int rawTilex, int rawTiley) {
        return rawTilex >= 0
                && rawTilex < highlightedSummonTilesByUiCoords.length
                && rawTiley >= 0
                && rawTiley < highlightedSummonTilesByUiCoords[0].length
                && highlightedSummonTilesByUiCoords[rawTilex][rawTiley];
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
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception ignored) {
        }

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

    public String buildUnitConfigPath(Card card) {
        String cardName = getCardName(card).toLowerCase()
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return "conf/gameconfs/units/" + cardName + ".json";
    }
}
