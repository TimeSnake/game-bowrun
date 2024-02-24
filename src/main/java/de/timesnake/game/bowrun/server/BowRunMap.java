/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.world.ExLocation;
import de.timesnake.basic.bukkit.util.world.ExWorld;
import de.timesnake.basic.game.util.game.Map;
import de.timesnake.basic.loungebridge.util.game.ResetableMap;
import de.timesnake.basic.loungebridge.util.tool.Timeable;
import de.timesnake.database.util.game.DbMap;
import de.timesnake.library.basic.util.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

  private final Logger logger = LogManager.getLogger("bowrun.map");

  private final List<ExLocation> runnerSpawns = new ArrayList<>();
  private final Set<Tuple<Integer, Integer>> archerBorderLocs = new HashSet<>();

  private Integer time;
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
  private UUID bestPlayer;

  public BowRunMap(DbMap map) {
    super(map, true);

    ExWorld world = this.getWorld();
    if (world != null) {
      world.restrict(ExWorld.Restriction.BLOCK_PLACE, true);
      world.restrict(ExWorld.Restriction.FIRE_SPREAD_SPEED, 0f);
      world.restrict(ExWorld.Restriction.BLOCK_BREAK, true);
      world.restrict(ExWorld.Restriction.ENTITY_EXPLODE, true);
      world.restrict(ExWorld.Restriction.BLOCK_BURN_UP, true);
      world.restrict(ExWorld.Restriction.LIGHT_UP_INTERACTION, false);
      world.restrict(ExWorld.Restriction.FLUID_COLLECT, true);
      world.restrict(ExWorld.Restriction.FLUID_PLACE, true);
      world.restrict(ExWorld.Restriction.FLINT_AND_STEEL, true);
      world.restrict(ExWorld.Restriction.CRAFTING, true);
      world.restrict(ExWorld.Restriction.OPEN_INVENTORIES, List.of(Material.DROPPER, Material.HOPPER,
          Material.DISPENSER));
      world.setExceptService(true);

      world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
      world.setGameRule(GameRule.DO_FIRE_TICK, false);
      world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
      world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);

      world.setTime(1000);
      world.setStorm(false);
    }

    this.time = this.getProperty("time", Integer.class, 300,
        v -> this.logger.warn("Could not load time of map '{}'", this.getName()));

    String tags = this.getProperty("tags");

    if (tags != null) {
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

    this.runnerDeathHeight = this.getProperty("death_height", Integer.class, 0,
        v -> this.logger.warn("Could not load death-height of map '{}'", this.getName()));

    this.bestTime = this.getProperty("best_time", Integer.class, null, v -> {
      if (v != null) this.logger.warn("Could not load best-time of map '{}'", this.getName());
    });

    this.bestPlayer = this.getProperty("best_player", UUID.class, null, v -> {
      if (v != null) this.logger.warn("Could not load best-player of map '{}'", this.getName());
    });


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

      this.searchArcherBorder(
          new ExLocation(loc.getExWorld(), loc.getX() - 1, loc.getY(), loc.getZ()));
      this.searchArcherBorder(
          new ExLocation(loc.getExWorld(), loc.getX() + 1, loc.getY(), loc.getZ()));

      this.searchArcherBorder(
          new ExLocation(loc.getExWorld(), loc.getX(), loc.getY(), loc.getZ() - 1));
      this.searchArcherBorder(
          new ExLocation(loc.getExWorld(), loc.getX(), loc.getY(), loc.getZ() + 1));
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

  public void setBestTime(Integer bestTime, UUID bestPlayer) {
    this.bestTime = bestTime;
    this.setProperty("best_time", String.valueOf(bestTime));

    this.bestPlayer = bestPlayer;
    this.setProperty("best_player", String.valueOf(bestPlayer));
  }

  public UUID getBestPlayer() {
    return bestPlayer;
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
