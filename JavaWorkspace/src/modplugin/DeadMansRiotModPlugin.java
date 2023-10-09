package modplugin;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import scripts.*;
import scripts.world.SystemGen;

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