package scripts.campaign;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import scripts.campaign.intel.missions.FirstBattleEndDialog;

public class DeadMansRiotCampaignPlugin extends BaseCampaignPlugin
{
    public String getID()
    {
        return "deadmansriot_campaignplugin";
    }

    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget)
    {
		// if the player is attempting to interact with their ally fleet after the first battle, 
		// return our custom dialog plugin.
		if (interactionTarget instanceof CampaignFleetAPI && interactionTarget.getMemoryWithoutUpdate().getBoolean("$riot_allySelected"))
        {
			return new PluginPick<InteractionDialogPlugin>(new FirstBattleEndDialog(), PickPriority.MOD_SPECIFIC);
		}
		return null;
	}
}
