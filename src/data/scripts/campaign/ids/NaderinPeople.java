package data.scripts.campaign.ids;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
// import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
// import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

// note: see com\fs\starfarer\api\impl\campaign\ids\People.java
public class NaderinPeople {
    // The Derinkuyu Drop
    public static final String HARLAN = "naderin_harlan";
    public static final String REMY = "naderin_remy"; 

    public static void createCharacters() {
        ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
        // MarketAPI market = null;

        {
            // Harlan Hines
            PersonAPI person = Global.getFactory().createPerson();
            person.setId(HARLAN);
            person.setFaction(Factions.INDEPENDENT);
            person.setGender(Gender.MALE);
            person.setRankId(Ranks.AGENT);
            // no set post
            person.setImportance(PersonImportance.LOW);
            person.getName().setFirst("Harlan");
            person.getName().setLast("Hines");
            person.setVoice(Voices.SPACER);
            // no portrait yet
            person.getStats().setSkillLevel(Skills.GUNNERY_IMPLANTS, 1);

            ip.addPerson(person);
        }

        {   // Remy Laurent
            PersonAPI person = Global.getFactory().createPerson();
            person.setId(REMY);
            person.setFaction(Factions.INDEPENDENT);
            person.setGender(Gender.MALE);
            person.setRankId(Ranks.AGENT);
            person.setPostId(Ranks.POST_SMUGGLER);
            person.setImportance(PersonImportance.MEDIUM);
            person.getName().setFirst("Remy");
            person.getName().setLast("Laurent");
            person.setVoice(Voices.SPACER);
            // no portrait yet
            person.getStats().setSkillLevel(Skills.SENSORS, 1);
            person.getStats().setSkillLevel(Skills.NAVIGATION, 1);

            ip.addPerson(person);
        }
    }
}
