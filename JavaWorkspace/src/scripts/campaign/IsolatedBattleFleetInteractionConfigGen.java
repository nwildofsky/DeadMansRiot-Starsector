package scripts.campaign;

import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfigGen;

public class IsolatedBattleFleetInteractionConfigGen implements FIDConfigGen
{
    public FIDConfig createConfig()
    {
        FIDConfig config = new FIDConfig();

        config.pullInAllies = false;
        config.pullInEnemies = false;
        
        return config;
    }
}
