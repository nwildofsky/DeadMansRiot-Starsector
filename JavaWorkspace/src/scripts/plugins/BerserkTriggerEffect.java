package scripts.plugins;

import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class BerserkTriggerEffect implements OnHitEffectPlugin
{
    @Override
    public void onHit(DamagingProjectileAPI proj, CombatEntityAPI taregt, Vector2f pos, boolean hitShield,
            ApplyDamageResultAPI damageResult, CombatEngineAPI combatEngine)
    {
        Global.getLogger(this.getClass()).info("Berserk Trigger Effect called on hit");
    }
}
