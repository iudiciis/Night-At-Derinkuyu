package data.scripts.campaign.missions;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class NaderinFindingTheLuminary extends HubMissionWithBarEvent {

    public static enum Stage {
        CONTACT,
        SEARCHING_SALVAGE,
        RESUPPLY_INTERCEPT,
        VISITING_THE_STARWORKS,
        PAYMENT,
        COMPLETED,
        FAILED,
    }

    protected SectorEntityToken firstDebrisField;
    protected SectorEntityToken secondDebrisField;
    protected SectorEntityToken supplyCache;
    protected StarSystemAPI salvageSystem;
    protected StarSystemAPI pirateSystem;
    protected PersonAPI giver;
    protected MarketAPI starworks;
    protected FleetMemberAPI fleetMember;
    protected int reward = 100000;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        // don't create if already in progress
        if (!setGlobalReference("$naderin_ftl_ref", "$naderin_ftl_inProgress")) {
            return false;
        }

        // prevent appearing twice
        // (alternative is a big timeout in bar_events.csv)
        boolean completed = Global.getSector().getMemoryWithoutUpdate().getBoolean("$naderin_ftl_completed");
        if (completed) {
            return false;
        }

        if (barEvent) {
            setGiverRank(Ranks.CITIZEN);
            setGiverPost(pickOne(Ranks.POST_SPACER, Ranks.POST_FLEET_COMMANDER));
            setGiverImportance(PersonImportance.LOW);
            setGiverFaction(Factions.INDEPENDENT);
            findOrCreateGiver(createdAt, true, false);
        }

        // Giver
        PersonAPI giver = getPerson();
        if (giver == null) return false;
        makeImportant(giver, "$naderin_ftl_giver", Stage.PAYMENT, Stage.COMPLETED);
        // giver.addTag("$naderin_luminary_guy");

        starworks = Global.getSector().getEconomy().getMarket("station_kapteyn");
        if (starworks == null) return false;
        if (!starworks.getFactionId().equals(Factions.PIRATES)) return false;
        makeImportant(starworks, "$naderin_ftl_starworks", Stage.VISITING_THE_STARWORKS);

        requireSystemNot(createdAt.getStarSystem());
        requireSystemInterestingAndNotUnsafeOrCore();
        preferSystemInInnerSector();
        preferSystemUnexplored();
        preferSystemInDirectionOfOtherMissions();
        salvageSystem = pickSystem(true);
        if (salvageSystem == null) return false;

        requireSystemNot(createdAt.getStarSystem());
        requireSystemNot(salvageSystem);
        requireSystemInterestingAndNotUnsafeOrCore();
        preferSystemUnexplored();
        pirateSystem = pickSystem(true);
        if (pirateSystem == null) return false;

        // Ship - from hand-me-down
        ShipVariantAPI variant = Global.getSettings().getVariant("apogee_Balanced").clone();

        int dMods = 2 + genRandom.nextInt(3);
        DModManager.addDMods(variant, true, dMods, genRandom);
        DModManager.removeDMod(variant, HullMods.COMP_STORAGE);

        fleetMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        fleetMember.setShipName("ISS The Luminary");

        fleetMember.getCrewComposition().setCrew(100000);
        fleetMember.getRepairTracker().setCR(0.7f);

        // Debris Fields
        beginStageTrigger(Stage.SEARCHING_SALVAGE);
        LocData firstDebrisLoc = new LocData(EntityLocationType.ORBITING_PLANET_OR_STAR, null, salvageSystem);
        triggerSpawnDebrisField(10f, 1f, firstDebrisLoc);
        triggerEntityMakeImportant("$naderin_ftl_fdf", Stage.SEARCHING_SALVAGE);
        endTrigger();

        beginStageTrigger(Stage.SEARCHING_SALVAGE);
        LocData secondDebrisLoc = new LocData(EntityLocationType.HIDDEN_NOT_NEAR_STAR, null, salvageSystem);
        triggerSpawnDebrisField(15f, 2f, secondDebrisLoc);
        triggerEntityMakeImportant("$naderin_ftl_sdf", Stage.SEARCHING_SALVAGE);
        endTrigger();

        // Pirate Fleet and Cache
        beginStageTrigger(Stage.RESUPPLY_INTERCEPT);
        LocData supplyCacheLoc = new LocData(EntityLocationType.HIDDEN_NOT_NEAR_STAR, null, pirateSystem);
        triggerSpawnEntity(Entities.SUPPLY_CACHE, supplyCacheLoc);
        triggerEntityMakeImportant("$naderin_ftl_cache", Stage.RESUPPLY_INTERCEPT);

        triggerCreateFleet(FleetSize.SMALL, FleetQuality.DEFAULT, Factions.PIRATES, FleetTypes.PATROL_SMALL, pirateSystem);
        triggerAutoAdjustFleetStrengthMajor();
        triggerSetStandardHostilePirateFlags();
        triggerMakeFleetIgnoredByOtherFleets();
        triggerPickLocationAtInSystemJumpPoint(pirateSystem);
        triggerSpawnFleetAtPickedLocation("$naderin_ftl_pirates", null);
        triggerOrderFleetPatrolEntity(true);
        triggerSetFleetMissionRef("$naderin_ftl_ref");
        endTrigger();

        setName("Finding The Luminary");
        setStoryMission();
        setStartingStage(Stage.CONTACT);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);

        // could have used only memory flags if we had the cache/debris entity variables
        // ...could we get it while keeping spawns trigger based?
        setStageOnMemoryFlag(Stage.SEARCHING_SALVAGE, giver, "$naderin_ftl_given");
        setStageOnGlobalFlag(Stage.RESUPPLY_INTERCEPT, "$naderin_ftl_searched");
        setStageOnGlobalFlag(Stage.VISITING_THE_STARWORKS, "$naderin_ftl_located");
        setStageOnMemoryFlag(Stage.PAYMENT, starworks, "$naderin_ftl_acquired");
        setStageOnMemoryFlag(Stage.COMPLETED, giver, "$naderin_ftl_returned");

        return true;
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (action.equals("showShip")) {
            dialog.getVisualPanel().showFleetMemberInfo(fleetMember, true);
            return true;
        } else if (action.equals("showGiver")) {
            dialog.getVisualPanel().showPersonInfo(giver, true);
            return true;
        } else if (action.equals("viewMap")) {     // Not to be confused with the vanilla showMap
            SectorEntityToken mapLoc = getMapLocationFor(salvageSystem.getCenter());
            if (mapLoc != null) {
                String title = params.get(1).getStringWithTokenReplacement(ruleId, dialog, memoryMap);
                String text = "";
                Set<String> tags = getIntelTags(null);
                tags.remove(Tags.INTEL_ACCEPTED);
                String icon = getIcon();

                Color color = getFactionForUIColors().getBaseUIColor();
                if (mapMarkerNameColor != null) {
                    color = mapMarkerNameColor;
                }

                dialog.getVisualPanel().showMapMarker(mapLoc,
                        title, color,
                        true, icon, text, tags);
            }
            return true;
        }
        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$naderin_ftl_personName", getPerson().getNameString());
        set("$naderin_ftl_heOrShe", getPerson().getHeOrShe());
        set("$naderin_ftl_himOrHer", getPerson().getHimOrHer());
        set("$naderin_ftl_hisOrHer", getPerson().getHisOrHer());
        set("$naderin_ftl_luminary", fleetMember);
        set("$naderin_ftl_systemName", salvageSystem.getNameWithLowercaseTypeShort());
        set("$naderin_ftl_dist", getDistanceLY(salvageSystem));
        // Note: $naderin_ftl_blurbBar and $naderin_ftl_optionBar is defined elsewhere
        // Ensure person_missions.csv and/or bar_events.csv has the right id
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        if (currentStage == Stage.SEARCHING_SALVAGE) {
            info.addPara(getGoToSystemTextShort(salvageSystem) + " and investigate the system for any signs of The Luminary.", opad);
        }
        if (currentStage == Stage.RESUPPLY_INTERCEPT) {
            info.addPara(getGoToSystemTextShort(pirateSystem) + " and search for the pirates that possibly have The Luminary, or at least find out what they did to it.", opad);
        }
        if (currentStage == Stage.VISITING_THE_STARWORKS) {
            info.addPara(getGoToMarketText(starworks) + " and recover The Luminary if at all possible.", opad);
            addStandardMarketDesc(null, starworks, info, opad);
        }
        if (currentStage == Stage.PAYMENT) {
            if(Global.getSector().getMemoryWithoutUpdate().contains("$naderin_ftl_keep")) {
                info.addPara("It's time to return what you've 'found' of The Luminary to " + giver.getNameString() + ".", opad);
                info.addPara("And get paid for it too, of course.", opad);
                addStandardMarketDesc("Go to ", giver.getMarket(), info, opad);
            }
            info.addPara("It's time to return what you've found of The Luminary to " + giver.getNameString() + ".", opad);
            info.addPara("And get paid for it too, of course.", opad);
            addStandardMarketDesc("Go to ", giver.getMarket(), info, opad);
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == Stage.SEARCHING_SALVAGE) {
            info.addPara("Search for The Luminary in the " + salvageSystem.getName() + " system", tc, pad);
            return true;
        } else if (currentStage == Stage.RESUPPLY_INTERCEPT) {
            info.addPara("See if the pirate fleet in the " + pirateSystem.getName() + " system knows anything about The Luminary's whereabouts", tc, pad);
            return true;
        } else if (currentStage == Stage.VISITING_THE_STARWORKS) {
            info.addPara("See if you can recover The Luminary from the pirates at Kapteyn Starworks", tc, pad);
            return true;
        } else if (currentStage == Stage.PAYMENT) {
            info.addPara("Get paid by " + giver.getNameString() + " at " + giver.getMarket() + ".", tc, pad);
        }

        return false;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (currentStage == Stage.SEARCHING_SALVAGE) {
            return salvageSystem.getCenter();
        } else if (currentStage == Stage.RESUPPLY_INTERCEPT) {
            return pirateSystem.getCenter();
        } else if (currentStage == Stage.VISITING_THE_STARWORKS) {
            return starworks.getPlanetEntity();
        } else if (currentStage == Stage.PAYMENT) {
            return giver.getMarket().getPlanetEntity();
        } else {
            return super.getMapLocation(map);
        }
    }

    //    @Override
    //    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
    //          // blurb and option stuff
    //    }

    @Override
    public String getBaseName() {
        return "Finding The Luminary";
    }
}
