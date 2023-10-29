package scripts.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import scripts.world.systems.LazarusSystem;

import java.awt.*;
import java.util.List;
import java.util.Map;

import org.lazywizard.lazylib.campaign.CampaignUtils;


public class Riot extends HubMissionWithBarEvent implements FleetEventListener 
{

    // mission stages
    public static enum Stage 
    {
        REACH_SYSTEM,
        CONTACT_COMMANDERS,
        JOIN_BATTLE,
        AFTER_ACTION_REPORT,
        RAID_PLANET,
        DEFEND_SELF,
        CONTACT_GIVER,
        COMPLETED,
        FAILED,
    }

    // important objects, systems and people
    protected CampaignFleetAPI tritachyonFleet;
    protected CampaignFleetAPI luddicpathFleet;
    protected CampaignFleetAPI winningFleet;
    protected PersonAPI tritachyonCommander;
    protected PersonAPI luddicpathCommander;
    protected StarSystemAPI system;
    protected boolean createdFleets = false;

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

        tritachyonCommander = Global.getSector().getFaction(Factions.TRITACHYON).createRandomPerson();
        tritachyonCommander.setRankId(Ranks.SPACE_ADMIRAL);
        tritachyonCommander.setPostId(Ranks.POST_AGENT);
        tritachyonCommander.getMemoryWithoutUpdate().set("$riot_tritachComm", true);

        luddicpathCommander = Global.getSector().getFaction(Factions.LUDDIC_PATH).createRandomPerson();
        luddicpathCommander.setRankId(Ranks.BROTHER);
        luddicpathCommander.setPostId(Ranks.POST_TERRORIST);
        luddicpathCommander.getMemoryWithoutUpdate().set("$riot_luddicpathComm", true);

        // Get the Lazarus system
        requireSystemIs(Global.getSector().getStarSystem("lazarus"));

        system = pickSystem(true);
        if (system == null) return false;



        if(!createdFleets)
        {
            tritachyonFleet = Global.getFactory().createEmptyFleet(Factions.TRITACHYON, "Lazarus Tri-Tachyon Fleet", true);
            CampaignUtils.addShipToFleet("aurora_Balanced", FleetMemberType.SHIP, tritachyonFleet);
            CampaignUtils.addShipToFleet("medusa_CS", FleetMemberType.SHIP, tritachyonFleet);
            CampaignUtils.addShipToFleet("harbinger_Strike", FleetMemberType.SHIP, tritachyonFleet);
            CampaignUtils.addShipToFleet("shrike_Support", FleetMemberType.SHIP, tritachyonFleet);
            CampaignUtils.addShipToFleet("shrike_Attack", FleetMemberType.SHIP, tritachyonFleet);
            CampaignUtils.addShipToFleet("wolf_Strike", FleetMemberType.SHIP, tritachyonFleet);
            CampaignUtils.addShipToFleet("wolf_Strike", FleetMemberType.SHIP, tritachyonFleet);
            CampaignUtils.addShipToFleet("wolf_PD", FleetMemberType.SHIP, tritachyonFleet);
            tritachyonFleet.setCommander(tritachyonCommander);
            tritachyonFleet.getFlagship().setCaptain(tritachyonCommander);
            tritachyonFleet.setLocation(LazarusSystem.GetCombatLoc1().getLocation().x, LazarusSystem.GetCombatLoc1().getLocation().y);
            tritachyonFleet.addAssignment(FleetAssignment.HOLD, LazarusSystem.GetCombatLoc1(), 1000000f);
            tritachyonFleet.setNoFactionInName(true);
            tritachyonFleet.forceSync();
            tritachyonFleet.getFleetData().setSyncNeeded();
            tritachyonFleet.getFleetData().syncIfNeeded();
            tritachyonFleet.getMemoryWithoutUpdate().set("$riot_tritachfleet", true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);
            tritachyonFleet.setTransponderOn(true);

            system.addEntity(tritachyonFleet);

            luddicpathFleet = Global.getFactory().createEmptyFleet(Factions.LUDDIC_PATH, "Lazarus Luddic Path Fleet", true);
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
            luddicpathFleet.setCommander(luddicpathCommander);
            luddicpathFleet.getFlagship().setCaptain(luddicpathCommander);
            luddicpathFleet.setLocation(LazarusSystem.GetCombatLoc2().getLocation().x, LazarusSystem.GetCombatLoc2().getLocation().y);
            luddicpathFleet.addAssignment(FleetAssignment.HOLD, LazarusSystem.GetCombatLoc2(), 1000000f);
            luddicpathFleet.setNoFactionInName(true);
            luddicpathFleet.forceSync();
            luddicpathFleet.getFleetData().setSyncNeeded();
            luddicpathFleet.getFleetData().syncIfNeeded();
            luddicpathFleet.getMemoryWithoutUpdate().set("$riot_luddicpathfleet", true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);
            luddicpathFleet.setTransponderOn(true);

            system.addEntity(luddicpathFleet);
            createdFleets = true;
        }

