package data.scripts.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

/**
 * Sets the provided person as the active person during dialog.
 * Does not start an actual conversation.
 * Inspiration taken from com\fs\starfarer\api\impl\campaign\rulecmd\academy\GenGAIntroAcademician.java
 * though truthfully there's a lot of rulecmds that use setActivePerson().
 */

public class NaderinSetActivePerson extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        PersonAPI person = Global.getSector().getImportantPeople().getPerson(params.get(0).string);
        if (person == null) return false;

        dialog.getInteractionTarget().setActivePerson(person);
        dialog.getVisualPanel().showPersonInfo(person, false, true); // minimal mode, relation bar

        return true;
    }
}
