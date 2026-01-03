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
import com.fs.starfarer.api.impl.campaign.missions.DelayedFleetEncounter;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddShip;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/**
 * A simple fetch quest with a classic dose of space violence.
 * @author iudiciis
 */
public class NaderinFindingTheLuminary extends HubMissionWithBarEvent {

    // Use stages to track how far the Player has progressed, and to react accordingly.
    public enum Stage {
        SEARCHING_SALVAGE,
        SECOND_SALVAGE,
        RESUPPLY_INTERCEPT,
        VISITING_THE_STARWORKS,
        PAYMENT,
        COMPLETED,
        FAILED,
        FAILED_NO_PENALTY,
    }

    // protected SectorEntityToken firstDebrisField;
    protected SectorEntityToken secondDebrisField;
    // protected SectorEntityToken supplyCache;
    protected StarSystemAPI salvageSystem;
    protected StarSystemAPI pirateSystem;
    protected PersonAPI giver;
    protected MarketAPI starworks;
    protected ShipVariantAPI variant;
    protected FleetMemberAPI fleetMember;
    // protected int reward = 150000;
    protected float raidDifficulty = 150f;

    // Apparently more for organisation - can go into the create function instead.
    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return !market.getFactionId().equals(Factions.PIRATES);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        // don't create if already in progress
        if (!setGlobalReference("$naderin_ftl_ref", "$naderin_ftl_inProgress")) {
            return false;
        }

