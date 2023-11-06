// Source code is decompiled from a .class file using FernFlower decompiler.
package scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import java.util.EnumSet;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Vector2f;

public class BerserkTriggerEffect extends BaseCombatLayeredRenderingPlugin {
   private int maxTicks = 0;
   protected DamagingProjectileAPI proj;
   protected ShipAPI target;
   protected Vector2f offset;
   protected int ticks = 0;
   protected IntervalUtil interval;
   protected FaderUtil fader = new FaderUtil(1.0F, 0.5F, 0.5F);
   protected EnumSet<CombatEngineLayers> layers;
   protected boolean betrayed = false;

   public BerserkTriggerEffect() {
      this.layers = EnumSet.of(CombatEngineLayers.BELOW_INDICATORS_LAYER);
   }

   public BerserkTriggerEffect(DamagingProjectileAPI proj, ShipAPI target, Vector2f offset, int alpha, float duration) {
      this.layers = EnumSet.of(CombatEngineLayers.BELOW_INDICATORS_LAYER);
      this.proj = proj;
      this.target = target;
      this.offset = offset;
      this.maxTicks = (int)(duration * 2.0F);
      this.interval = new IntervalUtil(0.4F, 0.6F);
      this.interval.forceIntervalElapsed();
   }

   public float getRenderRadius() {
      return 500.0F;
   }

   public EnumSet<CombatEngineLayers> getActiveLayers() {
      return this.layers;
   }

   public void init(CombatEntityAPI entity) {
      super.init(entity);
   }

   public void advance(float amount) {
        
        //Get the shipapi reference
        ShipAPI hitTarget = ((ShipAPI) target);

        //Get a reference to the original owner of the ship
        int hitTeam = hitTarget.getOwner();

        //Find the nearest ship
        ShipAPI nearestShip = AIUtils.getNearestShip(target);

        //Clears the effect if the ticks surpass the duration of the effect
        if (this.ticks >= this.maxTicks || !this.target.isAlive() || !Global.getCombatEngine().isEntityInPlay(this.target)) {
            turnTraitorInternal(hitTarget, hitTeam);
            this.fader.fadeOut();
            this.fader.advance(amount);
         }

         //Advance the interval
         this.interval.advance(amount);
         
            //Safety boolean my beloved
            if(!betrayed){
                //Checks the closest ship
                if (nearestShip.getOwner() == 1) {
                    //Turns the enemy traitor and sets their target
                    turnTraitor(hitTarget);
                    hitTarget.setShipTarget(nearestShip);
                } else {
                    //Else sets them to target the closest ship
                    hitTarget.setShipTarget(nearestShip);
                }
                betrayed = true;
            }
            

        //Increments the tick counter while the interval is valid
        if (this.interval.intervalElapsed() && this.ticks <= this.maxTicks) {

            ++this.ticks;
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


   public void render(CombatEngineLayers layer, ViewportAPI viewport) {

      GL14.glBlendEquation(32779);
      

      GL14.glBlendEquation(32774);
   }
}
