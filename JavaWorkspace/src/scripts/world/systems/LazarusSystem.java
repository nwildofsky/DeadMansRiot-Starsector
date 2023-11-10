package scripts.world.systems;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner.ShipRecoverySpecialCreator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin.AsteroidFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicCampaign;
import org.lazywizard.lazylib.campaign.CampaignUtils;

public class LazarusSystem
{
    // Setup all distances here
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

    // Static Fields ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    protected static StarSystemAPI system;
    protected static PlanetAPI yurei;
    protected static MarketAPI yureiMarket;
    protected static SectorEntityToken berserkDebris;
    protected static CustomCampaignEntityAPI fleetCombatLoc1;
    protected static CustomCampaignEntityAPI fleetCombatLoc2;

    // Static Functions ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Getters
    public static StarSystemAPI GetSystem()
    {
        if (system == null)
        {
            system = Global.getSector().getStarSystem("lazarus");
        }

        return system;
    }

    public static PlanetAPI GetYurei()
    {
        if (yurei == null)
        {
            List<PlanetAPI> planets = GetSystem().getPlanets();
            for (PlanetAPI p : planets)
            {
                if (p.getId().equals("yurei"))
                {
                    yurei = p;
                    continue;
                }
            }
        }

        return yurei;
    }

    public static MarketAPI GetYureiMarket()
    {
        if (yureiMarket == null)
        {
            yureiMarket = GetYurei().getMarket();
        }

        return yureiMarket;
    }

    public static CustomCampaignEntityAPI GetCombatLoc1()
    {
        if (fleetCombatLoc1 == null)
        {
            //TODO
        }

        return fleetCombatLoc1;
    }
    public static CustomCampaignEntityAPI GetCombatLoc2()
    {
        if (fleetCombatLoc2 == null)
        {
            //TODO
        }

        return fleetCombatLoc2;
    }

    // Delayed World Actions
    public static void integrateMarket()
    {
        Global.getSector().getEconomy().addMarket(GetYureiMarket(), true);
    }

    public static void addMarketAIAdmin()
    {
        // Add the rogue AI as the planet admin and add it into the comm directory
        PersonAPI admin = Global.getFactory().createPerson();
        admin.setId("theta_ai_admin");
        admin.setName(new FullName("Theta", "AI Core", FullName.Gender.ANY));
        admin.setPortraitSprite("graphics/portraits/portrait_ai2.png");
        admin.setPersonality("reckless");
        admin.setPostId(Ranks.POST_ADMINISTRATOR);
        admin.setRankId(Ranks.TERRORIST);
        GetYureiMarket().getCommDirectory().addPerson(admin, 0);
        //yureiMarket.addPerson(admin);
        GetYureiMarket().setAdmin(admin);
    }

    public static void addBerserkDebris()
    {
        DebrisFieldParams debrisParams = new DebrisFieldParams(
            100f,      // field radius - should not go above 1000 for performance reasons
            -1f,       // density, visual - affects number of debris pieces
            10000000f, // duration in days 
            10000000f);// days the field will keep generating glowing pieces
		debrisParams.source = DebrisFieldSource.BATTLE;
		debrisParams.baseSalvageXP = 500; // base XP for scavenging in field
		berserkDebris = addDebrisField(GetSystem(), debrisParams, StarSystemGenerator.random);
		berserkDebris.setSensorProfile(null);
		berserkDebris.setDiscoverable(true);
		berserkDebris.setCircularOrbitPointingDown(GetYurei(), 360 * (float)Math.random(), 300f, 15f);
		berserkDebris.setId("berserk_debris");

        DropData d = new DropData();
        d.chances = 300;
        d.group = "extended";
        berserkDebris.addDropRandom(d);

        CargoAPI weaponCargo = Global.getFactory().createCargo(false);
        weaponCargo.addWeapons("berserkcanon", 1);
        BaseSalvageSpecial.addExtraSalvage(berserkDebris, weaponCargo);
    }

    public static void addModWeaponToDebris()
    {
        berserkDebris.getCargo().addItems(CargoItemType.WEAPONS, "berserkcanon", 1);
    }

