package events;


import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import structures.GameState;
import structures.basic.Tile;
import structures.basic.Unit;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case a tile.
 * The event returns the x (horizontal) and y (vertical) indices of the tile that was
 * clicked. Tile indices start at 1.
 * 
 * { 
 *   messageType = “tileClicked”
 *   tilex = <x index of the tile>
 *   tiley = <y index of the tile>
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class TileClicked implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		int tilex = message.get("tilex").asInt();
		int tiley = message.get("tiley").asInt();
		
		// if (gameState.something == true) {
		// 	// do some logic
		// }

		Tile clickedTile = gameState.getTile(tilex, tiley);

		if (clickedTile == null) {
			return;
		}

		if (clickedTile.getUnit() != null) {
			Unit clickedUnit = clickedTile.getUnit();

			// only allowing human-owned units to show movement tiles
			if (clickedUnit == gameState.humanAvatar /* for later human summoned units */) {
				gameState.highlightValidMoveTiles(out, tilex, tiley);
				return;
			}
		}
		
	}

}
