package scripts.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.ids.FleetTypes.PATROL_MEDIUM;

public class Riot extends HubMissionWithBarEvent implements FleetEventListener 
{

    // time we have to complete the mission
    public static float MISSION_DAYS = 360f;

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
    protected SectorEntityToken derelict;
    protected SectorEntityToken cache;
    protected CampaignFleetAPI target;
    protected PersonAPI executive;
    protected StarSystemAPI system;
    protected StarSystemAPI system2;

    // Mission only spawns in Tri-Tach bars
    public boolean shouldShowAtMarket(MarketAPI market) {
        return market.getFactionId().equals(Factions.TRITACHYON);
    }

    // run when the bar event starts / when we ask a contact about the mission
    protected boolean create(MarketAPI createdAt, boolean barEvent) {


        setGiverRank(Ranks.AGENT);
        setGiverPost(Ranks.POST_EXECUTIVE);
        setGiverImportance(PersonImportance.HIGH);
        setGiverFaction(Factions.TRITACHYON);
        setGiverTags(Tags.CONTACT_UNDERWORLD);
        setGiverVoice(Voices.BUSINESS);
        findOrCreateGiver(createdAt, false, false);
        

        PersonAPI person = getPerson();
        if (person == null) return false;

        // setting the mission ref allows us to use the Call rulecommand in their dialogues, so that we can make this script do things
        if (!setPersonMissionRef(person, "$riot_ref")) {
            return false;
        }


        setGiverIsPotentialContactOnSuccess(1f);

        // set up the disgraced executive
        executive = Global.getSector().getFaction(Factions.TRITACHYON).createRandomPerson();
        executive.setRankId(Ranks.SPACE_ADMIRAL);
        executive.setPostId(Ranks.POST_SENIOR_EXECUTIVE);
        executive.getMemoryWithoutUpdate().set("$riot_exec", true);

        // pick the system with the clues inside
        requireSystemIs(Global.getSector().getStarSystem("lazarus"));

        system = pickSystem(true);
        if (system == null) return false;

        // pick the target fleet's system
        requireSystemInterestingAndNotUnsafeOrCore();
        preferSystemWithinRangeOf(system.getLocation(), 3f);
        preferSystemUnexplored();
        requireSystemNot(system);

        system2 = pickSystem(true);
        if (system2 == null) return false;

        // determine the faction and ship type of the derelict
        String derelict_faction = Factions.TRITACHYON;
        DerelictType derelict_type = DerelictType.CIVILIAN;

        // spawn a supply cache and derelict ship, both serving as clues. They have memory flags that are checked for in rules.csv
        cache = spawnEntity(Entities.SUPPLY_CACHE, new LocData(EntityLocationType.HIDDEN, null, system));
        derelict = spawnDerelict(derelict_faction, derelict_type, new LocData(EntityLocationType.HIDDEN, null, system));
        cache.getMemoryWithoutUpdate().set("$riot_clue", true);
        setEntityMissionRef(cache, "$riot_ref");
        derelict.getMemoryWithoutUpdate().set("$riot_clue", true);
        setEntityMissionRef(derelict, "$riot_ref");

        // set up the target fleet. I've done this using the old style, because the trigger-system doesn't support event listeners by default,
        // and we need to know when this fleet dies or despawns
        FleetParamsV3 params = new FleetParamsV3(
                null,
                null,
                Factions.TRITACHYON,
                null,
                PATROL_MEDIUM,
                50f, // combatPts
                10f, // freighterPts
                10f, // tankerPts
                0f, // transportPts
                0f, // linerPts
                0f, // utilityPts
                -0.25f // qualityMod
        );
        // toughen them up, in exchange for the shoddy ship quality we set
        params.averageSMods = 2;
        target = FleetFactoryV3.createFleet(params);

        target.setName(executive.getNameString() + "'s Fleet");
        target.setNoFactionInName(true);

        target.setCommander(executive);
        target.getFlagship().setCaptain(executive);

        Misc.makeHostile(target);
        Misc.makeNoRepImpact(target, "$riot");
        Misc.makeImportant(target, "$riot");

        target.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, "$riot");
        target.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY, "$riot");
        target.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, "$riot");
        target.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, "$riot");

        target.getMemoryWithoutUpdate().set("$riot_execfleet", true);
        target.getAI().addAssignment(FleetAssignment.PATROL_SYSTEM, system2.getCenter(), 200f, null);
        target.addEventListener(this);
        system2.addEntity(target);

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

    // when Call-ing something that isn't a default option for a mission, it'll try and run this method with "action" being the first parameter
    // e.g Call $global.riot_ref unsetClues
    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (action.equals("unsetClues")){
            // make the other clue no longer a clue
            if (cache != null) {
                cache.getMemoryWithoutUpdate().unset("$riot_clue");
            }
            if (derelict != null) {
                derelict.getMemoryWithoutUpdate().unset("$riot_clue");
            }
            return true;
        }
        return false;
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

        if (battle.isInvolved(fleet) && !playerInvolved) {
            if (fleet.getFlagship() == null || fleet.getFlagship().getCaptain() != target) {
                fleet.setCommander(fleet.getFaction().createRandomPerson());
                getPerson().getMemoryWithoutUpdate().set("$riot_completed", true);
                return;
            }
        }

        if (!playerInvolved || !battle.isInvolved(fleet) || battle.onPlayerSide(fleet)) {
            return;
        }

        // didn't destroy the original flagship
        if (fleet.getFlagship() != null && fleet.getFlagship().getCaptain() == target) return;

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
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (currentStage == Stage.REACH_SYSTEM) {
            return getMapLocationFor(system.getCenter());
        } else if (currentStage == Stage.JOIN_BATTLE) {
            return getMapLocationFor(system2.getCenter());
        }
        return null;
    }

    // mission name
    @Override
    public String getBaseName() {
        return "Dead Man's Riot";
    }
}