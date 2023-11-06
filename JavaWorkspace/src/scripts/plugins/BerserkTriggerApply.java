package scripts.plugins;

import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;

import org.lwjgl.util.vector.Vector2f;



import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.IntervalUtil;

public class BerserkTriggerApply implements OnHitEffectPlugin {

    public BerserkTriggerApply() {
    }
 

    protected IntervalUtil interval;
    protected float upTime = 0.0f;

    //Calls the onHit method and applies the effect only when a valid hit is detected
    public void onHit(DamagingProjectileAPI proj, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damage, CombatEngineAPI engine) {
        if (!shieldHit) {
           if (!proj.isFading()) {
              if (target instanceof ShipAPI) {
                 Vector2f offset = Vector2f.sub(point, target.getLocation(), new Vector2f());
                 offset = Misc.rotateAroundOrigin(offset, -target.getFacing());
                 
                 int alpha = 20;

                 //See BerserkTriggerEffect for the internals
                 BerserkTriggerEffect effect = new BerserkTriggerEffect(proj, (ShipAPI)target, offset, alpha, 10);
                 CombatEntityAPI e = engine.addLayeredRenderingPlugin(effect);
                 e.getLocation().set(proj.getLocation());
              }
           }
        }
     }
  }
