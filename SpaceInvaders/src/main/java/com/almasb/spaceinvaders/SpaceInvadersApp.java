/*
 * The MIT License (MIT)
 *
 * FXGL - JavaFX Game Library
 *
 * Copyright (c) 2015 AlmasB (almaslvl@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.almasb.spaceinvaders;

import com.almasb.fxgl.app.ApplicationMode;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.asset.Texture;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityType;
import com.almasb.fxgl.entity.control.ProjectileControl;
import com.almasb.fxgl.event.DisplayEvent;
import com.almasb.fxgl.gameplay.Achievement;
import com.almasb.fxgl.input.*;
import com.almasb.fxgl.physics.CollisionHandler;
import com.almasb.fxgl.physics.PhysicsEntity;
import com.almasb.fxgl.physics.PhysicsWorld;
import com.almasb.fxgl.settings.GameSettings;
import com.almasb.fxgl.ui.UIFactory;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;

/**
 * @author Almas Baimagambetov (AlmasB) (almaslvl@gmail.com)
 */
public class SpaceInvadersApp extends GameApplication {

    private static final String SAVE_DATA_NAME = "hiscore.dat";

    public enum Type implements EntityType {
        PLAYER, ENEMY, PLAYER_BULLET, ENEMY_BULLET,
        LEVEL_INFO
    }

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("FXGL Space Invaders");
        settings.setVersion("0.3dev");
        settings.setWidth(600);
        settings.setHeight(800);
        settings.setIntroEnabled(false);
        settings.setMenuEnabled(false);
        settings.setShowFPS(false);
        settings.setApplicationMode(ApplicationMode.DEVELOPER);
    }

    @Override
    protected void initAchievements() {
        getAchievementManager().registerAchievement(
                new Achievement("Hitman", "Destroy 5 enemies"));
        getAchievementManager().registerAchievement(
                new Achievement("Master Scorer", "Score 10000+ points"));
    }

    @Override
    protected void initInput() {
        Input input = getInput();

        input.addInputMapping(new InputMapping("Move Left", KeyCode.A));
        input.addInputMapping(new InputMapping("Move Right", KeyCode.D));
        input.addInputMapping(new InputMapping("Shoot", KeyCode.F));
    }

    @Override
    protected void initAssets() {}

    private Entity player;
    private IntegerProperty enemiesDestroyed;
    private IntegerProperty score;
    private IntegerProperty level;
    private IntegerProperty lives;

    private int highScore;
    private String highScoreName;

    @Override
    protected void initGame() {
        SaveData data = getSaveLoadManager().<SaveData>load(SAVE_DATA_NAME)
                .orElse(new SaveData("CPU", 0));

        highScoreName = data.getName();
        highScore = data.getHighScore();

        getAudioPlayer().setGlobalSoundVolume(0);

        initBackground();

        getNotificationService().setBackgroundColor(Color.DARKBLUE);

        enemiesDestroyed = new SimpleIntegerProperty(0);
        score = new SimpleIntegerProperty();
        level = new SimpleIntegerProperty();
        lives = new SimpleIntegerProperty(3);

        getAchievementManager().getAchievementByName("Hitman")
                .achievedProperty().bind(enemiesDestroyed.greaterThanOrEqualTo(5));
        getAchievementManager().getAchievementByName("Master Scorer")
                .achievedProperty().bind(score.greaterThanOrEqualTo(10000));

        spawnPlayer();
        nextLevel();
    }

    private void initBackground() {
        Entity bg = Entity.noType();
        Texture bgTexture = getAssetLoader().loadTexture("background.png");
        bgTexture.setFitWidth(getWidth());
        bgTexture.setFitHeight(getHeight());

        bg.setSceneView(bgTexture);

        getGameWorld().addEntity(bg);
    }

    private void initLevel() {
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 8; x++) {
                spawnEnemy(x * (40 + 20), 100 + y * (40 + 20));
            }
        }

        getInput().setProcessActions(true);
    }

    private void nextLevel() {
        getInput().setProcessActions(false);
        level.set(level.get() + 1);

        PhysicsEntity levelInfo = new PhysicsEntity(Type.LEVEL_INFO);
        levelInfo.setPosition(getWidth() / 2 - UIFactory.widthOf("Level " + level.get(), 44) / 2, 0);
        levelInfo.setSceneView(UIFactory.newText("Level " + level.get(), Color.AQUAMARINE, 44));
        levelInfo.setBodyType(BodyType.DYNAMIC);
        levelInfo.setOnPhysicsInitialized(() -> levelInfo.setLinearVelocity(0, 5));
        levelInfo.setExpireTime(Duration.seconds(3));

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.setDensity(0.05f);
        fixtureDef.setRestitution(0.3f);
        levelInfo.setFixtureDef(fixtureDef);

        PhysicsEntity ground = new PhysicsEntity(Type.LEVEL_INFO);
        ground.setPosition(0, getHeight() / 2);
        ground.setSceneView(new Rectangle(getWidth(), 100, Color.TRANSPARENT));
        ground.setExpireTime(Duration.seconds(3));

        getGameWorld().addEntities(levelInfo, ground);

        getMasterTimer().runOnceAfter(this::initLevel, Duration.seconds(3));
    }

    @Override
    protected void initPhysics() {
        PhysicsWorld physicsWorld = getPhysicsWorld();

        physicsWorld.addCollisionHandler(new CollisionHandler(Type.ENEMY_BULLET, Type.PLAYER) {
            @Override
            protected void onCollisionBegin(Entity bullet, Entity player) {
                bullet.removeFromWorld();
                lives.set(lives.get() - 1);
                if (lives.get() == 0) {
                    getDisplay().showConfirmationBox("Game Over. Continue?", yes -> {
                        if (yes) {
                            startNewGame();
                        } else {
                            if (score.get() > highScore) {
                                getDisplay().showInputBox("Enter your name", input -> {
                                    getSaveLoadManager().save(new SaveData(input, score.get()), SAVE_DATA_NAME);
                                    getEventBus().fireEvent(new DisplayEvent(DisplayEvent.CLOSE_REQUEST));
                                });
                            } else {
                                getEventBus().fireEvent(new DisplayEvent(DisplayEvent.CLOSE_REQUEST));
                            }
                        }
                    });
                }
            }
        });

        physicsWorld.addCollisionHandler(new CollisionHandler(Type.PLAYER_BULLET, Type.ENEMY) {
            @Override
            protected void onCollisionBegin(Entity bullet, Entity enemy) {
                bullet.removeFromWorld();
                enemy.removeFromWorld();
                enemiesDestroyed.set(enemiesDestroyed.get() + 1);
                score.set(score.get() + 200);

                if (enemiesDestroyed.get() % 40 == 0)
                    nextLevel();
            }
        });
    }

    @Override
    protected void initUI() {
        Text textScore = UIFactory.newText("", Color.AQUAMARINE, 18);
        textScore.textProperty().bind(score.asString("Score:[%d]"));

        Text textHighScore = UIFactory.newText("", Color.AQUAMARINE, 18);
        textHighScore.setText("HiScore:[" + highScore + "](" + highScoreName + ")");

        for (int i = 0; i < lives.get(); i++) {
            final int index = i;
            Texture t = getAssetLoader().loadTexture("life.png");
            t.setFitWidth(16);
            t.setFitHeight(16);
            t.setTranslateX(getWidth() * 4 / 5 + i * 32);
            t.setTranslateY(10);

            lives.addListener((observable, oldValue, newValue) -> {
                if (newValue.intValue() == index) {

                    t.setFitWidth(64);
                    t.setFitHeight(64);

                    TranslateTransition tt = new TranslateTransition(Duration.seconds(0.66), t);
                    tt.setToX(getWidth() / 2 - t.getFitWidth() / 2);
                    tt.setToY(getHeight() / 2 - t.getFitHeight() / 2);
                    tt.setOnFinished(e -> {
                        ScaleTransition st = new ScaleTransition(Duration.seconds(0.66), t);
                        st.setToX(0);
                        st.setToY(0);
                        st.setOnFinished(e2 -> {
                            getGameScene().removeUINode(t);
                        });
                        st.play();
                    });
                    tt.play();
                }
            });

            getGameScene().addUINode(t);
        }

        HBox textBox = new HBox(20, textScore, textHighScore);
        textBox.setTranslateX(25);
        textBox.setTranslateY(10);

        getGameScene().addUINodes(textBox);
    }

    @Override
    protected void onUpdate() {
        getGameWorld().getEntities(Type.PLAYER_BULLET, Type.ENEMY_BULLET)
                .stream()
                .filter(b -> b.isOutside(0, 0, getWidth(), getHeight()))
                .forEach(Entity::removeFromWorld);
    }

    private void spawnEnemy(double x, double y) {
        Entity enemy = new Entity(Type.ENEMY);
        enemy.setPosition(x, y);

        Texture texture = getAssetLoader().loadTexture("tank_enemy.png");
        texture.setFitWidth(40);
        texture.setFitHeight(40);

        enemy.setSceneView(texture);
        enemy.setCollidable(true);
        enemy.setRotation(90);
        enemy.addControl(new EnemyControl());

        getGameWorld().addEntity(enemy);
    }

    private void spawnPlayer() {
        player = new Entity(Type.PLAYER);
        player.setPosition(getWidth() / 2 - 20, getHeight() - 40);

        Texture texture = getAssetLoader().loadTexture("tank_player.png");
        texture.setFitWidth(40);
        texture.setFitHeight(40);

        player.setSceneView(texture);
        player.setCollidable(true);
        player.setRotation(-90);

        getGameWorld().addEntity(player);
    }

    @OnUserAction(name = "Move Left", type = ActionType.ON_ACTION)
    public void moveLeft() {
        if (player.getX() >= 5)
            player.translate(-5, 0);
    }

    @OnUserAction(name = "Move Right", type = ActionType.ON_ACTION_BEGIN)
    public void moveRightStart() {
        log.finer("Starting move right");
    }

    @OnUserAction(name = "Move Right", type = ActionType.ON_ACTION)
    public void moveRight() {
        if (player.getX() <= getWidth() - player.getWidth() - 5)
            player.translate(5, 0);
    }

    @OnUserAction(name = "Move Right", type = ActionType.ON_ACTION_END)
    public void moveRightStop() {
        log.finer("Stopping move right");
    }

    @OnUserAction(name = "Shoot", type = ActionType.ON_ACTION_BEGIN)
    public void shoot() {
        Entity bullet = new Entity(Type.PLAYER_BULLET);
        bullet.setPosition(player.getCenter().subtract(8, player.getHeight() / 2));
        bullet.setCollidable(true);
        bullet.setSceneView(getAssetLoader().loadTexture("tank_bullet.png"));
        bullet.addControl(new ProjectileControl(new Point2D(0, -1), 10));

        getGameWorld().addEntity(bullet);
    }

    public static void main(String[] args) {
        launch(args);
    }
}