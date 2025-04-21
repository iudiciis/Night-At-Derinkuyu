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
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.ids.NaderinPeople;

public class NaderinTheDerinkuyuDrop extends HubMissionWithSearch {
    // See com\fs\starfarer\api\impl\campaign\missions\DeadDropMission.java

    public static enum Stage {
        CONTACT,
        DROP_OFF,
        SUBMIT,
        COMPLETED,
        FAILED,
    }

    protected Integer reward = 75000;
    protected String thing = "a shielded crate";
    protected SectorEntityToken target;
    protected StarSystemAPI system;
    protected PersonAPI giver; // not really the giver anymore, but still refers to the person paying at the end

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        requireSystemNot(createdAt.getStarSystem());
        requireSystemInterestingAndNotUnsafeOrCore();
		preferSystemInInnerSector();
		preferSystemUnexplored();
		preferSystemInDirectionOfOtherMissions();
        system = pickSystem();
        if (system == null) return false;
        
        target = spawnMissionNode(new LocData(EntityLocationType.HIDDEN_NOT_NEAR_STAR, null, system));
        if (!setEntityMissionRef(target, "$naderin_tdd_ref")) return false;
        target.setName("Drop Point");
        // target.setCustomDescriptionId("Drop site.");

        giver = getImportantPerson(NaderinPeople.REMY);
        if (giver == null) return false;
        if (!setPersonMissionRef(giver, "$naderin_tdd_ref")) return false;


        MarketAPI derinkuyu = getMarket("derinkuyu_market");
        if (derinkuyu == null) return false;
        derinkuyu.getCommDirectory().addPerson(giver);
        derinkuyu.addPerson(giver);
        
        makeImportant(target, "$naderin_tdd_target", Stage.DROP_OFF);
        makeImportant(giver, "$naderin_tdd_giver", Stage.CONTACT, Stage.SUBMIT);
        
        setName("The Derinkuyu Drop");
        setStoryMission();
        setStartingStage(Stage.CONTACT);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);

        setStageOnMemoryFlag(Stage.DROP_OFF, giver, "$naderin_tdd_drop");
        setStageOnMemoryFlag(Stage.SUBMIT, target, "$naderin_tdd_submit");
        setStageOnMemoryFlag(Stage.COMPLETED, giver, "$naderin_tdd_completed");
        setCreditReward(reward);
        setRepRewardPerson(0f);
        setRepRewardFaction(0f);

        // See HubMissionWithTriggers.java
        // Pirate complication
        beginWithinHyperspaceRangeTrigger(system, 5f, true, Stage.DROP_OFF);
        triggerCreateFleet(FleetSize.SMALL, FleetQuality.DEFAULT, Factions.PIRATES, FleetTypes.RAIDER, system);
        triggerAutoAdjustFleetSize(FleetSize.SMALL, FleetSize.LARGE);
        triggerPickLocationTowardsPlayer(system.getHyperspaceAnchor(), 10f, getUnits(0.3f));
        triggerSpawnFleetAtPickedLocation("$naderin_tdd_pirates_ref", null);
        triggerSetFleetMissionRef("$naderin_tdd_ref");
        triggerMakeFleetGoAwayAfterDefeat();
        triggerSetPirateFleet(); // important, pirate fleet behaves differently otherwise when transponder is off
        // triggerSetPatrol();
        triggerOrderFleetPatrolHyper(system);
        endTrigger();

        // Mercenary complication
        beginStageTrigger(Stage.SUBMIT);
        triggerCreateFleet(FleetSize.MEDIUM, FleetQuality.HIGHER, Factions.INDEPENDENT, FleetTypes.MERC_PRIVATEER, system);
        triggerPickLocationAtClosestToPlayerJumpPoint(system);
        triggerSpawnFleetAtPickedLocation("$naderin_tdd_merc_ref", null);
        triggerSetFleetMissionRef("$naderin_tdd_ref");
        triggerMakeNoRepImpact();
        triggerMakeNonHostile();
        triggerMakeFleetIgnoreOtherFleets();
        triggerMakeFleetGoAwayAfterDefeat();
        triggerOrderFleetInterceptPlayer();
        triggerFleetMakeImportant(null, Stage.SUBMIT);
        triggerFleetSetName("Grey Rock Privateers");
        endTrigger();

        return true;
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (action.equals("viewMap")) {     // Not to be confused with the vanilla showMap
            SectorEntityToken mapLoc = getMapLocationFor(system.getCenter());
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
        } else {
            return super.callAction(action, ruleId, dialog, params, memoryMap);
        }
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$naderin_tdd_reward", reward);
        set("$naderin_tdd_aOrAnThing", thing);                  // unused
        set("$naderin_tdd_thing", getWithoutArticle(thing));    // unused
        set("$naderin_tdd_personName", giver.getNameString());
        set("$naderin_tdd_systemName", system.getNameWithLowercaseTypeShort());
        // set("$naderin_tdd_dist", getDistanceLY(target));     // getDistanceLY relies on the quest originator
        int dist = 0;
        if (giver.getMarket() != null) {
            dist = (int) Math.round(Misc.getDistanceLY(giver.getMarket().getLocationInHyperspace(), target.getLocationInHyperspace()));
        }
        set("$naderin_tdd_dist", dist);
	}

    @Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
        if (currentStage == Stage.CONTACT) {
            info.addPara("Not knowing the location of the drop themselves, you were told to ask Remy about where to deliver the package.", opad);
        }
		if (currentStage == Stage.DROP_OFF) {
			info.addPara("Now knowing the location of the drop, the next step is to deliver the package to the specified coordinates in the " +
                    system.getNameWithLowercaseTypeShort() + ".", opad);
		}
        if (currentStage == Stage.SUBMIT) {
            info.addPara("Technically having made the drop, you now must return to Remy to receive payment.", opad);
        }
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == Stage.CONTACT) {
            info.addPara("Ask Remy at Derinkuyu Station about where to deliver the package", tc, pad);
            return true;
        }
		if (currentStage == Stage.DROP_OFF) {
			info.addPara("Deliver the package to the specified location in the " +
					system.getNameWithLowercaseTypeShort(), tc, pad);
			return true;
		}
        if (currentStage == Stage.SUBMIT) {
            info.addPara("Return to Remy for your reward", tc, pad);
            return true;
        }
		return false;
	}

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (currentStage == Stage.CONTACT || currentStage == Stage.SUBMIT) {
            return getMapLocationFor(Global.getSector().getStarSystem("galatia").getEntityById("derinkuyu_station"));
        }
        if (currentStage == Stage.DROP_OFF) {
            return getMapLocationFor(system.getCenter());
        }
        return null;
    }
	
	@Override
	public String getBaseName() {
		return "The Derinkuyu Drop";
	}
}