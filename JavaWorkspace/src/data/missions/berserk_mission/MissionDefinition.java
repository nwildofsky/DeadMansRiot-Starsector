package data.missions.berserk_mission;

import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

public class MissionDefinition implements MissionDefinitionPlugin {

	public void defineMission(MissionDefinitionAPI api) {

			api.initFleet(FleetSide.PLAYER, "TMS", FleetGoal.ATTACK, false);
			api.initFleet(FleetSide.ENEMY,  "TME",FleetGoal.ATTACK, true);
			
			api.addToFleet(FleetSide.PLAYER, "aurora_Balanced", FleetMemberType.SHIP, true);
			api.addToFleet(FleetSide.PLAYER, "medusa_CS", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.PLAYER, "harbinger_Strike", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.PLAYER, "harbinger_Strike", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.PLAYER, "shrike_Support", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.PLAYER, "shrike_Attack", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.PLAYER, "wolf_PD", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.PLAYER, "wolf_Strike", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.PLAYER, "wolf_Strike", FleetMemberType.SHIP, false);
			
			api.addToFleet(FleetSide.ENEMY, "venture_pather_Attack", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "colossus2_Pather", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "colossus2_Pather", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "manticore_luddic_path_Strike", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "manticore_luddic_path_Strike", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "manticore_luddic_path_Strike", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "hammerhead_Balanced", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "hammerhead_Elite", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "enforcer_Overdriven", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "kite_luddic_path_Raider", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "kite_luddic_path_Raider", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "kite_luddic_path_Raider", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "hound_luddic_path_Attack", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "hound_luddic_path_Attack", FleetMemberType.SHIP, false);
			api.addToFleet(FleetSide.ENEMY, "hound_luddic_path_Attack", FleetMemberType.SHIP, false);

			float width = 15000.0F;
			float height = 11000.0F;
			api.initMap(-width / 2.0F, width / 2.0F, -height / 2.0F, height / 2.0F);
			float minX = -width / 2.0F;
			float minY = -height / 2.0F;
	  
			for(int i = 0; i < 7; ++i) {
			   float x = (float)Math.random() * width - width / 2.0F;
			   float y = (float)Math.random() * height - height / 2.0F;
			   float radius = 100.0F + (float)Math.random() * 800.0F;
			   api.addNebula(x, y, radius);
			}
	  
			//api.addObjective(minX + width * 0.7F, minY + height * 0.25F, "sensor_array");
			//api.addObjective(minX + width * 0.8F, minY + height * 0.75F, "nav_buoy");
			//api.addObjective(minX + width * 0.2F, minY + height * 0.25F, "nav_buoy");
	}

}