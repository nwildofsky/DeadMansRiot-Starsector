package scripts.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.ExtraSalvageShownListener;
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


public class Riot extends HubMissionWithBarEvent implements FleetEventListener, ColonyPlayerHostileActListener, ExtraSalvageShownListener, CurrentLocationChangedListener
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
        LEAVE_OR_FIGHT,
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
        switch(luddicpathCommander.getGender())
        {
            case FEMALE:
                luddicpathCommander.setRankId(Ranks.SISTER);
                break;
            case MALE:
                luddicpathCommander.setRankId(Ranks.BROTHER);
                break;
            default:
                luddicpathCommander.setRankId(Ranks.TERRORIST);
                break;
            
        }
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
        setStageOnMemoryFlag(Stage.LEAVE_OR_FIGHT, person, "$riot_leaveorfight");
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
                Global.getSector().getListenerManager().addListener(base);
                LazarusSystem.addMarketAIAdmin();
                luddicpathFleet.setAI(null);
                tritachyonFleet.setAI(null);
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
                if(winningFleet == tritachyonFleet)
                {
                    tritachyonFleet.getMemoryWithoutUpdate().unset("$riot_tritachpostbattle");
                    tritachyonFleet.getMemoryWithoutUpdate().set("$riot_tritachgoraid", true);
                }
                else if(winningFleet == luddicpathFleet)
                {
                    luddicpathFleet.getMemoryWithoutUpdate().unset("$riot_luddicpathpostbattle");
                    luddicpathFleet.getMemoryWithoutUpdate().set("$riot_luddicpathgoraid", true);
                }
                Global.getSector().getListenerManager().addListener(base);
                //LazarusSystem.integrateMarket();
                LazarusSystem.unHideMarket();
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
                LazarusSystem.addBerserkDebris();
            }
        });
        endTrigger();

        // Transition goal from GRAB_CORE to DEFEND_SELF
        beginStageTrigger(Stage.DEFEND_SELF);
        triggerRunScriptAfterDelay(0f, new Script() {
            @Override
            public void run()
            {
                Global.getLogger(this.getClass()).info("Theta canon was picked up in salvage, trigger encountered!");
                createMissionFleets(2);
            }
        });
        endTrigger();

        // Transition goal from DEFEND_SELF to LEAVE_OR_FIGHT
        beginStageTrigger(Stage.LEAVE_OR_FIGHT);
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
                Global.getSector().getListenerManager().removeListener(base);
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
                if(Global.getSector().getListenerManager().hasListener(base))
                    Global.getSector().getListenerManager().removeListener(base);
            }
        });
        endTrigger();
    }

                            //Int to keep track of what quest stage it is
    private void createMissionFleets(int timesCalled)
    {

        //First time called
        if(timesCalled == 1){
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
            tritachyonFleet.setTransponderOn(true);

            tritachyonFleet.getMemoryWithoutUpdate().set("$riot_tritachfleet", true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);
            tritachyonFleet.getMemoryWithoutUpdate().set("$riot_donotkill", true);

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
            luddicpathFleet.setTransponderOn(true);

            luddicpathFleet.getMemoryWithoutUpdate().set("$riot_donotkill", true);
            luddicpathFleet.getMemoryWithoutUpdate().set("$riot_luddicpathfleet", true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);

            system.addEntity(luddicpathFleet);
        }
        
        //Second time called, betrayal fleet
        if(timesCalled == 2){
            if(winningFleet == tritachyonFleet){
                            
                

                //Build the betrayal fleet
                tritachyonBetrayalFleet = Global.getFactory().createEmptyFleet(Factions.TRITACHYON, "Lazarus Tri-Tachyon Fleet", true);
                CampaignUtils.addShipToFleet("odyssey_Balanced", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("aurora_Balanced", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("fury_Support", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("medusa_CS", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("harbinger_Strike", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("shrike_Support", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("shrike_Attack", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("wolf_Strike", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("wolf_Strike", FleetMemberType.SHIP, tritachyonBetrayalFleet);
                CampaignUtils.addShipToFleet("wolf_PD", FleetMemberType.SHIP, tritachyonBetrayalFleet);

                tritachyonBetrayalFleet.setCommander(tritachyonCommander);
                tritachyonBetrayalFleet.getFlagship().setCaptain(tritachyonCommander);
                tritachyonBetrayalFleet.setLocation(LazarusSystem.GetBetrayalSpawnLoc().getLocation().x, LazarusSystem.GetBetrayalSpawnLoc().getLocation().y);
                tritachyonBetrayalFleet.addAssignment(FleetAssignment.INTERCEPT, Global.getSector().getPlayerFleet(), 1000000f);
                tritachyonBetrayalFleet.setNoFactionInName(true);
                tritachyonBetrayalFleet.forceSync();
                tritachyonBetrayalFleet.getFleetData().setSyncNeeded();
                tritachyonBetrayalFleet.getFleetData().syncIfNeeded();
                tritachyonBetrayalFleet.setTransponderOn(true);

                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set("$riot_tritachfleetbetrayal", true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
                tritachyonBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN, 
                    new IsolatedBattleFleetInteractionConfigGen());
                system.addEntity(tritachyonBetrayalFleet);

                //Despawn the original fleet
                tritachyonFleet.getMemoryWithoutUpdate().unset("$riot_donotkill");
                tritachyonFleet.despawn();
            }

            if(winningFleet == luddicpathFleet){

                //Despawn the original fleet
                luddicpathFleet.getMemoryWithoutUpdate().unset("$riot_donotkill");
                luddicpathFleet.despawn();

                luddicpathBetrayalFleet = Global.getFactory().createEmptyFleet(Factions.LUDDIC_PATH, "Lazarus Luddic Path Fleet", true);
                CampaignUtils.addShipToFleet("prometheus2_Standard", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("venture_pather_Attack", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("colossus2_Pather", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("manticore_luddic_path_Strike", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("manticore_luddic_path_Strike", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("hammerhead_Balanced", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("hammerhead_Elite", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("enforcer_Overdriven", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("kite_luddic_path_Raider", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("kite_luddic_path_Raider", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("kite_luddic_path_Raider", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("kite_luddic_path_Raider", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("hound_luddic_path_Attack", FleetMemberType.SHIP, luddicpathBetrayalFleet);
                CampaignUtils.addShipToFleet("hound_luddic_path_Attack", FleetMemberType.SHIP, luddicpathBetrayalFleet);

                luddicpathBetrayalFleet.setCommander(luddicpathCommander);
                luddicpathBetrayalFleet.getFlagship().setCaptain(luddicpathCommander);
                luddicpathBetrayalFleet.setLocation(LazarusSystem.GetBetrayalSpawnLoc().getLocation().x, LazarusSystem.GetBetrayalSpawnLoc().getLocation().y);
                luddicpathBetrayalFleet.addAssignment(FleetAssignment.INTERCEPT, Global.getSector().getPlayerFleet(), 1000000f);
                luddicpathBetrayalFleet.setNoFactionInName(true);
                luddicpathBetrayalFleet.forceSync();
                luddicpathBetrayalFleet.getFleetData().setSyncNeeded();
                luddicpathBetrayalFleet.getFleetData().syncIfNeeded();
                luddicpathBetrayalFleet.setTransponderOn(true);

                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set("$riot_luddicpathfleetbetrayal", true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
                luddicpathBetrayalFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN, 
                    new IsolatedBattleFleetInteractionConfigGen());
                system.addEntity(luddicpathBetrayalFleet);

                //Despawn the original fleet
                luddicpathFleet.despawn();
            }
        }

    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
    {
        if (action.equals("helpTritach"))
        {
            luddicpathFleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE);
            luddicpathFleet.getMemoryWithoutUpdate().unset("$riot_donotkill");
            luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
            tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
            StartBattle(luddicpathFleet, tritachyonFleet, dialog);
            
            return true;
        }
        if (action.equals("helpLuddic"))
        {
            tritachyonFleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE);
            tritachyonFleet.getMemoryWithoutUpdate().unset("$riot_donotkill");
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
            winningFleet.getMemoryWithoutUpdate().unset(MemFlags.ENTITY_MISSION_IMPORTANT);
            return true;
        }
        if (action.equals("fightOrFlight"))
        {
            getPerson().getMemoryWithoutUpdate().set("$riot_leaveorfight", true);
            return true;
        }
        return false;
    }

    public void StartBattle(CampaignFleetAPI targetFleet, CampaignFleetAPI helpingFleet, InteractionDialogAPI dialog)
    {
        getPerson().getMemoryWithoutUpdate().set("$riot_allySelected", true);
        helpingFleet.getMemoryWithoutUpdate().set("$riot_allySelected", true);

        luddicpathFleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_FIGHT_TO_THE_LAST, true);
        luddicpathFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN, 
            new IsolatedBattleFleetInteractionConfigGen());
        
        tritachyonFleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_FIGHT_TO_THE_LAST, true);
        tritachyonFleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN, 
            new IsolatedBattleFleetInteractionConfigGen());
        
        tritachyonFleet.addEventListener(this);
        
        dialog.dismissAsCancel();
        Global.getSector().getPlayerFleet().getBattle().leave(Global.getSector().getPlayerFleet(), false);
        helpingFleet.getBattle().leave(helpingFleet, false);
        Global.getFactory().createBattle(helpingFleet, targetFleet);
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
        set("$riot_planetName", system.getEntityById("yurei").getName());
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle)
    {
        Global.getLogger(this.getClass()).info("Fleet Listener found battle! Battle is " + (battle.isDone() ? "done. " : "not done. ")
        + "Result is " + result + ". Tritachyon fleet was " + (battle.isInvolved(tritachyonFleet) ? "involved. " : "not involved. ")
        + "Fleet is " + fleet + ". PrimaryWinner is " + primaryWinner + ".");

        
        if((tritachyonFleet.getMemoryWithoutUpdate().contains("riot_donotkill") && !tritachyonFleet.isAlive()) || (luddicpathFleet.getMemoryWithoutUpdate().contains("riot_donotkill") && !luddicpathFleet.isAlive()))
            getPerson().getMemoryWithoutUpdate().set("$riot_failed", true);

        if (fleet != null && currentStage == Stage.JOIN_BATTLE)
        {
            if (primaryWinner.equals(tritachyonFleet))
            {
                winningFleet = tritachyonFleet;
                getPerson().getMemoryWithoutUpdate().set("$riot_afteraction", true);
                tritachyonFleet.getMemoryWithoutUpdate().set("$riot_tritachpostbattle", true);
                tritachyonFleet.getMemoryWithoutUpdate().unset("$riot_tritachfleet");
                Global.getLogger(this.getClass()).info("Tritachyon fleet wins!");
            }
            else if (primaryWinner.equals(luddicpathFleet))
            {
                winningFleet = luddicpathFleet;
                getPerson().getMemoryWithoutUpdate().set("$riot_afteraction", true);
                luddicpathFleet.getMemoryWithoutUpdate().set("$riot_luddicpathpostbattle", true);
                luddicpathFleet.getMemoryWithoutUpdate().unset("$riot_luddicpathfleet");
                Global.getLogger(this.getClass()).info("Luddic Path fleet wins!");
            }
        }
        else if (currentStage == Stage.LEAVE_OR_FIGHT || currentStage == Stage.DEFEND_SELF)
        {
            if (battle.isInvolved(tritachyonBetrayalFleet) || battle.isInvolved(luddicpathBetrayalFleet))
            {
                getPerson().getMemoryWithoutUpdate().set("$riot_completed", true);
            }
            else
            {
                Global.getLogger(this.getClass()).info("Was one of the betrayal fleets involved? Tritach betray: " + (battle.isInvolved(tritachyonBetrayalFleet) ? "yes." : "no.") + "/nLuddic Betray: " + (battle.isInvolved(luddicpathBetrayalFleet) ? "yes." : "no.") + "/nWas the player the primary winner?" + (battle.isPlayerPrimary() ? "yes." : "no."));
            }
        }
        else
        {
            Global.getLogger(this.getClass()).info("Neither in the Join Battle nor Defend Self stage.");
        }
    }

    // if the fleet despawns for whatever reason, fail the mission
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) 
    {
        if (isDone() || result != null || fleet == null) return;

        if (fleet.getMemoryWithoutUpdate().contains("$riot_donotkill")) {
            getPerson().getMemoryWithoutUpdate().set("$riot_failed", true);
        }
    }

    // COLONY PLAYER HOSTILE ACT LISTENER FUNCTIONS

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, CargoAPI cargo) 
    {
        //We're not using this
        
    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData, Industry cargo) 
    {
        //TO-DO: Make it where player has to disrupt a specific location
        if(market.getId().equals("yurei_market"))
        {
            if(currentStage == Stage.RAID_PLANET)
            {
                getPerson().getMemoryWithoutUpdate().set("$riot_grabcore", true);
            }
        }
    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI arg0, MarketAPI arg1, TempData arg2) 
    {
        //We're not using this
        
    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI arg0, MarketAPI arg1, TempData arg2) 
    {
        //We're not using this
        
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
            info.addPara("Raid to disrupt Yurei for the AI", opad);
        } else if (currentStage == Stage.GRAB_CORE) {
            info.addPara("Find the AI core in the debris field", opad);
        } else if (currentStage == Stage.DEFEND_SELF) {
            info.addPara("Report back to the fleet commander with the AI.", opad);
        } else if (currentStage == Stage.LEAVE_OR_FIGHT) {
            info.addPara("Leave the system or destroy the zombie fleet.", opad);
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
            info.addPara("Raid to disrupt the planet Yurei for the AI core.", tc, pad);
            return true;
        } else if (currentStage == Stage.GRAB_CORE) {
            info.addPara("Find the Rogue AI Core in the debris field.", tc, pad);
            return true;
        } else if (currentStage == Stage.DEFEND_SELF) {
            info.addPara("Return to the fleet that you aided with the AI core.", tc, pad);
            return true;
        } else if (currentStage == Stage.LEAVE_OR_FIGHT) {
            info.addPara("Leave Lazarus or fight the zombie fleet", tc, pad);
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
        else if (currentStage == Stage.LEAVE_OR_FIGHT) 
            return getMapLocationFor(system.getCenter());
        return null;
    }

    // mission name
    @Override
    public String getBaseName() {
        return "Dead Man's Riot";
    }

    @Override
    public void reportExtraSalvageShown(SectorEntityToken entity) 
    {        
        if(currentStage == Stage.GRAB_CORE && entity.isInOrNearSystem(system))
            getPerson().getMemoryWithoutUpdate().set("$riot_defendself", true);
    }

    @Override
    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI current) 
    {
        if(currentStage == Stage.LEAVE_OR_FIGHT)
        {
            if(prev.getId().equals("lazarus") && !current.getId().equals("lazarus"))
            {
                getPerson().getMemoryWithoutUpdate().set("$riot_completed", true);
            }
        }
    }

    
}