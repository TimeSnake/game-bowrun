/*
 * game-bowrun.main
 * Copyright (C) 2022 timesnake
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; If not, see <http://www.gnu.org/licenses/>.
 */

package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.world.ExLocation;
import de.timesnake.basic.bukkit.util.world.ExWorld;
import de.timesnake.basic.game.util.Map;
import de.timesnake.basic.loungebridge.util.game.ResetableMap;
import de.timesnake.basic.loungebridge.util.tool.Timeable;
import de.timesnake.database.util.game.DbMap;
import de.timesnake.game.bowrun.chat.Plugin;
import de.timesnake.library.basic.util.Tuple;
import org.bukkit.GameRule;
import org.bukkit.Material;

import java.util.*;

public class BowRunMap extends Map implements Timeable, ResetableMap {

    public static final Integer RUNNER_SPAWN_NUMBER = 2; //start, up to 99
    public static final Integer ARCHER_SPAWN_NUMBER = 1;
    public static final Integer RUNNER_FINISH = 0;
    public static final Integer RELAY_PICKUP = 100;

    private static final String TIME_NIGHT = "d";
    private static final String RELAY_RACE = "R";

    private static final String ARCHER_NO_SPECIAL_ITEMS = "n";
    private static final String ARCHER_HOVER = "h";
    private static final String ARCHER_ONLY_INSTANT = "i";
    private static final String ARCHER_ONLY_PUNCH = "p";
    private static final String ARCHER_BOW_NO_GRAVITY = "g";
    private static final String ARCHER_BORDER = "b";
    private static final String ARCHER_KNOCKBACK_BORDER = "k";
    private static final String ARCHER_NO_SPEED = "s";

    private static final String RUNNER_NO_SPECIAL_ITEMS = "N";
    private static final String RUNNER_HOVER = "H";
    private static final String RUNNER_ARMOR = "A";
    private static final String RUNNER_JUMP = "J";
    private static final String RUNNER_SPEED = "S";
    private static final String RUNNER_NO_FALL_DAMAGE = "F";
    private static final String RUNNER_WATER_DAMAGE = "W";

    private final Integer time;
    private final List<ExLocation> runnerSpawns = new ArrayList<>();
    private final Set<Tuple<Integer, Integer>> archerBorderLocs = new HashSet<>();
    private String tags;
    private boolean timeNight = false;
    private boolean archerNoSpecialItems = false;
    private boolean archerHover = false;
    private boolean archerBowNoGravity = false;
    private boolean archerBorder = false;
    private boolean archerKnockbackBorder = false;
    private boolean archerNoSpeed = false;
    private boolean onlyInstant = false;
    private boolean onlyPunch = false;
    private boolean runnerNoSpecialItems = false;
    private boolean runnerArmor = false;
    private boolean runnerHover = false;
    private boolean runnerJump = false;
    private boolean runnerSpeed = false;
    private boolean runnerNoFallDamage = false;
    private boolean runnerWaterDamage = false;
    private boolean relayRace = false;
    private int runnerDeathHeight = 0;
    private Integer bestTime;
    private UUID bestTimeUser;

