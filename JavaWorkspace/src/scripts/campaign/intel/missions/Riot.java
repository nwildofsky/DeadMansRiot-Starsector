package scripts.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfigGen;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager.RemnantFleetInteractionConfigGen;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import scripts.campaign.IsolatedBattleFleetInteractionConfigGen;
import scripts.world.systems.LazarusSystem;

import java.awt.*;
import java.util.List;
import java.util.Map;

import org.lazywizard.lazylib.campaign.CampaignUtils;


public class Riot extends HubMissionWithBarEvent implements FleetEventListener, ColonyPlayerHostileActListener
{

    // mission stages
    public static enum Stage 
    {
        REACH_SYSTEM,
        CONTACT_COMMANDERS,
        JOIN_BATTLE,
        AFTER_ACTION_REPORT,
        RAID_PLANET,
        GRAB_CORE,
        DEFEND_SELF,
        CONTACT_GIVER,
        COMPLETED,
        FAILED,
    }

    // important objects, systems and people
    protected CampaignFleetAPI tritachyonFleet;
    protected CampaignFleetAPI luddicpathFleet;
    protected CampaignFleetAPI tritachyonBetrayalFleet;
    protected CampaignFleetAPI luddicpathBetrayalFleet;

    protected CampaignFleetAPI winningFleet;
    protected PersonAPI tritachyonCommander;
    protected PersonAPI luddicpathCommander;
    protected StarSystemAPI system;
    protected MarketAPI initialMarket;

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
        initialMarket = createdAt;
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

        // set a global reference we can use, useful for once-off missions.
        if (!setGlobalReference("$riot_ref")) return false;

