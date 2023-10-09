package scripts.world;

import com.fs.starfarer.api.campaign.SectorAPI;
import scripts.world.systems.LazarusSystem;

public class SystemGen
{
    public void generate(SectorAPI sector)
    {
        new LazarusSystem().generate(sector);
    }
}
