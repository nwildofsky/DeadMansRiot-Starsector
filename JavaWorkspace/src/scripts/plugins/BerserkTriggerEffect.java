package scripts.plugins;

import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;

import org.lwjgl.util.vector.Vector2f;

import org.lazywizard.lazylib.combat.AIUtils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.IntervalUtil;

public class BerserkTriggerEffect extends BaseCombatLayeredRenderingPlugin implements OnHitEffectPlugin {

    public void TestIntegration()
    {
        Global.getLogger(this.getClass()).info("Running blank through the ModPlugin, integration success!");
    }

    protected IntervalUtil interval;
    protected float upTime = 0.0f;

    @Override
    public void onHit(DamagingProjectileAPI proj, CombatEntityAPI target, Vector2f pos, boolean hitShield,
            ApplyDamageResultAPI damageResult, CombatEngineAPI combatEngine) {
        // Global.getLogger(this.getClass()).info("Berserk Trigger Effect called on
        // hit");

        if (!hitShield) {
            if (target instanceof ShipAPI && upTime <= 10f) {

                ShipAPI hitTarget = ((ShipAPI) target);

                int hitTeam = hitTarget.getOwner();

                ShipAPI nearestShip = AIUtils.getNearestShip(target);

                if (nearestShip.getOwner() == 1) {
                    turnTraitor(hitTarget);
                    hitTarget.setShipTarget(nearestShip);
                } else {
                    hitTarget.setShipTarget(nearestShip);
                }

                if(upTime >= 10f){
                    target.setOwner(hitTeam);
                    upTime = 0f;
                }

            }
            

        }

    }

    public void advance(float amount) {
        if (!Global.getCombatEngine().isPaused()) {

            this.interval.advance(amount);
            if (this.interval.intervalElapsed() && this.upTime < 20) {

                ++this.upTime;
            }

        }
    }

    private static void turnTraitorInternal(ShipAPI ship, int newOwner) {
        // Switch to the opposite side
        ship.setOwner(newOwner);
        ship.setOriginalOwner(newOwner);

        // Force AI to re-evaluate surroundings
        if (ship.getShipAI() != null) {
            ship.getShipAI().forceCircumstanceEvaluation();
        }

        // Also switch sides of any drones (doesn't affect any new ones)
        if (ship.getDeployedDrones() != null) {
            for (ShipAPI drone : ship.getDeployedDrones()) {
                drone.setOwner(newOwner);
                drone.getShipAI().forceCircumstanceEvaluation();
            }
        }

        // As well as any fighters launched from that ship
        if (ship.hasLaunchBays()) {
            for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
                for (ShipAPI fighter : bay.getWing().getWingMembers()) {
                    turnTraitorInternal(fighter, newOwner);
                }
            }
        }
    }

    public static void turnTraitor(ShipAPI ship) {
        // Switch squadmates if this is a fighter wing
        final int newOwner = (ship.getOwner() == 0 ? 1 : 0);

        if (ship.isFighter() && !ship.isDrone()) {
            for (ShipAPI member : ship.getWing().getWingMembers()) {
                turnTraitorInternal(member, newOwner);
            }
        } else {
            turnTraitorInternal(ship, newOwner);
        }
    }

}