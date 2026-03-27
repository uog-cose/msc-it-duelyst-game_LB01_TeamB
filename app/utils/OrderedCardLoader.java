package utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import structures.basic.Card;

/**
 * This is a utility class that provides methods for loading the decks for each
 * player, as the deck ordering is fixed. 
 * @author Richard
 *
 */
public class OrderedCardLoader {

	public static String cardsDIR = "conf/gameconfs/cards/";
	
	/**
	 * Returns all of the cards in the human player's deck in order
	 * @return
	 */
	public static List<Card> getPlayer1Cards(int copies) {
 
     List<Card> cardsInDeck = new ArrayList<Card>(20);
 
     // Get the list of files
 
     String[] files = new File(cardsDIR).list();
 
     if (files == null) return cardsInDeck;
 
    // SORT the files to ensure cross-platform consistency (Mac vs Windows)
 
     Arrays.sort(files);
 
    int cardID = 1;
 
     for (int i = 0; i < copies; i++) {
 
         for (String filename : files) {
 
             if (filename.startsWith("1_")) {
 
                 cardsInDeck.add(BasicObjectBuilders.loadCard(cardsDIR + filename, cardID++, Card.class));
 
             }
 
         }
 
     }
 
     return cardsInDeck;
 
 }
	
	
	/**
	 * Returns all of the cards in the human player's deck in order
	 * @return
	 */
	public static List<Card> getPlayer2Cards(int copies) {
 
		List<Card> cardsInDeck = new ArrayList<Card>(20);
	
		String[] files = new File(cardsDIR).list();
	
		if (files == null) return cardsInDeck;
	
	   // MANDATORY: Sort to ensure Mac and Windows see the same AI deck order
	
		Arrays.sort(files);
	
	   int cardID = 1;
	
		for (int i = 0; i < copies; i++) {
	
			for (String filename : files) {
	
				// Player 2 cards are prefixed with "2_"
	
				if (filename.startsWith("2_")) {
	
					cardsInDeck.add(BasicObjectBuilders.loadCard(cardsDIR + filename, cardID++, Card.class));
	
				}
	
			}
	
		}
	
		return cardsInDeck;
	
	}
	
}
