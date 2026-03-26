package events;


import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import structures.GameState;
import structures.CombatHandler;
import structures.basic.Unit;

/**
 * Indicates that a unit instance has stopped moving. 
 * The event reports the unique id of the unit.
 * 
 * { 
 *   messageType = “unitStopped”
 *   id = <unit id>
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class UnitStopped implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
        int unitid = message.get("id").asInt();

        // Pending attack check
        if (gameState.pendingAttacker != null
                && gameState.pendingAttacker.getId() == unitid
                && gameState.pendingDefender != null) {

            Unit attacker = gameState.pendingAttacker;
            Unit defender = gameState.pendingDefender;

            // Clear pending state
            gameState.pendingAttacker = null;
            gameState.pendingDefender = null;

            // Execute attack after move animation complete
            CombatHandler.executeAttack(out, gameState, attacker, defender);
            gameState.actionSeq++;
            System.out.println("[SC-303] move+attack via UnitStopped");
        }
    }
}