    // Responsible for create the Lazarus System, called on the start of a new game
    public void generate(SectorAPI sector)
    {
        system = sector.createStarSystem("Lazarus");
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
        // Planet
        // The planet location is placed randomly
        float planetAngle = 360f * (float)Math.random();
        yurei = system.addPlanet("yurei",        // id
                                erythemaStar,    // focus
                                "Yurei",         // name
                                "desert",        // type
                                planetAngle,     // angle
                                180f,            // radius
                                yureiDist,       // orbitRadius
                                320f);           // orbitDays
        yurei.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "asharu"));
        yurei.getSpec().setGlowColor(new Color(255,255,255,255));
        yurei.getSpec().setUseReverseLightForGlow(true);
        yurei.applySpecChanges();
        yurei.setCustomDescriptionId("planet_yurei");

        // Orbiting Space Station
        SectorEntityToken abattoirStation = system.addCustomEntity("abattoir_station", "Abattoir Station", "station_hightech3", "tritachyon");
        abattoirStation.setCircularOrbitPointingDown(yurei, 0, stationDist, 30f);
        abattoirStation.setCustomDescriptionId("abattoir_station");

        // Market
        yureiMarket = Global.getFactory().createMarket("yurei_market", yurei.getName(), 4);
		yureiMarket.setFactionId(Factions.TRITACHYON);
		
		yureiMarket.setSurveyLevel(SurveyLevel.NONE);
		yureiMarket.setPrimaryEntity(yurei);
		yureiMarket.getConnectedEntities().add(abattoirStation);
		
        //yureiMarket.addCondition(Conditions.AI_CORE_ADMIN);
        yureiMarket.addCondition(Conditions.ROGUE_AI_CORE);
		yureiMarket.addCondition(Conditions.POPULATION_4);
		yureiMarket.addCondition(Conditions.HOT);
		yureiMarket.addCondition(Conditions.HABITABLE);
        yureiMarket.addCondition(Conditions.DECIVILIZED_SUBPOP);
		
		yureiMarket.addIndustry(Industries.POPULATION);
		yureiMarket.addIndustry(Industries.SPACEPORT);
		yureiMarket.addIndustry(Industries.ORBITALSTATION);
        yureiMarket.addIndustry(Industries.MILITARYBASE);
        yureiMarket.addIndustry(Industries.GROUNDDEFENSES);
        yureiMarket.addIndustry(Industries.HEAVYBATTERIES);

		//yureiMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
		yureiMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
		yureiMarket.getTariff().modifyFlat("default_tariff", yureiMarket.getFaction().getTariffFraction());
		yureiMarket.setUseStockpilesForShortages(true);

        // Set the raid target on a specific industry
        yureiMarket.getIndustry(Industries.MILITARYBASE).setAICoreId("theta_core");
        
        abattoirStation.setMarket(yureiMarket);
		yurei.setMarket(yureiMarket);
        integrateMarket();
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        //#region Asteroid Belts ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // Asteroid belt is what actually creates the game object
        // Ring bands are extra visual elements pulled from graphics/planets
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
        // Place the jump point 150 degrees away from the planet
        // Randomly pick a direction for this angle
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
        // Locations are between the planet and the jump point
        float fleetCombatAngle = planetAngle + ((70f * (float)Math.random() + 45f) * randSign) % 360f;

        // Locations to be used for fleet generation and placement
        // Removed so that the icons don't appear on the map
        fleetCombatLoc1 = system.addCustomEntity("fleet_combat_loc1", null, "mission_location", null);
		fleetCombatLoc1.setCircularOrbitPointingDown(erythemaStar, fleetCombatAngle, fleetCombatDist - 50, 370f);
        system.removeEntity(fleetCombatLoc1);

        fleetCombatLoc2 = system.addCustomEntity("fleet_combat_loc2", null, "mission_location", null);
		fleetCombatLoc2.setCircularOrbitPointingDown(erythemaStar, fleetCombatAngle, fleetCombatDist + 50, 370f);
        system.removeEntity(fleetCombatLoc2);
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
								ShipCondition condition, float orbitRadius, boolean recoverable)
    {
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

    // Taken from Misc.java and slightly modified so no default salvage items are set
    protected static SectorEntityToken addDebrisField(LocationAPI loc, DebrisFieldParams params, Random random)
    {
        if (random == null) random = Misc.random;
        SectorEntityToken debris = loc.addTerrain(Terrain.DEBRIS_FIELD, params);
        debris.setName(((CampaignTerrainAPI)debris).getPlugin().getTerrainName());
        
        float range = DebrisFieldTerrainPlugin.computeDetectionRange(params.bandWidthInEngine);
        debris.getDetectedRangeMod().modifyFlat("gen", range);

        debris.getMemoryWithoutUpdate().set(MemFlags.SALVAGE_SEED, random.nextLong());
        
        debris.setDiscoveryXP((float)((int)(params.bandWidthInEngine * 0.2f)));
        if (params.baseSalvageXP <= 0) {
            debris.setSalvageXP((float)((int)(params.bandWidthInEngine * 0.6f)));
        }
        
        return debris;
	}
}
