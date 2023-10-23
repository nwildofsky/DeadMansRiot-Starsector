package scripts.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import scripts.world.systems.LazarusSystem;

import java.awt.*;

import org.lazywizard.lazylib.campaign.CampaignUtils;

import static com.fs.starfarer.api.impl.campaign.ids.FleetTypes.PATROL_MEDIUM;

public class Riot extends HubMissionWithBarEvent implements FleetEventListener 
{

    // mission stages
    public static enum Stage 
    {
        REACH_SYSTEM,
        JOIN_BATTLE,
        RAID_PLANET,
        DEFEND_SELF,
        CONTACT_GIVER,
        COMPLETED,
        FAILED,
    }

    // important objects, systems and people
    protected CampaignFleetAPI tritachyonFleet; //CHANGE TO TRI-TACH AND ADD LUDDIC FLEETS - Dominic
    protected CampaignFleetAPI luddicpathFleet;
    protected PersonAPI executive; //RENAME TO TRI-TACH COMMANDER AND MAKE LUDDIC COMMANDER - Dominic
    protected StarSystemAPI system;

    // Mission only spawns in Tri-Tach bars
    public boolean shouldShowAtMarket(MarketAPI market) {
        return market.getFactionId().equals(Factions.TRITACHYON);
    }

    // run when the bar event starts / when we ask a contact about the mission
    protected boolean create(MarketAPI createdAt, boolean barEvent) 
    {

        setGiverRank(Ranks.AGENT);
        setGiverPost(Ranks.POST_EXECUTIVE); //DOES THIS POST MAKE SENSE? - Dominic
        setGiverImportance(PersonImportance.HIGH);
        setGiverFaction(Factions.TRITACHYON);
        setGiverTags(Tags.CONTACT_UNDERWORLD);
        setGiverVoice(Voices.BUSINESS); //I SHOULD LOOK INTO CHANGING THIS VOICE - Dominic
        findOrCreateGiver(createdAt, false, false);
        

        PersonAPI person = getPerson();
        if (person == null) return false;

        // setting the mission ref allows us to use the Call rulecommand in their dialogues, so that we can make this script do things
        if (!setPersonMissionRef(person, "$riot_ref")) {
            return false;
        }


        setGiverIsPotentialContactOnSuccess(1f);

        //NEED TO CHANGE THIS TO A TRI-TACH COMMANDER AND ALSO ADD A LUDDIC COMMANDER - Dominic
        // set up the disgraced executive
        executive = Global.getSector().getFaction(Factions.TRITACHYON).createRandomPerson();
        executive.setRankId(Ranks.SPACE_ADMIRAL);
        executive.setPostId(Ranks.POST_SENIOR_EXECUTIVE);
        executive.getMemoryWithoutUpdate().set("$riot_exec", true);

        // Get the Lazarus system
        requireSystemIs(Global.getSector().getStarSystem("lazarus"));

        system = pickSystem(true);
        if (system == null) return false;


        //TEMPLATE FOR MAKING THE TRI-TACH AND LUDDIC FLEETS. WILL BE REPLACED SOON ENOUGH - Dominic
        
        tritachyonFleet = Global.getFactory().createEmptyFleet(Factions.TRITACHYON, "Lazarus Tri-Tachyon Fleet", false);
        CampaignUtils.addShipToFleet("aurora_Balanced", FleetMemberType.SHIP, tritachyonFleet);
        CampaignUtils.addShipToFleet("medusa_CS", FleetMemberType.SHIP, tritachyonFleet);
        CampaignUtils.addShipToFleet("harbinger_Strike", FleetMemberType.SHIP, tritachyonFleet);
        CampaignUtils.addShipToFleet("shrike_Support", FleetMemberType.SHIP, tritachyonFleet);
        CampaignUtils.addShipToFleet("shrike_Attack", FleetMemberType.SHIP, tritachyonFleet);
        CampaignUtils.addShipToFleet("wolf_Strike", FleetMemberType.SHIP, tritachyonFleet);
        CampaignUtils.addShipToFleet("wolf_Strike", FleetMemberType.SHIP, tritachyonFleet);
        CampaignUtils.addShipToFleet("wolf_PD", FleetMemberType.SHIP, tritachyonFleet);
        tritachyonFleet.setLocation(LazarusSystem.GetCombatLoc1().getLocation().x, LazarusSystem.GetCombatLoc1().getLocation().y);
        //tritachFleet.setCircularOrbitPointingDown(fleetCombatLoc1, 0, 0, 0);
        tritachyonFleet.addAssignment(FleetAssignment.HOLD, LazarusSystem.GetCombatLoc1(), 1000000f);
        //tritachFleet.setAI(null);
        //tritachFleet.setNoEngaging(1000000f);
        //tritachFleet.setDoNotAdvanceAI(true);
        tritachyonFleet.setNoFactionInName(true);
        tritachyonFleet.forceSync();
        tritachyonFleet.getFleetData().setSyncNeeded();
        tritachyonFleet.getFleetData().syncIfNeeded();
        //tritachFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        //tritachFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY, true);
        // tritachFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        // tritachFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        // tritachFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);
        tritachyonFleet.getMemoryWithoutUpdate().set("$riot_tritachfleet", true);
        tritachyonFleet.setTransponderOn(true);
        system.addEntity(tritachyonFleet);

        luddicpathFleet = Global.getFactory().createEmptyFleet(Factions.LUDDIC_PATH, "Lazarus Luddic Path Fleet", false);
        CampaignUtils.addShipToFleet("venture_pather_Attack", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("colossus2_Pather", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("manticore_luddic_path_Strike", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("manticore_luddic_path_Strike", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("hammerhead_Balanced", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("hammerhead_Elite", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("enforcer_Overdriven", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("kite_luddic_path_Raider", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("kite_luddic_path_Raider", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("kite_luddic_path_Raider", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("kite_luddic_path_Raider", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("hound_luddic_path_Attack", FleetMemberType.SHIP, luddicpathFleet);
        CampaignUtils.addShipToFleet("hound_luddic_path_Attack", FleetMemberType.SHIP, luddicpathFleet);
        //patherFleet.setCircularOrbitPointingDown(fleetCombatLoc2, 0, 0, 0);
        luddicpathFleet.setLocation(LazarusSystem.GetCombatLoc2().getLocation().x, LazarusSystem.GetCombatLoc2().getLocation().y);
        luddicpathFleet.addAssignment(FleetAssignment.HOLD, LazarusSystem.GetCombatLoc2(), 1000000f);
        //patherFleet.setAI(null);
        //patherFleet.setNoEngaging(1000000f);
        //patherFleet.setDoNotAdvanceAI(true);
        luddicpathFleet.setNoFactionInName(true);
        luddicpathFleet.forceSync();
        luddicpathFleet.getFleetData().setSyncNeeded();
        luddicpathFleet.getFleetData().syncIfNeeded();
        //patherFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        //patherFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY, true);
        // patherFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        // patherFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        // patherFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);
        luddicpathFleet.setTransponderOn(true);
        system.addEntity(luddicpathFleet);

        // set a global reference we can use, useful for once-off missions.
        if (!setGlobalReference("$riot_ref")) return false;

        // set our starting, success and failure stages
        setStartingStage(Stage.REACH_SYSTEM);
        setStageOnEnteredLocation(Stage.JOIN_BATTLE, system);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);

        // set stage transitions when certain global flags are set, and when certain flags are set on the questgiver
        setStageOnGlobalFlag(Stage.JOIN_BATTLE, "$riot_reachedlazarus");
        setStageOnMemoryFlag(Stage.COMPLETED, person, "$riot_completed");
        setStageOnMemoryFlag(Stage.FAILED, person, "$riot_failed" );
        // set time limit and credit reward
        setCreditReward(CreditReward.HIGH);

        return true;
    }

    // during the initial dialogue and in any dialogue where we use "Call $intaff_ref updateData", these values will be put in memory
    // here, used so we can, say, type $intaff_execName and automatically insert the disgraced executive's name
    protected void updateInteractionDataImpl() {
        set("$riot_barEvent", isBarEvent());
        set("$riot_manOrWoman", getPerson().getManOrWoman());
        set("$riot_heOrShe", getPerson().getHeOrShe());
        set("$riot_himOrHer", getPerson().getHimOrHer());
        set("$riot_hisOrHer", getPerson().getHisOrHer());
        set("$riot_reward", Misc.getWithDGS(getCreditsReward()));

        set("$riot_personName", getPerson().getNameString());
        set("$riot_execName", executive.getNameString());
        set("$riot_systemName", system.getNameWithLowercaseTypeShort());
        set("$riot_dist", getDistanceLY(system));
    }

    // used to detect when the executive's fleet is destroyed and complete the mission
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (isDone() || result != null) return;

        // also credit the player if they're in the same location as the fleet and nearby
        float distToPlayer = Misc.getDistance(fleet, Global.getSector().getPlayerFleet());
        boolean playerInvolved = battle.isPlayerInvolved() || (fleet.isInCurrentLocation() && distToPlayer < 2000f);

        if (!playerInvolved || !battle.isInvolved(fleet) || battle.onPlayerSide(fleet)) {
            return;
        }

        getPerson().getMemoryWithoutUpdate().set("$riot_completed", true);

    }

    // if the fleet despawns for whatever reason, fail the mission
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (isDone() || result != null) return;

        if (fleet.getMemoryWithoutUpdate().contains("$riot_execfleet")) {
            getPerson().getMemoryWithoutUpdate().set("$riot_failed", true);
        }
    }

    // description when selected in intel screen
    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        if (currentStage == Stage.REACH_SYSTEM) {
            info.addPara("Investigate the " +
                    system.getNameWithLowercaseTypeShort() + ".", opad);
        } else if (currentStage == Stage.JOIN_BATTLE) {
            info.addPara("Find out who else is in the " +
                    system.getNameWithLowercaseTypeShort() + ".", opad);
        }
    }

    // short description in popups and the intel entry
    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        if (currentStage == Stage.REACH_SYSTEM) {
            info.addPara("Reach the " +
                    system.getNameWithLowercaseTypeShort(), tc, pad);
            return true;
        } else if (currentStage == Stage.JOIN_BATTLE) {
            info.addPara("Search the " +
                    system.getNameWithLowercaseTypeShort()  + " for other signs of life.", tc, pad);
            return true;
        }
        return false;
    }

    // where on the map the intel screen tells us to go
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) 
    {
        if (currentStage == Stage.REACH_SYSTEM) 
            return getMapLocationFor(system.getCenter());
        else if (currentStage == Stage.JOIN_BATTLE) 
            return getMapLocationFor(system.getCenter());
        return null;
    }

    // mission name
    @Override
    public String getBaseName() {
        return "Dead Man's Riot";
    }
}