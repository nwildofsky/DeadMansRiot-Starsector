package scripts.world.systems;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.Condition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.procgen.PlanetConditionGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin.AsteroidFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.MagneticFieldParams;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicCampaign;

public class LazarusSystem
{
    //setup all distances here
    final float asteroidBelt1Dist = 5000f;
    final float asteroidBelt2Dist = 15700f;
    final float yureiDist = 3600f;
    final float stationDist = 500f;

    final float jumpInnerDist = 3050f;
    final float jumpOuterDist = 8400f;
    final float jumpFringeDist = 4100f;

final float majorisRad = 670f;

    public void generate(SectorAPI sector)
    {
        StarSystemAPI system = sector.createStarSystem("Lazarus");
        system.getLocation().set(9500, 22000);
        system.setBackgroundTextureFilename("graphics/backgrounds/background6.jpg");

        //#region Star ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // create the star and generate the hyperspace anchor for this system
        PlanetAPI erythemaStar = system.initStar("Erythema",// unique id for this star
                                            "star_orange",  // id in planets.json
                                            700f,           // radius (in pixels at default zoom)
                                            280f);          // corona radius, from star edge
        system.setLightColor(new Color(255, 151, 0)); // light color in entire system, affects all entities
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        //#region Planets ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        PlanetAPI yurei = system.addPlanet("yurei",                      // id
                                            erythemaStar,                // focus
                                            "Yurei",                     // name
                                            "barren3",                   // type
                                            360f * (float)Math.random(), // angle
                                            220f,                        // radius
                                            yureiDist,                   // orbitRadius
                                            320f);                       // orbitDays
        yurei.setCustomDescriptionId("lazarus_erythema_yurei");

        MarketAPI yureiMarket = MagicCampaign.addSimpleMarket(yurei,
                                                        "yurei_market",
                                                        "Yurei Market",
                                                        4,
                                                        "tritachyon",
                                                        true,
                                                        false,
                                                        Arrays.asList // list of conditions from campaign/market_conditions.csv
                                                        (
                                                            Conditions.ABANDONED_STATION
                                                        ),
                                                        Arrays.asList // list of industries from campaign/industries.csv
                                                        (
                                                            Industries.SPACEPORT
                                                        ),
                                                        false,
                                                        false,
                                                        false,
                                                        false,
                                                        false,
                                                        true);

        // Orbiting Space Station
        SectorEntityToken yureiStation = system.addCustomEntity("yurei_station", "Yurei Station", "station_hightech3", "tritachyon");
        yureiStation.setCircularOrbitPointingDown(yurei, 0, stationDist, 360f);
        yureiStation.setCustomDescriptionId("lazarus_yurei_station");
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
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        //#region Jump Points ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("fringe_jump", "Fringe System Jump");
        jumpPoint.setCircularOrbit(system.getEntityById("Erythema"), 2, jumpFringeDist, 4000f);
        jumpPoint.setStandardWormholeToHyperspaceVisual();

        system.addEntity(jumpPoint);
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        //#region Inactive Gate ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        //#region Rubble and Debris ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        //#endregion ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    }
}
