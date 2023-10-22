package scripts.world.systems;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.Condition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.EncounterOption;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.procgen.PlanetConditionGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner.ShipRecoverySpecialCreator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin.AsteroidFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicCampaign;
import org.lazywizard.lazylib.campaign.CampaignUtils;

public class LazarusSystem
{
    //setup all distances here
    final float asteroidBelt1Dist = 5300f;
    final float asteroidBelt2Dist = 15700f;
    final float innerRingDist = 2200f;
    final float yureiDist = 3600f;
    final float stationDist = 380f;
    final float jumpPointDist = 4500f;
    final float fleetCombatDist = 4000f;
    final float shipwreckJumpDist = 350f;
    final float wreckDebrisJumpDist = shipwreckJumpDist + 20f;
    final float shipwreckStarDist = 1200f;
    final float wreckDebrisStarDist = shipwreckStarDist + 20f;
    final float gateDist = 6800f;

    //final float majorisRad = 670f;

    static SectorEntityToken fleetCombatLoc1;
    static SectorEntityToken fleetCombatLoc2;

    public static SectorEntityToken GetCombatLoc1()
    {
        return fleetCombatLoc1;
    }
    public static SectorEntityToken GetCombatLoc2()
    {
        return fleetCombatLoc2;
    }

    public void generate(SectorAPI sector)
    {
        StarSystemAPI system = sector.createStarSystem("Lazarus");
        system.getLocation().set(9500, 22000);
        system.setBackgroundTextureFilename("graphics/backgrounds/background6.jpg");

        //#region Star ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // create the star and generate the hyperspace anchor for this system
        PlanetAPI erythemaStar = system.initStar("Erythema",  // unique id for this star
                                            "star_orange",    // id in planets.json
                                            800f,             // radius (in pixels at default zoom)
                                            350f);            // corona radius, from star edge
        system.setLightColor(new Color(255, 202, 125)); // light color in entire system, affects all entities
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        //#region Planets ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        float planetAngle = 360f * (float)Math.random();
        PlanetAPI yurei = system.addPlanet("yurei",          // id
                                            erythemaStar,    // focus
                                            "Yurei",         // name
                                            "desert", // type
                                            planetAngle,     // angle
                                            180f,            // radius
                                            yureiDist,       // orbitRadius
                                            320f);           // orbitDays
        yurei.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "asharu"));
        yurei.getSpec().setGlowColor(new Color(255,255,255,255));
        yurei.getSpec().setUseReverseLightForGlow(true);
        yurei.applySpecChanges();
        yurei.setCustomDescriptionId("lazarus_erythema_yurei");

        // Orbiting Space Station
        SectorEntityToken yureiStation = system.addCustomEntity("yurei_station", "Yurei Station", "station_hightech3", "tritachyon");
        yureiStation.setCircularOrbitPointingDown(yurei, 0, stationDist, 30f);
        yureiStation.setCustomDescriptionId("lazarus_yurei_station");

        // Market
        MarketAPI yureiMarket = Global.getFactory().createMarket("yurei_market", yurei.getName(), 4);
		yureiMarket.setFactionId(Factions.TRITACHYON);
		
		yureiMarket.setSurveyLevel(SurveyLevel.NONE);
		yureiMarket.setPrimaryEntity(yurei);
		yureiMarket.getConnectedEntities().add(yureiStation);
		
        yureiMarket.addCondition(Conditions.AI_CORE_ADMIN);
        yureiMarket.addCondition(Conditions.ROGUE_AI_CORE);
		yureiMarket.addCondition(Conditions.POPULATION_4);
        yureiMarket.addCondition(Conditions.DESERT);
		yureiMarket.addCondition(Conditions.HOT);
		yureiMarket.addCondition(Conditions.HABITABLE);
        yureiMarket.addCondition(Conditions.ORE_ABUNDANT);
		
		yureiMarket.addIndustry(Industries.POPULATION);
		yureiMarket.addIndustry(Industries.SPACEPORT);
		yureiMarket.addIndustry(Industries.ORBITALSTATION);
        yureiMarket.addIndustry(Industries.MILITARYBASE);
        yureiMarket.addIndustry(Industries.MINING);
        yureiMarket.addIndustry(Industries.GROUNDDEFENSES);
        yureiMarket.addIndustry(Industries.HEAVYBATTERIES);

        //yureiMarket.getCondition(Conditions.AI_CORE_ADMIN).getPlugin().apply("alpha_core");
        yureiMarket.getIndustry(Industries.MILITARYBASE).setAICoreId("alpha_core");
        // PersonAPI admin = yureiMarket.getAdmin();
        // admin.setAICoreId("alpha_core");
        // admin.getName().setFirst("Theta");
        // admin.getName().setLast("AI Core");
        // admin.setPortraitSprite("portrait_ai2");
        // admin.setPersonality("reckless");
        // yureiMarket.setAdmin(admin);

		yureiMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
		yureiMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
		yureiMarket.getTariff().modifyFlat("default_tariff", yureiMarket.getFaction().getTariffFraction());
		
		yureiMarket.setUseStockpilesForShortages(true);

        yureiStation.setMarket(yureiMarket);
		yurei.setMarket(yureiMarket);
        Global.getSector().getEconomy().addMarket(yureiMarket, true);
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        //#region Asteroid Belts ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        system.addAsteroidBelt(erythemaStar,            // Focus
                                1000,                  // numAsteroids
                                asteroidBelt1Dist,     // orbitRadius
                                800,                   // width
                                250,                   // minOrbitDays
                                400,                   // maxOrbitDays
                                Terrain.ASTEROID_BELT, // terrainId
                                "Inner Band");         // optionalName
        system.addRingBand(erythemaStar,             // focus
                            "misc",                  // category
                            "rings_asteroids0",      // key
                            256f,                    // bandWidthInTexture
                            3,                       // bandIndex
                            Color.gray,              // color
                            256f,                    // bandWidthInEngine
                            asteroidBelt1Dist - 200, // middleRadius
                            250f);                   // orbitDays
        system.addRingBand(erythemaStar,             // focus
                            "misc",                  // category
                            "rings_asteroids0",      // key
                            256f,                    // bandWidthInTexture
                            0,                       // bandIndex
                            Color.gray,              // color
                            380f,                    // bandWidthInEngine
                            asteroidBelt1Dist - 25, // middleRadius
                            300f);                   // orbitDays
        system.addRingBand(erythemaStar,             // focus
                            "misc",                  // category
                            "rings_dust0",      // key
                            256f,                    // bandWidthInTexture
                            2,                       // bandIndex
                            Color.gray,              // color
                            512f,                    // bandWidthInEngine
                            innerRingDist, // middleRadius
                            150f);                   // orbitDays
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        //#region Jump Points ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        int randSign = Math.random() > 0.5 ? 1 : -1;
        float jumpPointAngle = (planetAngle + 150 * randSign) % 360f;
        JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("lazarus_jump", "Lazarus System Jump");
        jumpPoint.setCircularOrbit(system.getEntityById("Erythema"), jumpPointAngle, jumpPointDist, 400f);
        jumpPoint.setStandardWormholeToHyperspaceVisual();

        system.addEntity(jumpPoint);
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        //#region Inactive Gate ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        SectorEntityToken gate = system.addCustomEntity("lazarus_gate",  // unique id
                                                        "Lazarus Gate",  // name - if null, defaultName from custom_entities.json will be used
                                                        "inactive_gate", // type of object, defined in custom_entities.json
                                                        null);           // faction
		gate.setCircularOrbitPointingDown(erythemaStar, 360 * (float)Math.random(), gateDist, 350);
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        //#region Rubble and Debris ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Jump Point
        SectorEntityToken shipwreckA1 = addDerelict(system, jumpPoint, "colossus2_Pather", ShipCondition.WRECKED, shipwreckJumpDist, false);
        SectorEntityToken shipwreckA2 = addDerelict(system, jumpPoint, "lasher_luddic_path_Raider", ShipCondition.WRECKED, shipwreckJumpDist, false);
        SectorEntityToken shipwreckA3 = addDerelict(system, jumpPoint, "cerberus_luddic_path_Attack", ShipCondition.WRECKED, shipwreckJumpDist, false);


        DebrisFieldParams debrisParams = new DebrisFieldParams(
            400f,      // field radius - should not go above 1000 for performance reasons
            -1f,       // density, visual - affects number of debris pieces
            10000000f, // duration in days 
            0f);       // days the field will keep generating glowing pieces
		debrisParams.source = DebrisFieldSource.BATTLE;
		debrisParams.baseSalvageXP = 250; // base XP for scavenging in field
		SectorEntityToken debrisNextToJump = Misc.addDebrisField(system, debrisParams, StarSystemGenerator.random);
		debrisNextToJump.setSensorProfile(null);
		debrisNextToJump.setDiscoverable(true);
		debrisNextToJump.setCircularOrbitPointingDown(jumpPoint, shipwreckA1.getCircularOrbitAngle(), wreckDebrisJumpDist, 25f);
		debrisNextToJump.setId("lazarus_debrisNextToJump");

        // Star
        SectorEntityToken shipwreckB1 = addDerelict(system, erythemaStar, "odyssey_Balanced", ShipCondition.WRECKED, shipwreckStarDist, false);
        SectorEntityToken shipwreckB2 = addDerelict(system, erythemaStar, "phantom_Elite", ShipCondition.WRECKED, shipwreckStarDist, false);
        SectorEntityToken shipwreckB3 = addDerelict(system, erythemaStar, "aurora_Balanced", ShipCondition.WRECKED, shipwreckStarDist, false);
        SectorEntityToken shipwreckB4 = addDerelict(system, erythemaStar, "medusa_Attack", ShipCondition.WRECKED, shipwreckStarDist, false);
        SectorEntityToken shipwreckB5 = addDerelict(system, erythemaStar, "shade_Assault", ShipCondition.WRECKED, shipwreckStarDist, false);

		SectorEntityToken debrisANextToStar = Misc.addDebrisField(system, debrisParams, StarSystemGenerator.random);
		debrisANextToStar.setSensorProfile(null);
		debrisANextToStar.setDiscoverable(null);
		debrisANextToStar.setCircularOrbitPointingDown(erythemaStar, shipwreckB1.getCircularOrbitAngle(), wreckDebrisStarDist, 100f);
		debrisANextToStar.setId("lazarus_debrisANextToStar");

        SectorEntityToken debrisBNextToStar = Misc.addDebrisField(system, debrisParams, StarSystemGenerator.random);
		debrisBNextToStar.setSensorProfile(null);
		debrisBNextToStar.setDiscoverable(null);
		debrisBNextToStar.setCircularOrbitPointingDown(erythemaStar, shipwreckB4.getCircularOrbitAngle(), wreckDebrisStarDist, 100f);
		debrisBNextToStar.setId("lazarus_debrisBNextToStar");
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        //#region Fleets ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        float fleetCombatAngle = planetAngle + ((90f * (float)Math.random() + 30f) * randSign) % 360f;
        fleetCombatLoc1 = system.addCustomEntity("fleet_combat_loc", null, "mission_location", null);
		fleetCombatLoc1.setCircularOrbitPointingDown(erythemaStar, fleetCombatAngle, fleetCombatDist - 50, 310f);
        fleetCombatLoc2 = system.addCustomEntity("fleet_combat_loc", null, "mission_location", null);
		fleetCombatLoc2.setCircularOrbitPointingDown(erythemaStar, fleetCombatAngle, fleetCombatDist + 50, 310f);
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        StarSystemGenerator.addSystemwideNebula(system, StarAge.OLD);
        system.autogenerateHyperspaceJumpPoints(true, false);

        HyperspaceTerrainPlugin hstPlugin = (HyperspaceTerrainPlugin)Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor nEditor = new NebulaEditor(hstPlugin);
        float minHyperRadius = hstPlugin.getTileSize() * 2f;

        float hyperRadius = system.getMaxRadiusInHyperspace();
        nEditor.clearArc(system.getLocation().x, system.getLocation().y, 0, hyperRadius + minHyperRadius, 0, 360f);
        nEditor.clearArc(system.getLocation().x, system.getLocation().y, 0, hyperRadius + minHyperRadius, 0, 360f, 0.25f);
    }

    // Vanilla API function grabbed from Galatia.java
    protected SectorEntityToken addDerelict(StarSystemAPI system, SectorEntityToken focus, String variantId, 
								ShipCondition condition, float orbitRadius, boolean recoverable) {
		DerelictShipData params = new DerelictShipData(new PerShipData(variantId, condition, 0f), false);
		SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(system, Entities.WRECK, Factions.NEUTRAL, params);
		ship.setDiscoverable(true);
		
		float orbitDays = orbitRadius / (10f + (float) Math.random() * 5f);
		ship.setCircularOrbit(focus, (float) Math.random() * 360f, orbitRadius, orbitDays);
		
		if (recoverable) {
			ShipRecoverySpecialCreator creator = new ShipRecoverySpecialCreator(null, 0, 0, false, null, null);
			Misc.setSalvageSpecial(ship, creator.createSpecial(ship, null));
		}

        return ship;
	}
}