        // prevent appearing twice. remember to set the flag in rules.csv!
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
            createGiver(createdAt, true, false);
        } else {
            return false; // ensure it is only ever a bar event (the csv files do that anyway, though)
        }

        // Giver
        giver = getPerson();
        if (giver == null) return false;
        makeImportant(giver, "$naderin_ftl_giver", Stage.PAYMENT, Stage.COMPLETED);

        // Starworks setup
        starworks = Global.getSector().getEconomy().getMarket("station_kapteyn");
        if (starworks == null) return false;
        if (!starworks.getFactionId().equals(Factions.PIRATES)) return false;
        makeImportant(starworks, "$naderin_ftl_starworks", Stage.VISITING_THE_STARWORKS);

        // Systems
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
        preferSystemWithinRangeOf(salvageSystem.getLocation(), 2f, 10f);
        pirateSystem = pickSystem(true);
        if (pirateSystem == null) return false;

        // Ship - see hand-me-down code in vanilla
        variant = Global.getSettings().getVariant("apogee_Balanced").clone();
        variant.clear();

        fleetMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        fleetMember.setShipName("ISS The Luminary");
        fleetMember.getCrewComposition().setCrew(100000);
        fleetMember.getRepairTracker().setCR(0.3f);

        int dMods = 4 + genRandom.nextInt(2);
        DModManager.setDHull(variant);
        DModManager.addDMods(fleetMember, true, dMods, genRandom);

        // Debris Fields
        beginStageTrigger(Stage.SEARCHING_SALVAGE);
        LocData firstDebrisLoc = new LocData(EntityLocationType.ORBITING_PLANET_OR_STAR, null, salvageSystem);
        triggerSpawnDebrisField(400f, 1f, firstDebrisLoc);
        triggerEntityMakeImportant("$naderin_ftl_fdf", Stage.SEARCHING_SALVAGE, Stage.SECOND_SALVAGE);
        endTrigger();

        // probably better to instantiate now rather than on trigger
        LocData secondDebrisLoc = new LocData(EntityLocationType.HIDDEN_NOT_NEAR_STAR, null, salvageSystem);
        secondDebrisField = spawnDebrisField(100f, 2f, secondDebrisLoc);
        makeImportant(secondDebrisField, "$naderin_ftl_sdf", Stage.SEARCHING_SALVAGE, Stage.SECOND_SALVAGE);

        // surely there's an easier way to instantly reveal an entity's location to the player
        beginStageTrigger(Stage.SECOND_SALVAGE);
        // triggerMakeDiscoverable, maybe
        triggerCustomAction(context -> {
            // I have no idea which ones we actually need and which ones we can get rid of
            secondDebrisField.getDetectedRangeMod().modifyFlat("gen", 10f);
            secondDebrisField.setExtendedDetectedAtRange(20000f);
            secondDebrisField.setDetectionRangeDetailsOverrideMult(10f);
            secondDebrisField.setSensorProfile(20000f);
            // context.entity = secondDebrisField;
        });
        endTrigger();

        // Pirate Fleet Complication and Cache
        beginStageTrigger(Stage.RESUPPLY_INTERCEPT);
        LocData supplyCacheLoc = new LocData(EntityLocationType.HIDDEN_NOT_NEAR_STAR, null, pirateSystem);
        triggerSpawnEntity(Entities.SUPPLY_CACHE, supplyCacheLoc);
        triggerEntityMakeImportant("$naderin_ftl_cache", Stage.RESUPPLY_INTERCEPT, Stage.VISITING_THE_STARWORKS);
        triggerSetEntityFlag("$naderin_ftl_hasLocation", Stage.RESUPPLY_INTERCEPT);

        triggerCreateFleet(FleetSize.MEDIUM, FleetQuality.DEFAULT, Factions.PIRATES, FleetTypes.PATROL_MEDIUM, pirateSystem);
        triggerAutoAdjustFleetStrengthModerate();
        triggerSetStandardHostilePirateFlags();
        //triggerMakeFleetIgnoredByOtherFleets();
        triggerMakeFleetIgnoreOtherFleetsExceptPlayer();
        triggerPickLocationAtInSystemJumpPoint(pirateSystem);
        triggerSpawnFleetAtPickedLocation("$naderin_ftl_pirates", null);
        triggerOrderFleetPatrolEntity(true);
        triggerSetFleetMissionRef("$naderin_ftl_ref");
        triggerFleetMakeImportant("$naderin_ftl_pirate", Stage.RESUPPLY_INTERCEPT);
        endTrigger();

        // Starworks Complications
        triggerCreateMediumPatrolAroundMarket(starworks, Stage.VISITING_THE_STARWORKS, 0f);
        triggerCreateSmallPatrolAroundMarket(starworks, Stage.VISITING_THE_STARWORKS, 0f);

        List<SectorEntityToken> jumpPoints = starworks.getStarSystem().getJumpPoints();
        for (SectorEntityToken point : jumpPoints) {
            spawnVengeanceFleet(point);
        }

        beginGlobalFlagTrigger("$naderin_ftl_raidedForLuminary", Stage.VISITING_THE_STARWORKS, Stage.PAYMENT);
        triggerIncreaseMarketHostileTimeout(starworks, 15f);
        endTrigger();

        // Stages
        setName("Finding The Luminary");
        setStoryMission();
        setTimeLimit(Stage.FAILED, 365, null, Stage.RESUPPLY_INTERCEPT);
        setNoAbandon();

        setStartingStage(Stage.SEARCHING_SALVAGE);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);

        addNoPenaltyFailureStages(Stage.FAILED_NO_PENALTY);
        setStageOnMarketDecivilized(Stage.FAILED_NO_PENALTY, starworks);
        setStageOnMarketDecivilized(Stage.FAILED_NO_PENALTY, giver.getMarket());

        // Progression
        setStageOnGlobalFlag(Stage.SECOND_SALVAGE, "$naderin_ftl_calculated");
        setStageOnGlobalFlag(Stage.RESUPPLY_INTERCEPT, "$naderin_ftl_searched");
        setStageOnGlobalFlag(Stage.VISITING_THE_STARWORKS, "$naderin_ftl_located");
        setStageOnGlobalFlag(Stage.PAYMENT, "$naderin_ftl_acquired");
        setStageOnMemoryFlag(Stage.COMPLETED, getPerson(), "$naderin_ftl_returned");
        setStageOnMemoryFlag(Stage.COMPLETED, getPerson(), "$naderin_ftl_locationAdvised");
        setStageOnMemoryFlag(Stage.FAILED, getPerson(), "$naderin_ftl_keptTheLuminary");

        return true;
    }

    //
    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        switch (action) {
            case "showShip" -> {
                dialog.getVisualPanel().showFleetMemberInfo(fleetMember, true);
                return true;
            }
            case "showGiver" -> {
                dialog.getVisualPanel().showPersonInfo(getPerson(), true);
                return true;
            }
            case "getMapForDebrisPhase" -> {        // similar to the vanilla showMap
                String title = params.get(1).getStringWithTokenReplacement(ruleId, dialog, memoryMap);
                return tryGetMapVisual(salvageSystem.getCenter(), dialog, title);
            }
            case "getMapForSupplyPhase" -> {
                String title = params.get(1).getStringWithTokenReplacement(ruleId, dialog, memoryMap);
                return tryGetMapVisual(pirateSystem.getCenter(), dialog, title);
            }
            case "playSoundHitHeavy" -> {
                Global.getSoundPlayer().playSound("hit_heavy", 1f, 1f, Global.getSoundPlayer().getListenerPos(), new Vector2f());
                return true;
            }
            case "transferLuminary" -> {
                FleetMemberAPI luminary = null;
                for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    if (member.getId().equals(fleetMember.getId())) {
                        luminary = member;
                        break;
                    }
                }
                if (luminary != null) {
                    AddRemoveCommodity.addFleetMemberLossText(luminary, dialog.getTextPanel());
                    Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(luminary);
                }
                return true;
            }
            case "addLuminary" -> {
                Global.getSector().getPlayerFleet().getFleetData().addFleetMember(fleetMember);
                AddShip.addShipGainText(fleetMember, dialog.getTextPanel());
            }
            case "refusedToReturn" -> {
                DelayedFleetEncounter e = new DelayedFleetEncounter(genRandom, getMissionId());
                e.setDelayMedium();
                e.setLocationInnerSector(false, Factions.INDEPENDENT);
                e.beginCreate();
                e.triggerCreateFleet(FleetSize.MEDIUM, FleetQuality.VERY_HIGH, Factions.MERCENARY, FleetTypes.PATROL_LARGE, new Vector2f());
                e.triggerSetFleetOfficers(OfficerNum.MORE, OfficerQuality.HIGHER);
                e.triggerFleetSetFaction(Factions.INDEPENDENT);
                e.triggerSetFleetFlag("$naderin_ftl_consequences");
                e.triggerMakeNoRepImpact();
                e.triggerSetStandardAggroInterceptFlags();
                e.endCreate();
            }
        }
        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    // too lazy to find a vanilla cmd that works, so I guess this will have to do
    private boolean tryGetMapVisual(SectorEntityToken targetEntity, InteractionDialogAPI dialog, String title) {
        SectorEntityToken mapLoc = getMapLocationFor(targetEntity);
        if (mapLoc != null) {
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
            return true;
        }
        return false;
    }

    protected void spawnVengeanceFleet(SectorEntityToken spawnPoint) {
        beginGlobalFlagTrigger("$naderin_ftl_raidedForLuminary", Stage.VISITING_THE_STARWORKS, Stage.PAYMENT);
        triggerCreateFleet(FleetSize.MEDIUM, FleetQuality.DEFAULT, Factions.PIRATES, FleetTypes.RAIDER, starworks.getStarSystem());
        triggerSetFleetFaction(Factions.PIRATES);
        triggerAutoAdjustFleetStrengthModerate();
        triggerPickLocationAroundEntity(spawnPoint, 100f);
        triggerSpawnFleetAtPickedLocation("$naderin_ftl_vengeanceFleet", null);
        triggerSetStandardAggroPirateFlags();
        triggerFleetAllowLongPursuit();
        triggerOrderFleetInterceptPlayer();
        endTrigger();
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$naderin_ftl_personName", getPerson().getNameString());
        set("$naderin_ftl_heOrShe", getPerson().getHeOrShe());
        set("$naderin_ftl_himOrHer", getPerson().getHimOrHer());
        set("$naderin_ftl_hisOrHer", getPerson().getHisOrHer());
        set("$naderin_ftl_luminary", fleetMember);
        set("$naderin_ftl_raidDifficulty", raidDifficulty);
        set("$naderin_ftl_systemName", salvageSystem.getNameWithLowercaseTypeShort());
        set("$naderin_ftl_dist", getDistanceLY(salvageSystem));
        set("$naderin_ftl_supplySystemName", pirateSystem.getNameWithLowercaseTypeShort());
        // we want the distance between these two, not from the bar
        set("$naderin_ftl_supplySystemDist", Math.round(Misc.getDistanceLY(salvageSystem.getCenter(), pirateSystem.getCenter())));
        // Note: $naderin_ftl_blurbBar and $naderin_ftl_optionBar is defined elsewhere
        // Ensure person_missions.csv and/or bar_events.csv has the right id
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        FactionAPI f = starworks.getFaction();
        if (currentStage == Stage.SEARCHING_SALVAGE) {
            LabelAPI label;
            label = info.addPara(getGoToSystemTextShort(salvageSystem) + " and investigate the system for any clues to The Luminary.", opad);
            label.setHighlight("The Luminary");
            label.setHighlightColor(h);
        }
        if (currentStage == Stage.SECOND_SALVAGE) {
            LabelAPI label;
            label = info.addPara(getGoToSystemTextShort(salvageSystem) + " and investigate the system for further clues to The Luminary.", opad);
            label.setHighlight("The Luminary");
            label.setHighlightColor(h);
        }
        if (currentStage == Stage.RESUPPLY_INTERCEPT) {
            LabelAPI label;
            label = info.addPara(getGoToSystemTextShort(pirateSystem) + " and see if the pirates have The Luminary. Or at least, find out what they did to it.", opad);
            label.setHighlight("The Luminary");
            label.setHighlightColor(h);
        }
        if (currentStage == Stage.VISITING_THE_STARWORKS) {
            LabelAPI label;
            label = info.addPara(getGoToMarketText(starworks) + " and recover The Luminary if at all possible from the " +
                    f.getDisplayNameWithArticle() + ".", opad);
            label.setHighlight("The Luminary", starworks.getName(), f.getDisplayNameWithArticle());
            label.setHighlightColors(h, f.getBaseUIColor(), f.getBaseUIColor());
        }
        if (currentStage == Stage.PAYMENT) {
            LabelAPI label;
            if(Global.getSector().getMemoryWithoutUpdate().contains("$naderin_ftl_keep")) {
                label = info.addPara("It's time to return what you've 'found' of The Luminary to " + getPerson().getNameString() + ".", opad);
            } else {
                label = info.addPara("It's time to return what you've found of The Luminary to " + getPerson().getNameString() + ".", opad);
            }
            label.setHighlight("The Luminary");
            label.setHighlightColor(h);

            info.addPara("And get paid for it too, of course.", opad);
            addStandardMarketDesc("Go to", getPerson().getMarket(), info, opad);
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == Stage.SEARCHING_SALVAGE) {
            info.addPara("Search for clues to The Luminary in the " + salvageSystem.getName() + " system", tc, pad);
            return true;
        } else if (currentStage == Stage.SECOND_SALVAGE) {
            info.addPara("Search for more clues to The Luminary in the " + salvageSystem.getName() + " system", tc, pad);
            return true;
        } else if (currentStage == Stage.RESUPPLY_INTERCEPT) {
            info.addPara("See if the pirate fleet in the " + pirateSystem.getName() + " system knows anything about The Luminary's whereabouts", tc, pad);
            return true;
        } else if (currentStage == Stage.VISITING_THE_STARWORKS) {
            info.addPara("See if you can recover The Luminary from the pirates at " + starworks.getName(), tc, pad);
            return true;
        } else if (currentStage == Stage.PAYMENT) {
            info.addPara("Get paid by " + getPerson().getNameString() + " at " + getPerson().getMarket().getName(), tc, pad);
        }

        return false;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (currentStage == Stage.SEARCHING_SALVAGE) {
            return salvageSystem.getCenter();
        } else if (currentStage == Stage.SECOND_SALVAGE) {
            return secondDebrisField;
        } else if (currentStage == Stage.RESUPPLY_INTERCEPT) {
            return pirateSystem.getCenter();
        } else if (currentStage == Stage.VISITING_THE_STARWORKS) {
            return starworks.getStarSystem().getCenter();
        } else if (currentStage == Stage.PAYMENT) {
            return getPerson().getMarket().getPlanetEntity();
        } else {
            return super.getMapLocation(map);
        }
    }

    @Override
    public String getBaseName() {
        return "Finding The Luminary";
    }
}
