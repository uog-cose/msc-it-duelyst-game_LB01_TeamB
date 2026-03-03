package structures.basic;

import java.util.ArrayList;
import java.util.List;

/**
 * A basic representation of of the Player. A player
 * has health and mana.
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class Player {

	int health;
	int mana;

	public List<Card> deck;
	public List<Card> hand;
	
	public Player() {
		super();
		this.health = 20;
		this.mana = 0;
		this.deck = new ArrayList<Card>();
		this.hand = new ArrayList<Card>();
	}
	public Player(int health, int mana) {
		super();
		this.health = health;
		this.mana = mana;
		this.deck = new ArrayList<Card>();
		this.hand = new ArrayList<Card>();
	}
	public int getHealth() {
		return health;
	}
	public void setHealth(int health) {
		this.health = health;
	}
	public int getMana() {
		return mana;
	}
	public void setMana(int mana) {
		this.mana = mana;
	}
	
	
	
}
