package missiontest.berserkmission;

import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

public class MissionTest implements MissionDefinitionPlugin {

	public void defineMission(MissionDefinitionAPI api) {

		// Set up the fleets
		api.initFleet(FleetSide.PLAYER, "TTS", FleetGoal.ATTACK, false, 30);
		api.initFleet(FleetSide.ENEMY, "LI", FleetGoal.ATTACK, true, 30);

		// Set a blurb for each fleet
		api.setFleetTagline(FleetSide.PLAYER, "Emergency Force Delta");
		api.setFleetTagline(FleetSide.ENEMY, "The Rogue L.I. / TTS Decisive");
		
		// These show up as items in the bulleted list under 
		// "Tactical Objectives" on the mission detail screen
		api.addBriefingItem("The TTS Decisive must be defeated.  The Rook must survive.");
		
		// Set up the player's fleet
		api.addToFleet(FleetSide.PLAYER, "aurora_Balanced", FleetMemberType.SHIP, "TTS Rook", true);
		api.addToFleet(FleetSide.PLAYER, "silent_raven_Standard", FleetMemberType.SHIP, "TTS Dark Wing", true);
		api.addToFleet(FleetSide.PLAYER, "silent_raven_Standard", FleetMemberType.SHIP, "TTS Ebon Claw", true);
		//api.addToFleet(FleetSide.PLAYER, "onslaught_Standard", FleetMemberType.SHIP, "TTS Invincible", true);
		
		// Mark player flagship as essential.
		api.defeatOnShipLoss("TTS Rook");
		
		// Set up the enemy fleet
		api.addToFleet(FleetSide.ENEMY, "paragon_Elite", FleetMemberType.SHIP, "TTS Decisive", true);
		api.addToFleet(FleetSide.ENEMY, "kite_hegemony_Interceptor", FleetMemberType.SHIP, "TTS Bait", false);
		api.addToFleet(FleetSide.ENEMY, "kite_hegemony_Interceptor", FleetMemberType.SHIP, "TTS Bait", false);
		
		// Set up the map.
		float width = 9000f;
		float height = 9000f;
		api.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);
		
		for (int i = 0; i < 15; i++) {
			float x = (float) Math.random() * width - width/2;
			float y = (float) Math.random() * height - height/2;
			float radius = 100f + (float) Math.random() * 900f; 
			api.addNebula(x, y, radius);
		}

		// Add an asteroid field going diagonally across the
		// battlefield, 2000 pixels wide, with a maximum of 
		// 100 asteroids in it.
		// 20-70 is the range of asteroid speeds.
		api.addAsteroidField(0f, 0f, (float) Math.random() * 360f, width, 20f, 70f, 100);
	}

}