    public BowRunMap(DbMap map) {
        super(map, true);

        ExWorld world = this.getWorld();
        if (world != null) {
            Server.getWorldManager().backupWorld(world);
            world.restrict(ExWorld.Restriction.BLOCK_PLACE, true);
            world.restrict(ExWorld.Restriction.FIRE_SPREAD, true);
            world.restrict(ExWorld.Restriction.BLOCK_BREAK, true);
            world.restrict(ExWorld.Restriction.ENTITY_EXPLODE, true);
            world.restrict(ExWorld.Restriction.BLOCK_BURN_UP, true);
            world.restrict(ExWorld.Restriction.LIGHT_UP_INTERACTION, false);
            world.restrict(ExWorld.Restriction.FLUID_COLLECT, true);
            world.restrict(ExWorld.Restriction.FLUID_PLACE, true);
            world.restrict(ExWorld.Restriction.FLINT_AND_STEEL, true);
            world.setExceptService(true);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.restrict(ExWorld.Restriction.OPEN_INVENTORIES, List.of(Material.DROPPER, Material.HOPPER, Material.DISPENSER));
            world.setTime(1000);
            world.setStorm(false);
            world.setAutoSave(false);
        }

        ArrayList<String> info = new ArrayList<>(super.getInfo());

        // time
        int time = 7 * 60;

        if (info.size() >= 1) {
            try {
                time = Integer.parseInt(info.get(0));
            } catch (NumberFormatException e) {
                Server.printWarning(Plugin.BOWRUN, "Could not load time of map " + this.getName());
            }
        } else {
            Server.printWarning(Plugin.BOWRUN, "Could not load time of map " + this.getName());
        }

        this.time = time;

        // tags
        if (info.size() >= 2) {
            this.tags = info.get(1);
        } else {
            Server.printWarning(Plugin.BOWRUN, "Could not load tags of map " + this.getName());
        }

        if (this.tags != null) {
            this.timeNight = tags.contains(TIME_NIGHT);
            if (world != null) {
                world.setTime(18000);
            }

            this.archerNoSpecialItems = tags.contains(ARCHER_NO_SPECIAL_ITEMS);
            this.archerHover = tags.contains(ARCHER_HOVER);
            this.archerBowNoGravity = tags.contains(ARCHER_BOW_NO_GRAVITY);
            this.archerNoSpeed = tags.contains(ARCHER_NO_SPEED);
            this.onlyInstant = tags.contains(ARCHER_ONLY_INSTANT);
            this.onlyPunch = tags.contains(ARCHER_ONLY_PUNCH);
            this.runnerHover = tags.contains(RUNNER_HOVER);
            this.runnerNoSpecialItems = tags.contains(RUNNER_NO_SPECIAL_ITEMS);
            this.runnerArmor = tags.contains(RUNNER_ARMOR);
            this.runnerJump = tags.contains(RUNNER_JUMP);
            this.runnerSpeed = tags.contains(RUNNER_SPEED);
            this.runnerNoFallDamage = tags.contains(RUNNER_NO_FALL_DAMAGE);
            this.runnerWaterDamage = tags.contains(RUNNER_WATER_DAMAGE);
            this.relayRace = tags.contains(RELAY_RACE);
            this.archerBorder = tags.contains(ARCHER_BORDER);
            this.archerKnockbackBorder = tags.contains(ARCHER_KNOCKBACK_BORDER);

            if (this.archerBorder || this.archerKnockbackBorder) {
                this.searchArcherBorder();
            }

            if (this.getWorld() != null) {
                if (this.runnerNoFallDamage) {
                    this.getWorld().setGameRule(GameRule.FALL_DAMAGE, false);
                } else {
                    this.getWorld().setGameRule(GameRule.FALL_DAMAGE, true);
                }
            }
        }

        if (info.size() >= 3) {
            try {
                this.runnerDeathHeight = Integer.parseInt(info.get(2));
            } catch (NumberFormatException e) {
                Server.printWarning(Plugin.BOWRUN, "Could not load death-height of map " + this.getName());
            }
        }

        // best time
        int bestTime = this.time;

        if (info.size() >= 4) {
            try {
                bestTime = Integer.parseInt(info.get(3));
            } catch (NumberFormatException e) {
                Server.printWarning(Plugin.BOWRUN, "Could not load best-time of map " + this.getName());
            }
        } else {
            Server.printWarning(Plugin.BOWRUN, "Could not load best-time of map " + this.getName());
        }

        this.bestTime = bestTime;

        if (info.size() >= 5) {
            try {
                this.bestTimeUser = UUID.fromString(info.get(4));
            } catch (IllegalArgumentException e) {
                Server.printWarning(Plugin.BOWRUN, "Could not load best-user of map " + this.getName());
            }

        }

        for (int i = RUNNER_SPAWN_NUMBER; i < this.getSpawnsAmount() && i < RELAY_PICKUP; i++) {
            ExLocation location = super.getLocation(i);
            if (location != null) {
                this.runnerSpawns.add(location);
            }
        }

    }

