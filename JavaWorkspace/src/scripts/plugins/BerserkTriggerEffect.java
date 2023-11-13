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
//import java.awt.Color;
//import java.util.ArrayList;
//import java.util.Iterator;
//import java.util.List;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Vector2f;

public class BerserkTriggerEffect extends BaseCombatLayeredRenderingPlugin {
   private int maxTicks = 0;
   protected float sizeMult;
   //private Color cloudColor;
   //protected List<ParticleData> particles = new ArrayList();
   protected boolean betrayed;
   protected DamagingProjectileAPI proj;
   protected ShipAPI target;
   protected Vector2f offset;
   protected int ticks = 0;
   protected IntervalUtil interval;
   protected FaderUtil fader = new FaderUtil(1.0F, 0.5F, 0.5F);
   protected EnumSet<CombatEngineLayers> layers;
   protected ShipAPI nearestShip;
   protected int hitTeam;

   public BerserkTriggerEffect() {
      this.layers = EnumSet.of(CombatEngineLayers.BELOW_INDICATORS_LAYER);
   }

   public BerserkTriggerEffect(DamagingProjectileAPI proj, ShipAPI target, Vector2f offset, int alpha, float duration) {
      this.layers = EnumSet.of(CombatEngineLayers.BELOW_INDICATORS_LAYER);
      this.proj = proj;
      this.target = target;
      this.offset = offset;
      this.maxTicks = (int)(duration * 2.0F);
      //this.cloudColor = new Color(100, 60, 120, alpha);
      this.interval = new IntervalUtil(0.4F, 0.6F);
      this.interval.forceIntervalElapsed();

             //Get the shipapi reference
        ShipAPI hitTarget = ((ShipAPI) target);

        //Get a reference to the original owner of the ship
        this.hitTeam = hitTarget.getOwner();

        //Find the nearest ship
        this.nearestShip = AIUtils.getNearestShip(target);

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
      if (!Global.getCombatEngine().isPaused()) {

         Vector2f loc = new Vector2f(this.offset);
         loc = Misc.rotateAroundOrigin(loc, this.target.getFacing());
         Vector2f.add(this.target.getLocation(), loc, loc);
         this.entity.getLocation().set(loc);
         //List<ParticleData> remove = new ArrayList();
         //Iterator var4 = this.particles.iterator();
         //while(var4.hasNext()) {
           // ParticleData p = (ParticleData)var4.next();
           // p.advance(amount);
          //  if (p.elapsed >= p.maxDur) {
           //    remove.add(p);
          //  }
         //}
         //this.particles.removeAll(remove);
         



         //Global.getSoundPlayer().playLoop("disintegrator_loop", this.target, 1.0F, volume, loc, this.target.getVelocity());
         this.interval.advance(amount);
         if (this.interval.intervalElapsed() && this.ticks <= this.maxTicks) {
            
            //Safety boolean my beloved
            //if(!betrayed){
                //Checks the closest ship
                if (nearestShip.getOwner() == 1) {
                    //Turns the enemy traitor and sets their target
                    turnTraitor(target);
                    target.setShipTarget(nearestShip);
                } else {
                    //Else sets them to target the closest ship
                    target.setShipTarget(nearestShip);
                }
               // betrayed = true;
            //}

            ++this.ticks;
         }


        if (this.ticks >= this.maxTicks) {
            turnTraitorInternal(target, hitTeam);
            this.fader.fadeOut();
            this.fader.advance(amount);

        }
      }
    }



   public boolean isExpired() {
        //this.particles.isEmpty() && 
      return (this.ticks >= this.maxTicks || !this.target.isAlive() || !Global.getCombatEngine().isEntityInPlay(this.target));
   }

   public void render(CombatEngineLayers layer, ViewportAPI viewport) {
      float x = this.entity.getLocation().x;
      float y = this.entity.getLocation().y;
      float b = viewport.getAlphaMult();
      GL14.glBlendEquation(32779);
      //Iterator var6 = this.particles.iterator();

      //while(var6.hasNext()) {
      //   ParticleData p = (ParticleData)var6.next();
      //   float size = p.baseSize * p.scale;
      //   Vector2f loc = new Vector2f(x + p.offset.x, y + p.offset.y);
      //   float alphaMult = 1.0F;
      //   p.sprite.setAngle(p.angle);
      //   p.sprite.setSize(size, size);
      //   p.sprite.setAlphaMult(b * alphaMult * p.fader.getBrightness());
      //   p.sprite.setColor(this.cloudColor);
      //   p.sprite.renderAtCenter(loc.x, loc.y);
      //}

      GL14.glBlendEquation(32774);
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
