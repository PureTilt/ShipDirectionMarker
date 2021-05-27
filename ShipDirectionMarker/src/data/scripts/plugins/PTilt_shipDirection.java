package data.scripts.plugins;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.MagicRender;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.io.IOException;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class PTilt_shipDirection extends BaseEveryFrameCombatPlugin {

    public static final String ID = "PT_DirectionMarker";
    public static final String SETTINGS_PATH = "ShipDirectionMarker.ini";
    SpriteAPI
            arrow,
            arrowBack,
            arrowTarget;
    Color
            allyColor = Misc.getPositiveHighlightColor(),
            enemyColor = Misc.getNegativeHighlightColor();

    private CombatEngineAPI engine;

    private boolean
            customColors = false,
            isON = true,
            enemyIsOn = true,
            reCalcForPlayer = false,
            useShieldShip = true,
            useShieldTarget = true,
            disableOnPause = true,
            drawOnAll = false,
            drawOnForFighters = false;

    private ShipAPI
            target,
            playerShip;

    private float
            collRadShip,
            collRadTarget;

    private float
            shipToggleKey,
            targetToggleKey,
    allToggleKey,
    fightersToggleKey;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;

        try {
            JSONObject cfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);
            shipToggleKey = cfg.getInt("PlayerShipToggleButton");
            targetToggleKey = cfg.getInt("TargetShipToggleButton");
            allToggleKey = cfg.getInt("DrawOnAllShipsToggleButton");
            fightersToggleKey = cfg.getInt("IncludeFightersInDrawOnAllToggle");
            disableOnPause = cfg.getBoolean("DisableMarkerOnPause");
            drawOnAll = cfg.getBoolean("DrawOnAll");
            drawOnForFighters = cfg.getBoolean("DrawOnAllFighters");
            isON = cfg.getBoolean("PayerMarkerOnAtStartOfCombat");
            enemyIsOn = cfg.getBoolean("TargetMarkerOnAtStartOfCombat");
            customColors = cfg.getBoolean("customColors");
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }


        //float sizeMult = 1.5f;
        if (customColors) {
            try {
                JSONObject cfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);
                allyColor = getColor(cfg.getJSONArray("allyColor"));
                enemyColor = getColor(cfg.getJSONArray("enemyColor"));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }

        arrow = Global.getSettings().getSprite("marker", "direction2");
        arrow.setColor(allyColor);
        arrow.setSize(arrow.getWidth(), arrow.getHeight());
        arrow.setNormalBlend();

        arrowTarget = Global.getSettings().getSprite("marker", "direction2");
        arrowTarget.setColor(enemyColor);
        arrowTarget.setSize(arrow.getWidth(), arrow.getHeight());
        arrowTarget.setNormalBlend();

        arrowBack = Global.getSettings().getSprite("marker", "direction2");
        arrowBack.setColor(Color.BLACK);
        arrowBack.setSize(arrowBack.getWidth() * 1.2f, arrowBack.getHeight() * 1.2f);
        arrowBack.setNormalBlend();
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        if (engine == null) return;

        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;

            if (e.isKeyDownEvent() && e.getEventValue() == shipToggleKey) {
                isON = !isON;
            }
            if (e.isKeyDownEvent() && e.getEventValue() == targetToggleKey) {
                enemyIsOn = !enemyIsOn;
            }
            if (e.isKeyDownEvent() && e.getEventValue() == allToggleKey) {
                drawOnAll = !drawOnAll;
            }
            if (e.isKeyDownEvent() && e.getEventValue() == fightersToggleKey) {
                drawOnForFighters = !drawOnForFighters;
            }
        }
    }

    Color getColor(JSONArray c) throws JSONException {
        return new Color(
                Math.min(255, Math.max(0, c.getInt(0))),
                Math.min(255, Math.max(0, c.getInt(1))),
                Math.min(255, Math.max(0, c.getInt(2))),
                Math.min(255, Math.max(0, c.getInt(3)))
        );
    }

    Color getColor(Color c, float alphaMult) {
        return new Color(
                Math.min(1, Math.max(0, c.getRed() / 255f)),
                Math.min(1, Math.max(0, c.getGreen() / 255f)),
                Math.min(1, Math.max(0, c.getBlue() / 255f)),
                Math.min(1, Math.max(0, (c.getAlpha() / 255f) * alphaMult))
        );
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {

        if (engine == null) return;
        if (engine.isPaused() && disableOnPause) return;
        if (engine.isUIShowingDialog()) return;
        if (engine.getCombatUI() != null && engine.getCombatUI().isShowingCommandUI()) return;
        if (engine.getPlayerShip() == null) return;
        if (engine.getPlayerShip().getLocation() == null) return;
        if (Global.getCurrentState() != GameState.COMBAT) return;
        if (engine.isCombatOver()) return;

        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, Global.getSettings().getScreenWidth(), 0, Global.getSettings().getScreenHeight(), -1, 1);

        //check if player ship same as in last frame
        if (playerShip == null || playerShip != engine.getPlayerShip()) {
            playerShip = engine.getPlayerShip();
            reCalcForPlayer = true;
        }

        float zoom = engine.getViewport().getViewMult();

        Vector2f whereToDraw;
        //player part
        if (isON) {

            if (reCalcForPlayer) {
                float shieldRad = playerShip.getShieldRadiusEvenIfNoShield();
                float collRad = playerShip.getCollisionRadius();

                //chose what to use
                if (shieldRad > collRad * 1.5f || shieldRad < collRad * 0.3f) {
                    collRadShip = collRad;
                    useShieldShip = false;
                } else {
                    collRadShip = shieldRad + 20f;
                    useShieldShip = true;
                }
            }

            Vector2f playerSpeed = new Vector2f();
            playerShip.getVelocity().normalise(playerSpeed);
            //don't render is doesn't move
            if (!VectorUtils.isZeroVector(playerSpeed)) {

                Vector2f playerPos = CombatUtils.toScreenCoordinates((useShieldShip) ? playerShip.getShieldCenterEvenIfNoShield() : playerShip.getLocation());

                //Some calculations
                float angleShip = VectorUtils.getFacing(playerSpeed);
                whereToDraw = new Vector2f(playerPos.x + ((collRadShip * playerSpeed.x) / zoom), playerPos.y + ((collRadShip * playerSpeed.y) / zoom));

                //render body
                arrowBack.setAngle(angleShip);
                arrowBack.renderAtCenter(whereToDraw.x, whereToDraw.y);
                arrow.setAngle(angleShip);
                arrow.renderAtCenter(whereToDraw.x, whereToDraw.y);
            }
        }

        //targetPart
        if (enemyIsOn && !drawOnAll) {

            if (playerShip.getShipTarget() != null) {
                //check if target ship same as in last frame
                if (target == null || target != playerShip.getShipTarget()) {

                    target = engine.getPlayerShip().getShipTarget();

                    float shieldRad = target.getShieldRadiusEvenIfNoShield();
                    float collRad = target.getCollisionRadius();


                    //chose what to use
                    if (shieldRad > collRad * 1.5f || shieldRad < collRad * 0.3f) {
                        collRadTarget = collRad;
                        useShieldTarget = false;
                    } else {
                        collRadTarget = shieldRad + 20f;
                        useShieldTarget = true;
                    }
                }

                //change color
                if (target.getOwner() == playerShip.getOwner()) arrowTarget.setColor(allyColor);
                else arrowTarget.setColor(enemyColor);

                Vector2f targetSpeed = new Vector2f();
                target.getVelocity().normalise(targetSpeed);
                //don't render is doesn't move
                if (!VectorUtils.isZeroVector(targetSpeed)) {

                    Vector2f targetPos = CombatUtils.toScreenCoordinates((useShieldTarget) ? target.getShieldCenterEvenIfNoShield() : target.getLocation());

                    //Some calculations
                    float angleTarget = VectorUtils.getFacing(targetSpeed);
                    whereToDraw = new Vector2f(targetPos.x + ((collRadTarget * targetSpeed.x) / zoom), targetPos.y + ((collRadTarget * targetSpeed.y) / zoom));

                    //render body
                    arrowBack.setAngle(angleTarget);
                    arrowBack.renderAtCenter(whereToDraw.x, whereToDraw.y);
                    arrowTarget.setAngle(angleTarget);
                    arrowTarget.renderAtCenter(whereToDraw.x, whereToDraw.y);
                }
            }
        }

        if (drawOnAll) {
            for (ShipAPI ship : engine.getShips()) {
                if (!engine.isEntityInPlay(ship)) continue;
                if (isON && ship == playerShip) continue;
                if (!MagicRender.screenCheck(0.5f, ship.getLocation())) continue;
                if (!ship.isAlive()) continue;
                if (ship.isHulk()) continue;
                if (ship.getHullSize() == ShipAPI.HullSize.FIGHTER && !drawOnForFighters) continue;
                if (VectorUtils.isZeroVector(ship.getVelocity())) continue;

                float shieldRad = ship.getShieldRadiusEvenIfNoShield();
                float collRad = ship.getCollisionRadius();

                //change color
                if (ship.getOwner() == playerShip.getOwner()){
                    arrowTarget.setColor(allyColor);
                }
                else arrowTarget.setColor(enemyColor);

                //chose what to use
                float collRadTarget;
                boolean useShieldTarget;
                if (shieldRad > collRad * 1.5f || shieldRad < collRad * 0.3f) {
                    collRadTarget = collRad;
                    useShieldTarget = false;
                } else {
                    collRadTarget = shieldRad + 20f;
                    useShieldTarget = true;
                }
                Vector2f targetPos = CombatUtils.toScreenCoordinates((useShieldTarget) ? ship.getShieldCenterEvenIfNoShield() : ship.getLocation());

                Vector2f targetSpeed = new Vector2f();
                ship.getVelocity().normalise(targetSpeed);

                //Some calculations
                float angleTarget = VectorUtils.getFacing(targetSpeed);
                whereToDraw = new Vector2f(targetPos.x + ((collRadTarget * targetSpeed.x) / zoom), targetPos.y + ((collRadTarget * targetSpeed.y) / zoom));

                //render body
                arrowBack.setAngle(angleTarget);
                arrowBack.renderAtCenter(whereToDraw.x, whereToDraw.y);
                arrowTarget.setAngle(angleTarget);
                arrowTarget.renderAtCenter(whereToDraw.x, whereToDraw.y);
            }
        }

        glEnd();
        glDisable(GL_BLEND);
        glPopAttrib();
        glColor4f(1, 1, 1, 1);
        glPopMatrix();
    }
}