    private void searchArcherBorder() {
        for (int i = 200; i < 300; i++) {
            ExLocation loc = this.getLocation(i);

            if (loc == null) {
                break;
            }

            this.searchArcherBorder(loc);
        }
    }

    private void searchArcherBorder(ExLocation loc) {
        if (loc.getBlock().getType().equals(Material.STRUCTURE_VOID)
                && !this.archerBorderLocs.contains(new Tuple<>(loc.getBlockX(), loc.getBlockZ()))) {

            this.archerBorderLocs.add(new Tuple<>(loc.getBlockX(), loc.getBlockZ()));
            loc.getBlock().setType(Material.AIR);

            this.searchArcherBorder(new ExLocation(loc.getExWorld(), loc.getX() - 1, loc.getY(), loc.getZ()));
            this.searchArcherBorder(new ExLocation(loc.getExWorld(), loc.getX() + 1, loc.getY(), loc.getZ()));

            this.searchArcherBorder(new ExLocation(loc.getExWorld(), loc.getX(), loc.getY(), loc.getZ() - 1));
            this.searchArcherBorder(new ExLocation(loc.getExWorld(), loc.getX(), loc.getY(), loc.getZ() + 1));
        }
    }

    public List<ExLocation> getRunnerSpawns() {
        return this.runnerSpawns;
    }

    public ExLocation getArcherSpawn() {
        return super.getLocation(ARCHER_SPAWN_NUMBER);
    }

    public ExLocation getRunnerFinish() {
        return super.getLocation(RUNNER_FINISH);
    }

    public ExLocation getRelayPickUp() {
        return super.getLocation(RELAY_PICKUP);
    }

    @Override
    public int getTime() {
        return time;
    }

    public Integer getBestTime() {
        return bestTime;
    }

    public void setBestTime(Integer bestTime, UUID bestTimeUser) {
        this.bestTime = bestTime;
        this.bestTimeUser = bestTimeUser;
        String tags = this.tags != null ? this.tags : "";
        this.getDatabase().setInfo(List.of("" + this.time, tags, "" + this.runnerDeathHeight, "" + this.bestTime,
                this.bestTimeUser.toString()));
    }

    public UUID getBestTimeUser() {
        return bestTimeUser;
    }

    public String getTags() {
        return tags;
    }

    public boolean isOnlyInstant() {
        return onlyInstant;
    }

    public boolean isArcherHover() {
        return archerHover;
    }

    public boolean isArcherBowNoGravity() {
        return archerBowNoGravity;
    }

    public boolean isArcherBorder() {
        return archerBorder;
    }

    public boolean isArcherKnockbackBorder() {
        return archerKnockbackBorder;
    }

    public boolean isArcherNoSpeed() {
        return archerNoSpeed;
    }

    public boolean isRunnerArmor() {
        return runnerArmor;
    }

    public boolean isOnlyPunch() {
        return onlyPunch;
    }

    public boolean isRunnerJump() {
        return runnerJump;
    }

    public boolean isRunnerSpeed() {
        return runnerSpeed;
    }

    public boolean isRunnerHover() {
        return runnerHover;
    }

    public boolean isRunnerNoFallDamage() {
        return runnerNoFallDamage;
    }

    public boolean isArcherNoSpecialItems() {
        return archerNoSpecialItems;
    }

    public boolean isRunnerNoSpecialItems() {
        return runnerNoSpecialItems;
    }

    public boolean isRunnerWaterDamage() {
        return runnerWaterDamage;
    }

    public boolean isRelayRace() {
        return relayRace;
    }

    public boolean isTimeNight() {
        return timeNight;
    }

    public int getRunnerDeathHeight() {
        return runnerDeathHeight;
    }

    public Set<Tuple<Integer, Integer>> getArcherBorderLocs() {
        return archerBorderLocs;
    }
}
