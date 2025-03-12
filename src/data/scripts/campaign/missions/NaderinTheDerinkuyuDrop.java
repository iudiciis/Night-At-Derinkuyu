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
        COMPLETED,
        FAILED,
    }

    protected Integer reward = 75000;
    protected String thing = "a shielded crate";
    protected SectorEntityToken target;
    protected StarSystemAPI system;
    protected PersonAPI giver;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        requireSystemNot(createdAt.getStarSystem());
		requireSystemInterestingAndNotCore();           // can be dangerous
		preferSystemInInnerSector();                    // preferably not too far
		preferSystemUnexplored();
		preferSystemInDirectionOfOtherMissions();
        system = pickSystem();
        if (system == null) return false;
        
        target = spawnMissionNode(new LocData(EntityLocationType.HIDDEN_NOT_NEAR_STAR, null, system));
        if (!setEntityMissionRef(target, "$naderin_tdd_ref")) return false;
        target.setName("Drop Point");

        giver = getImportantPerson(NaderinPeople.REMY);
        if (giver == null) return false;
        if (!setPersonMissionRef(giver, "$naderin_tdd_ref")) return false;

        createdAt.getCommDirectory().addPerson(giver);
        createdAt.addPerson(giver);
        
        makeImportant(target, "$naderin_tdd_target", Stage.DROP_OFF);
        
        setName("The Derinkuyu Drop");
        setStoryMission();
        setStartingStage(Stage.CONTACT);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);

        setStageOnMemoryFlag(Stage.DROP_OFF, giver, "$naderin_tdd_drop");
        setStageOnMemoryFlag(Stage.COMPLETED, target, "$naderin_tdd_completed");
        setCreditReward(reward);

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

    protected void updateInteractionDataImpl() {
        set("$naderin_tdd_reward", reward);
        set("$naderin_tdd_aOrAnThing", thing);
        set("$naderin_tdd_thing", getWithoutArticle(thing));
        set("$naderin_tdd_personName", giver.getNameString());
        set("$naderin_tdd_systemName", system.getNameWithLowercaseTypeShort());
        set("$naderin_tdd_dist", getDistanceLY(target));
	}

    @Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		// Color h = Misc.getHighlightColor();
        if (currentStage == Stage.CONTACT) {
            info.addPara("Ask Remy about where to deliver the package.", opad);
        }
		if (currentStage == Stage.DROP_OFF) {
			info.addPara("Deliver the package to the specified coordinates in the " +
                    system.getNameWithLowercaseTypeShort() + ".", opad);
		}
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		// Color h = Misc.getHighlightColor();
        if (currentStage == Stage.CONTACT) {
            info.addPara("Ask Remy at Derinkuyu Station about where to deliver the package", tc, pad);
            return true;
        }
		if (currentStage == Stage.DROP_OFF) {
			info.addPara("Deliver the package to the specified location in the " +
					system.getNameWithLowercaseTypeShort(), tc, pad);
			return true;
		}
		return false;
	}

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (currentStage == Stage.CONTACT) {
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