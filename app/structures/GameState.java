package structures;

import java.util.ArrayList;
import java.util.List;

import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import akka.actor.ActorRef;
import commands.BasicCommands;

/**
 * This class can be used to hold information about the on-going game.
 * Its created with the GameActor.
 * * @author Dr. Richard McCreadie
 *
 */
public class GameState {

	public boolean gameInitalised = false;
	public boolean something = false;
	public long actionSeq = 0;
	public int turnNumber = 1;
	public boolean humanTurn = true;

	// [SC-505] List to keep track of currently highlighted tiles on the board
	public List<Tile> highlightedTiles = new ArrayList<>();

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

	// SC-203: Highlight valid movement tiles for a unit
	public void highlightValidMoveTiles(ActorRef out, int startX, int startY) {
		if (board == null)
			return;

		// Movement rules:
		// - 2 tiles cardinal directions
		// - 1 tile diagonally
		int[][] offsets = {
				{ 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, // immediate next tiles
				{ 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 }, // cardinal
				{ 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } // diagonal
		};

		for (int[] offset : offsets) {
			int nx = startX + offset[0];
			int ny = startY + offset[1];

			if (isWithinBoard(nx, ny) && isTileFree(nx, ny)) {
				BasicCommands.drawTile(out, board[nx][ny], 1); // mode 1 = highlight
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
}
