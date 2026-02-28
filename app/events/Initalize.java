package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import structures.GameState;
import commands.BasicCommands;
import structures.basic.Card;
import utils.OrderedCardLoader;
import demo.CommandDemo;
import demo.Loaders_2024_Check;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;

/**
 * Indicates that both the core game loop in the browser is starting, meaning
 * that it is ready to recieve commands from the back-end.
 * * {
 * messageType = “initalize”
 * }
 * * @author Dr. Richard McCreadie
 *
 */
public class Initalize implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		gameState.gameInitalised = true;
		gameState.something = true;
		gameState.actionSeq++;

		// [SC-102] Initialize the 9x5 game board when the game starts
		gameState.initBoardArray();

		for (int x = 0; x < 9; x++) {
			for (int y = 0; y < 5; y++) {
				Tile tile = BasicObjectBuilders.loadTile(x, y);
				gameState.board[x][y] = tile;
				BasicCommands.drawTile(out, tile, 0);
			}
		}

		// SC-103- avatar mirrored positioned on board
		Unit humanAvatar = BasicObjectBuilders.loadUnit("conf/gameconfs/avatars/avatar1.json", 100, Unit.class);
		Unit aiAvatar = BasicObjectBuilders.loadUnit("conf/gameconfs/avatars/avatar2.json", 200, Unit.class);

		gameState.humanAvatar = humanAvatar;
		gameState.aiAvatar = aiAvatar;
		int humanX0 = 2 - 1;
		int humanY0 = 3 - 1;

		int aiX0 = 8 - humanX0; // avatars mirrored across 9 columns
		int aiY0 = humanY0; // same row

		Tile humanTile = gameState.board[humanX0][humanY0];
		Tile aiTile = gameState.board[aiX0][aiY0];
		humanTile.setUnit(humanAvatar);
		aiTile.setUnit(aiAvatar);

		// avatars placed on tiles
		humanAvatar.setPositionByTile(humanTile);
		aiAvatar.setPositionByTile(aiTile);


		gameState.humanPlayer.deck = OrderedCardLoader.getPlayer1Cards(2);
		gameState.aiPlayer.deck = OrderedCardLoader.getPlayer2Cards(2);
		
		for (int i = 0; i < 3; i++) {
			if (!gameState.humanPlayer.deck.isEmpty()) {
				Card drawnCard = gameState.humanPlayer.deck.remove(0);
				
				gameState.humanPlayer.hand.add(drawnCard);
				
				BasicCommands.drawCard(out, drawnCard, i + 1, 0);
			}
		}

		if (out != null) {
				// initializing hp health = 20
			BasicCommands.setPlayer1Health(out, gameState.humanPlayer);
			BasicCommands.setPlayer2Health(out, gameState.aiPlayer);
			BasicCommands.drawUnit(out, humanAvatar, humanTile);
			BasicCommands.drawUnit(out, aiAvatar, aiTile);
			BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
			BasicCommands.setPlayer2Mana(out, gameState.aiPlayer);
		
		}

		// [SC-102] Comment out the auto mode demo as requested for the actual gameplay
		// CommandDemo.executeDemo(out);
	}

}