        // set our starting, success and failure stages
        setStartingStage(Stage.REACH_SYSTEM);
        setStageOnEnteredLocation(Stage.CONTACT_COMMANDERS, system);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);

        // set stage transitions when certain global flags are set, and when certain flags are set on the questgiver
        setStageOnMemoryFlag(Stage.JOIN_BATTLE, person, "$riot_allySelected");
        setStageOnMemoryFlag(Stage.AFTER_ACTION_REPORT, person, "$riot_afteraction");
        setStageOnMemoryFlag(Stage.RAID_PLANET, person, "$riot_raidplanet");
        setStageOnMemoryFlag(Stage.GRAB_CORE, person, "$riot_grabcore");
        setStageOnMemoryFlag(Stage.DEFEND_SELF, person, "$riot_defendself");
        setStageOnMemoryFlag(Stage.COMPLETED, person, "$riot_completed");
        setStageOnMemoryFlag(Stage.FAILED, person, "$riot_failed" );
        // set time limit and credit reward
        setCreditReward(CreditReward.HIGH);

        return true;
    }

    public void accept(InteractionDialogAPI dialog, java.util.Map<java.lang.String, MemoryAPI> memoryMap)
    {
        super.accept(dialog, memoryMap);
        
        createMissionFleets(1);

        // Set up Mission Stage transition triggers, so code can be called at that momment in time
        final Riot base = this;

        // Transition goal from REACH_SYSTEM to CONTACT_COMMANDERS
        beginStageTrigger(Stage.CONTACT_COMMANDERS);
        triggerRunScriptAfterDelay(0f, new Script() {
            @Override
            public void run()
            {
                Global.getLogger(this.getClass()).info("Lazarus System reached, trigger encountered!");
                LazarusSystem.addMarketAIAdmin();
            }
        });
        endTrigger();

        // Transition goal from CONTACT_COMMANDERS to JOIN_BATTLE
        beginStageTrigger(Stage.JOIN_BATTLE);
        triggerRunScriptAfterDelay(0f, new Script() {
            @Override
            public void run()
            {
                Global.getLogger(this.getClass()).info("Ally fleet was selected, trigger encountered!");
            }
        });
        endTrigger();

        // Transition goal from JOIN_BATTLE to AFTER_ACTION_REPORT
        beginStageTrigger(Stage.AFTER_ACTION_REPORT);
        triggerRunScriptAfterDelay(0f, new Script() {
            @Override
            public void run()
            {
                Global.getLogger(this.getClass()).info("First battle has finished, trigger encountered!");
                tritachyonFleet.removeEventListener(base);
            }
        });
        endTrigger();

        // Transition goal from AFTER_ACTION_REPORT to RAID_PLANET
        beginStageTrigger(Stage.RAID_PLANET);
        triggerRunScriptAfterDelay(0f, new Script() {
            @Override
            public void run()
            {
                Global.getLogger(this.getClass()).info("Raid planet new objective, trigger encountered!");
                Global.getSector().getListenerManager().addListener(base);
            }
        });
        endTrigger();

        // Transition goal from RAID_PLANET to GRAB_CORE
        beginStageTrigger(Stage.GRAB_CORE);
        triggerRunScriptAfterDelay(0f, new Script() {
            @Override
            public void run()
            {
                Global.getLogger(this.getClass()).info("Planet has been raided, trigger encountered!");
                Global.getSector().getListenerManager().removeListener(base);
                LazarusSystem.addBerserkDebris();
            }
        });
        // triggerRunScriptAfterDelay(.2f, new Script() {
        //     @Override
        //     public void run()
        //     {
        //         Global.getLogger(this.getClass()).info("Berserk Weapon added to debris, delay trigger encountered!");
        //         LazarusSystem.addModWeaponToDebris();
        //     }
        // });
        endTrigger();

        // Transition goal from GRAB_CORE to DEFEND_SELF
        beginStageTrigger(Stage.DEFEND_SELF);
        triggerRunScriptAfterDelay(0f, new Script() {
            @Override
            public void run()
            {
                Global.getLogger(this.getClass()).info("Yurei Planet market was raided, trigger encountered!");
                createMissionFleets(2);
            }
        });
        endTrigger();

        // Transition goal from DEFEND_SELF to CONTACT_GIVER
        beginStageTrigger(Stage.CONTACT_GIVER);
        triggerRunScriptAfterDelay(0f, new Script() {
            @Override
            public void run()
            {
                Global.getLogger(this.getClass()).info("Second battle has finished, trigger encountered!");
            }
        });
        endTrigger();

        // Transition from any to Mission Complete
        beginStageTrigger(Stage.COMPLETED);
        triggerRunScriptAfterDelay(0f, new Script() {
            @Override
            public void run()
            {
                Global.getLogger(this.getClass()).info("Quest giver has been re-contacted, mission complete, trigger encountered!");
            }
        });
        endTrigger();

        // Transition from any to Mission Fail
        beginStageTrigger(Stage.FAILED);
        triggerRunScriptAfterDelay(0f, new Script() {
            @Override
            public void run()
            {
                Global.getLogger(this.getClass()).info("Mission failed, trigger encountered!");
            }
        });
        endTrigger();
    }

    private void createMissionFleets(int timesCalled)
    {
        //Called once for the initial fleets
        if(timesCalled == 1){
            tritachyonFleet = Global.getFactory().createEmptyFleet(Factions.TRITACHYON, "Lazarus Tri-Tachyon Fleet", true);
            CampaignUtils.addShipToFleet("aurora_Balanced", FleetMemberType.SHIP, tritachyonFleet);
            CampaignUtils.addShipToFleet("medusa_CS", FleetMemberType.SHIP, tritachyonFleet);
            CampaignUtils.addShipToFleet("harbinger_Strike", FleetMemberType.SHIP, tritachyonFleet);
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
            tritachyonFleet.setTransponderOn(true);

            tritachyonFleet.getMemoryWithoutUpdate().set("$riot_tritachfleet", true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);

            system.addEntity(tritachyonFleet);

            luddicpathFleet = Global.getFactory().createEmptyFleet(Factions.LUDDIC_PATH, "Lazarus Luddic Path Fleet", true);
            CampaignUtils.addShipToFleet("venture_pather_Attack", FleetMemberType.SHIP, luddicpathFleet);
            CampaignUtils.addShipToFleet("colossus2_Pather", FleetMemberType.SHIP, luddicpathFleet);
            CampaignUtils.addShipToFleet("colossus2_Pather", FleetMemberType.SHIP, luddicpathFleet);
            CampaignUtils.addShipToFleet("manticore_luddic_path_Strike", FleetMemberType.SHIP, luddicpathFleet);
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
            CampaignUtils.addShipToFleet("hound_luddic_path_Attack", FleetMemberType.SHIP, luddicpathFleet);

            luddicpathFleet.setCommander(luddicpathCommander);
            luddicpathFleet.getFlagship().setCaptain(luddicpathCommander);
            luddicpathFleet.setLocation(LazarusSystem.GetCombatLoc2().getLocation().x, LazarusSystem.GetCombatLoc2().getLocation().y);
            luddicpathFleet.addAssignment(FleetAssignment.HOLD, LazarusSystem.GetCombatLoc2(), 1000000f);
            luddicpathFleet.setNoFactionInName(true);
            luddicpathFleet.forceSync();
            luddicpathFleet.getFleetData().setSyncNeeded();
            luddicpathFleet.getFleetData().syncIfNeeded();
            luddicpathFleet.setTransponderOn(true);

            luddicpathFleet.getMemoryWithoutUpdate().set("$riot_luddicpathfleet", true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);

            system.addEntity(luddicpathFleet);
        }
        
        //Called a second time for the betrayal fleets
        if(timesCalled == 2){

            //If player sided with Tritach
            if(winningFleet == tritachyonFleet){

                tritachyonBetrayalFleet = Global.getFactory().createEmptyFleet(Factions.TRITACHYON, "Lazarus Tri-Tachyon Fleet", true);
                CampaignUtils.addShipToFleet("paragon_Escort", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("aurora_Balanced", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("fury_Attack", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("medusa_CS", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("harbinger_Strike", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("harbinger_Strike", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("shrike_Support", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("shrike_Attack", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("wolf_Strike", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("wolf_Strike", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("wolf_PD", FleetMemberType.SHIP, tritachyonBetrayalFleet);

                tritachyonBetrayalFleet.setCommander(tritachyonCommander);
                tritachyonBetrayalFleet.getFlagship().setCaptain(tritachyonCommander);
                tritachyonBetrayalFleet.setLocation(LazarusSystem.GetCombatLoc1().getLocation().x, LazarusSystem.GetCombatLoc1().getLocation().y);
                tritachyonBetrayalFleet.addAssignment(FleetAssignment.INTERCEPT, Global.getSector().getPlayerFleet(), 1000000f);
                tritachyonBetrayalFleet.setNoFactionInName(true);
                tritachyonBetrayalFleet.forceSync();
                tritachyonBetrayalFleet.getFleetData().setSyncNeeded();
                tritachyonBetrayalFleet.getFleetData().syncIfNeeded();
                tritachyonBetrayalFleet.setTransponderOn(true);

                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set("$riot_tritachfleet", true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);

                system.addEntity(tritachyonBetrayalFleet);
            }

            //If player sided with LP
            if(winningFleet == luddicpathFleet){
                
                luddicpathBetrayalFleet = Global.getFactory().createEmptyFleet(Factions.LUDDIC_PATH, "Lazarus Luddic Path Fleet", true);
                CampaignUtils.addShipToFleet("prometheus2_Standard", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("venture_pather_Attack", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("eradicator_Outdated", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("colossus2_Pather", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("colossus2_Pather", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("manticore_luddic_path_Strike", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("manticore_luddic_path_Strike", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("hammerhead_Balanced", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("hammerhead_Elite", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("enforcer_Overdriven", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("kite_luddic_path_Raider", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("kite_luddic_path_Raider", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("hound_luddic_path_Attack", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("hound_luddic_path_Attack", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                luddicpathBetrayalFleet.setCommander(luddicpathCommander);
                luddicpathBetrayalFleet.getFlagship().setCaptain(luddicpathCommander);
                luddicpathBetrayalFleet.setLocation(LazarusSystem.GetCombatLoc2().getLocation().x, LazarusSystem.GetCombatLoc2().getLocation().y);
                luddicpathBetrayalFleet.addAssignment(FleetAssignment.INTERCEPT, Global.getSector().getPlayerFleet(), 1000000f);
                luddicpathBetrayalFleet.setNoFactionInName(true);
                luddicpathBetrayalFleet.forceSync();
                luddicpathBetrayalFleet.getFleetData().setSyncNeeded();
                luddicpathBetrayalFleet.getFleetData().syncIfNeeded();
                luddicpathBetrayalFleet.setTransponderOn(true);

                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set("$riot_luddicpathfleet", true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);

                system.addEntity(luddicpathBetrayalFleet);
            }

        }
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
    {
        if (action.equals("helpTritach"))
        {
            luddicpathFleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
            StartBattle(luddicpathFleet, tritachyonFleet, dialog);
            
            return true;
        }
        if (action.equals("helpLuddic"))
        {
            tritachyonFleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
            StartBattle(tritachyonFleet, luddicpathFleet, dialog);
            return true;
        }
        //This will start after the dialog with the winning fleet after battle 1. You can add other things here if needed!
        if (action.equals("startRaid"))
        {
            getPerson().getMemoryWithoutUpdate().set("$riot_raidplanet", true);
            return true;
        }
        return false;
    }

    public void StartBattle(CampaignFleetAPI targetFleet, CampaignFleetAPI helpingFleet, InteractionDialogAPI dialog)
    {
        getPerson().getMemoryWithoutUpdate().set("$riot_allySelected", true);
        helpingFleet.getMemoryWithoutUpdate().set("$riot_allySelected", true);

        luddicpathFleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        //luddicpathFleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
        luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_FIGHT_TO_THE_LAST, true);
        luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN, 
            new IsolatedBattleFleetInteractionConfigGen());
        
        tritachyonFleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        //tritachyonFleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
        tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_FIGHT_TO_THE_LAST, true);
        tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN, 
            new IsolatedBattleFleetInteractionConfigGen());
        
        tritachyonFleet.addEventListener(this);
        
        dialog.dismissAsCancel();
        Global.getSector().getPlayerFleet().getBattle().leave(Global.getSector().getPlayerFleet(), false);
        helpingFleet.getBattle().leave(helpingFleet, false);
        Global.getFactory().createBattle(helpingFleet, targetFleet);
        


        //An attempt was made here ;-;
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        //BattleCreationContext dogfight = new BattleCreationContext(Global.getSector().getPlayerFleet(), FleetGoal.ATTACK, targetFleet, FleetGoal.ATTACK);

        // FleetInteractionDialogPluginImpl testPlugin = (FleetInteractionDialogPluginImpl)dialog.getPlugin();

        // FleetEncounterContext encounterContext = (FleetEncounterContext)testPlugin.getContext();
        
        // encounterContext.setBattle(dialog.startBattle(dogfight));
        
        // BattleAPI firstCombat = Global.getSector().getPlayerFleet().getBattle(); //Gets the battle the player is in

        // firstCombat.join(helpingFleet);
        
        // helpingFleet.getBattle().uncombine();
        // helpingFleet.getBattle().genCombined();
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

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle)
    {
        Global.getLogger(this.getClass()).info("Fleet Listener found battle! Battle is " + (battle.isDone() ? "done. " : "not done. ")
            + "Result is " + result + ". Tritachyon fleet was " + (battle.isInvolved(tritachyonFleet) ? "involved. " : "not involved. ")
            + "Fleet is " + fleet + ". PrimaryWinner is " + primaryWinner + ".");

        // also credit the player if they're in the same location as the fleet and nearby
        float distToPlayer = Misc.getDistance(fleet, Global.getSector().getPlayerFleet());
        boolean playerInvolved = battle.isPlayerInvolved() || (fleet.isInCurrentLocation() && distToPlayer < 2000f);

        if (primaryWinner.equals(tritachyonFleet))
        {
            winningFleet = tritachyonFleet;
            getPerson().getMemoryWithoutUpdate().set("$riot_afteraction", true);
            tritachyonFleet.getMemoryWithoutUpdate().set("$riot_tritachpostbattle", true);
            tritachyonFleet.getMemoryWithoutUpdate().unset("$riot_tritachfleet");
            Global.getLogger(this.getClass()).info("Tritachyon fleet wins!");
            //tritachyonFleet.addAssignment(FleetAssignment.HOLD, LazarusSystem.GetCombatLoc1(), 1000000f);
        }
        else if (primaryWinner.equals(luddicpathFleet))
        {
            winningFleet = luddicpathFleet;
            getPerson().getMemoryWithoutUpdate().set("$riot_afteraction", true);
            luddicpathFleet.getMemoryWithoutUpdate().set("$riot_luddicpathpostbattle", true);
            luddicpathFleet.getMemoryWithoutUpdate().unset("$riot_luddicpathfleet");
            Global.getLogger(this.getClass()).info("Luddic Path fleet wins!");
            //luddicpathFleet.addAssignment(FleetAssignment.HOLD, LazarusSystem.GetCombatLoc2(), 1000000f);
        }
    }

    // if the fleet despawns for whatever reason, fail the mission
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        // This does not work as I thought it did and we're not currently using it anyway -Nathan
        // if (isDone() || result != null) return;

        // Global.getLogger(this.getClass()).info("Fleet reported as despawned! Current fleet was "
        //     + (Global.getSector().getPlayerFleet().getBattle().isInvolved(fleet) ? "involved" : "not involved")
        //     + " in a player battle when it despawned");
        // if (!Global.getSector().getPlayerFleet().getBattle().isInvolved(fleet))
        // {
        //     if (fleet.getMemoryWithoutUpdate().contains("$riot_tritachfleet") || fleet.getMemoryWithoutUpdate().contains("$riot_luddicpathfleet"))
        //     {
        //         getPerson().getMemoryWithoutUpdate().set("$riot_failed", true);
        //     }
        // }
    }

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, CargoAPI cargo) 
    {
        if(market.getId().equals("yurei_market"))
        {
            if(currentStage == Stage.RAID_PLANET)
            {
                getPerson().getMemoryWithoutUpdate().set("$riot_grabcore", true);
            }
        }
    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, Industry cargo) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'reportRaidToDisruptFinished'");
    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI arg0, MarketAPI arg1, TempData arg2) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'reportSaturationBombardmentFinished'");
    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI arg0, MarketAPI arg1, TempData arg2) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'reportTacticalBombardmentFinished'");
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
        } else if (currentStage == Stage.AFTER_ACTION_REPORT) {
            info.addPara("Talk to the commander of the fleet you aided .", opad);
        } else if (currentStage == Stage.RAID_PLANET) {
            info.addPara("Raid Yurei for the AI", opad);
        } else if (currentStage == Stage.GRAB_CORE) {
            info.addPara("Grab the AI core in the debris field", opad);
        } else if (currentStage == Stage.DEFEND_SELF) {
            info.addPara("Report back to the fleet commander with the AI.", opad);
        } else if (currentStage == Stage.CONTACT_GIVER) {
            info.addPara("Go back to " + getPerson().getNameString() + ".", opad);
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
        } else if (currentStage == Stage.AFTER_ACTION_REPORT) {
            info.addPara("Talk to the fleet commander of the faction you have aided.", tc, pad);
            return true;
        } else if (currentStage == Stage.RAID_PLANET) {
            info.addPara("Raid the planet Yurei for the AI core.", tc, pad);
            return true;
        } else if (currentStage == Stage.GRAB_CORE) {
            info.addPara("Grab the Rogue AI Core in the debris field.", tc, pad);
            return true;
        } else if (currentStage == Stage.DEFEND_SELF) {
            info.addPara("Return to the fleet that you aided with the AI core.", tc, pad);
            return true;
        } else if (currentStage == Stage.CONTACT_GIVER) {
            info.addPara("Return to the bar that you accepted the mission in.", tc, pad);
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
        else if (currentStage == Stage.AFTER_ACTION_REPORT) 
            return getMapLocationFor(system.getCenter());
        else if (currentStage == Stage.RAID_PLANET) 
            return getMapLocationFor(system.getCenter());
        else if (currentStage == Stage.GRAB_CORE) 
            return getMapLocationFor(system.getCenter());
        else if (currentStage == Stage.DEFEND_SELF) 
            return getMapLocationFor(system.getCenter());
        else if (currentStage == Stage.CONTACT_GIVER) 
            return getMapLocationFor(initialMarket.getPrimaryEntity());
        return null;
    }

    // mission name
    @Override
    public String getBaseName() {
        return "Dead Man's Riot";
    }
}