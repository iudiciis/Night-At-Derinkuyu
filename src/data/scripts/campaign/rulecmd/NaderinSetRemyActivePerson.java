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
 * Sets Remy as the active person during dialog.
 * Does not start an actual conversation.
 * Based on com\fs\starfarer\api\impl\campaign\rulecmd\academy\GenGAIntroAcademician.java
 */
@Deprecated
public class NaderinSetRemyActivePerson extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        PersonAPI person = Global.getSector().getImportantPeople().getPerson("naderin_remy");

        dialog.getInteractionTarget().setActivePerson(person);
        dialog.getVisualPanel().showPersonInfo(person, false, true);

        return true;
    }
}