        // set a global reference we can use, useful for once-off missions.
        if (!setGlobalReference("$riot_ref")) return false;

        // set our starting, success and failure stages
        setStartingStage(Stage.REACH_SYSTEM);
        setStageOnEnteredLocation(Stage.CONTACT_COMMANDERS, system);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);

        // set stage transitions when certain global flags are set, and when certain flags are set on the questgiver
        setStageOnGlobalFlag(Stage.JOIN_BATTLE, "$riot_allySelected");
        setStageOnMemoryFlag(Stage.AFTER_ACTION_REPORT, person, "$riot_afteraction");
        setStageOnMemoryFlag(Stage.COMPLETED, person, "$riot_completed");
        setStageOnMemoryFlag(Stage.FAILED, person, "$riot_failed" );
        // set time limit and credit reward
        setCreditReward(CreditReward.HIGH);

        return true;
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
    {
        if(action.equals("helpTritach"))
        {
            luddicpathFleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
            StartBattle();
            
            return true;
        }
        if(action.equals("helpLuddic"))
        {
            tritachyonFleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
            StartBattle();
            return true;
        }
        return false;
    }
    public void StartBattle()
    {
        luddicpathFleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        luddicpathFleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
        luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_FIGHT_TO_THE_LAST, true);
        luddicpathFleet.removeFirstAssignment();
        luddicpathFleet.addAssignment(FleetAssignment.INTERCEPT, tritachyonFleet, 9999999);
        luddicpathFleet.setInteractionTarget(tritachyonFleet);
        tritachyonFleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        tritachyonFleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
        tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_FIGHT_TO_THE_LAST, true);
        tritachyonFleet.removeFirstAssignment();
        tritachyonFleet.addAssignment(FleetAssignment.INTERCEPT, luddicpathFleet, 9999999);
        tritachyonFleet.setInteractionTarget(luddicpathFleet);


    }

    // during the initial dialogue and in any dialogue where we use "Call $riot_ref updateData", these values will be put in memory
    // here, used so we can, say, type $riot_personName and automatically insert the quest giver's name
    //It's supposed to do that, but sometimes it will decide not to :)
    protected void updateInteractionDataImpl() {
        set("$riot_barEvent", isBarEvent());
        set("$riot_manOrWoman", getPerson().getManOrWoman());
        set("$riot_heOrShe", getPerson().getHeOrShe());
        set("$riot_himOrHer", getPerson().getHimOrHer());
        set("$riot_hisOrHer", getPerson().getHisOrHer());
        set("$riot_reward", Misc.getWithDGS(getCreditsReward()));

        set("$riot_personName", getPerson().getNameString());
        set("$riot_tritachCommName", tritachyonCommander.getNameString());
        set("$riot_luddicpathCommName", luddicpathCommander.getNameString());
        set("$riot_systemName", system.getNameWithLowercaseTypeShort());
        set("$riot_dist", getDistanceLY(system));
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (isDone() || result != null) return;

        // also credit the player if they're in the same location as the fleet and nearby
        float distToPlayer = Misc.getDistance(fleet, Global.getSector().getPlayerFleet());
        boolean playerInvolved = battle.isPlayerInvolved() || (fleet.isInCurrentLocation() && distToPlayer < 2000f);

        //If it's a battle between the two fleets
        //NOTE: I'M PRETTY SURE THIS DOESN'T DO WHAT I WANT IT TO, THEREFORE IT'S COMMENTED OUT.
        /*
        if(battle.isInvolved(tritachyonFleet) && battle.isInvolved(luddicpathFleet))
        {
            if(primaryWinner.equals(tritachyonFleet))
            {
                winningFleet = tritachyonFleet;
                getPerson().getMemoryWithoutUpdate().set("$riot_afteraction", true);
                tritachyonFleet.getMemoryWithoutUpdate().set("$riot_survived", true);
                tritachyonFleet.removeFirstAssignment();
                tritachyonFleet.addAssignment(FleetAssignment.HOLD, LazarusSystem.GetCombatLoc1(), 1000000f);
                return;
            }
            if(primaryWinner.equals(luddicpathFleet))
            {
                winningFleet = luddicpathFleet;
                getPerson().getMemoryWithoutUpdate().set("$riot_afteraction", true);
                luddicpathFleet.getMemoryWithoutUpdate().set("$riot_survived", true);
                luddicpathFleet.removeFirstAssignment();
                luddicpathFleet.addAssignment(FleetAssignment.HOLD, LazarusSystem.GetCombatLoc2(), 1000000f);
                return;
            }
        }
        */


        if (!playerInvolved || !battle.isInvolved(fleet) || battle.onPlayerSide(fleet)) {
            return;
        }
    }

    // if the fleet despawns for whatever reason, fail the mission
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (isDone() || result != null) return;

        if (fleet.getMemoryWithoutUpdate().contains("$riot_tritachfleet") || fleet.getMemoryWithoutUpdate().contains("$riot_luddicpathfleet")) {
            getPerson().getMemoryWithoutUpdate().set("$riot_failed", true);
        }
    }

    // description when selected in intel screen
    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        //This is about how far I have the quest working reliably
        if (currentStage == Stage.REACH_SYSTEM) {
            info.addPara("Investigate the " +
                    system.getNameWithLowercaseTypeShort() + ".", opad);
        } else if (currentStage == Stage.CONTACT_COMMANDERS) {
            info.addPara("Find out who else is in the " +
                    system.getNameWithLowercaseTypeShort() + ".", opad);
        } else if (currentStage == Stage.JOIN_BATTLE) {
            info.addPara("Join the battle in the " +
                    system.getNameWithLowercaseTypeShort() + ".", opad);
        }
    }

    // short description in popups and the intel entry
    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();

        //This is about how far I have the quest working reliably
        if (currentStage == Stage.REACH_SYSTEM) {
            info.addPara("Reach the " +
                    system.getNameWithLowercaseTypeShort(), tc, pad);
            return true;
        } else if (currentStage == Stage.CONTACT_COMMANDERS) {
            info.addPara("Search the " +
                    system.getNameWithLowercaseTypeShort()  + " for other signs of life.", tc, pad);
            return true;
        } else if (currentStage == Stage.JOIN_BATTLE) {
            info.addPara("Join the battle in the " +
                    system.getNameWithLowercaseTypeShort()  + " with the fleet you promised to aid.", tc, pad);
            return true;
        }
        return false;
    }

    // where on the map the intel screen tells us to go
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) 
    {
        //This is about how far I have the quest working reliably
        if (currentStage == Stage.REACH_SYSTEM) 
            return getMapLocationFor(system.getCenter());
        else if (currentStage == Stage.CONTACT_COMMANDERS) 
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