package data.scripts.plugins;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class PTilt_shipDirection extends BaseEveryFrameCombatPlugin {

    private CombatEngineAPI engine;
    SpriteAPI
            arrow,
            arrowBack;
    ViewportAPI vp;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;

        //float sizeMult = 1.5f;

        arrow = Global.getSettings().getSprite("marker", "direction2");
        arrow.setColor(Misc.getPositiveHighlightColor());
        arrow.setSize(arrow.getWidth(),arrow.getHeight());
        arrow.setNormalBlend();

        arrowBack = Global.getSettings().getSprite("marker", "direction2");
        arrowBack.setColor(Color.BLACK);
        arrowBack.setSize(arrowBack.getWidth() * 1.2f,arrowBack.getHeight() * 1.2f);
        arrowBack.setNormalBlend();
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return;
        }

        if (engine.isPaused()) {
            return;
        }
        if(engine == null || engine.isUIShowingDialog() || engine.getCombatUI().isShowingCommandUI()
                || engine.getPlayerShip() == null || engine.getPlayerShip().getLocation() == null
                || Global.getCurrentState() != GameState.COMBAT || engine.isCombatOver()){
            return;
        }
        ShipAPI playerShip = engine.getPlayerShip();

        if (playerShip == null)return;

        Vector2f playerSpeed = new Vector2f();
        engine.getPlayerShip().getVelocity().normalise(playerSpeed);
        if (VectorUtils.isZeroVector(playerSpeed))return;

        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, Global.getSettings().getScreenWidth(), 0, Global.getSettings().getScreenHeight(), -1, 1);

        Vector2f playerPos = CombatUtils.toScreenCoordinates(engine.getPlayerShip().getLocation());

        float collRad = playerShip.getCollisionRadius();
        float Zoom = engine.getViewport().getViewMult();
        float angle = VectorUtils.getFacing(playerSpeed);

        arrowBack.setAngle(angle);
        arrowBack.renderAtCenter(playerPos.x + ((collRad * playerSpeed.x) / Zoom), playerPos.y + ((collRad * playerSpeed.y) / Zoom));
        arrow.setAngle(angle);
        arrow.renderAtCenter(playerPos.x + ((collRad * playerSpeed.x) / Zoom), playerPos.y + ((collRad * playerSpeed.y) / Zoom));

        glEnd();
        glDisable(GL_BLEND);
        glPopAttrib();
        glColor4f(1, 1, 1, 1);
        glPopMatrix();
    }

}
