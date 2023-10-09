package modplugin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import scripts.*;
import scripts.world.SystemGen;
import scripts.campaign.intel.missions.Riot;

import java.util.List;

public class DeadMansRiotModPlugin extends BaseModPlugin
{
    @Override
    public void onApplicationLoad() 
    {
        Global.getLogger(this.getClass()).info("Dead Man's Riot scripts are working! Start calling code from DeadMansRiotModPlugin.java");
        
        // Add initialization scripts
        blank b = new blank();
        b.TestIntegration();
    }

    @Override
    public void onNewGame()
    {
        new SystemGen().generate(Global.getSector());
    }
}