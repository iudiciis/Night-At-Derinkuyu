package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import data.scripts.campaign.ids.NaderinPeople;

public class NaderinModPlugin extends BaseModPlugin {
    @Override
    public void onGameLoad(boolean newGame) {
        if (newGame) {
            NaderinPeople.createCharacters();
        }
    }
}