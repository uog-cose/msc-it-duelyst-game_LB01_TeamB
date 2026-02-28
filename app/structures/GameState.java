package structures;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
/**
 * This class can be used to hold information about the on-going game.
 * Its created with the GameActor.
 * 
 * @author Dr. Richard McCreadie
 *
 */

public class GameState {

	public boolean gameInitalised = false;
	public boolean something = false;
	public long actionSeq = 0;

	// Players (stats)
    public Player humanPlayer = new Player();
    public Player aiPlayer = new Player();

	// Avatars 
    public Unit humanAvatar = null;
    public Unit aiAvatar = null;

	// 9x5 grid for the game board (SC-102)
	public Tile[][] board = new Tile[9][5];

	// initialize board array only (tiles will be created in Initialize.java)
	public void initBoardArray() {
		board = new Tile[9][5];
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
}


