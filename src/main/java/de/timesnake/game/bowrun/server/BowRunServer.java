/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.user.inventory.ExItemStack;
import de.timesnake.basic.game.util.game.Team;
import de.timesnake.basic.loungebridge.util.server.LoungeBridgeServer;
import de.timesnake.game.bowrun.user.BowRunUser;
import de.timesnake.library.basic.util.TimeCoins;
import de.timesnake.library.basic.util.Tuple;
import de.timesnake.library.basic.util.statistics.IntegerStat;
import de.timesnake.library.basic.util.statistics.PercentStat;
import de.timesnake.library.basic.util.statistics.StatType;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class BowRunServer extends LoungeBridgeServer {

  public static final List<ExItemStack> RUNNER_ITEMS = List.of(
      ExItemStack.getPotion(ExItemStack.PotionMaterial.SPLASH, 2, "§6Heal II",
          PotionEffectType.INSTANT_HEALTH, 0, 2),
      ExItemStack.getPotion(ExItemStack.PotionMaterial.SPLASH, 1, "§6Jump II  (7 s)",
          PotionEffectType.JUMP_BOOST, 20 * 7, 2),
      ExItemStack.getPotion(ExItemStack.PotionMaterial.SPLASH, 1, "§6Slow Fall (20 s)",
          PotionEffectType.SLOW_FALLING, 20 * 20, 1),
      ExItemStack.getPotion(ExItemStack.PotionMaterial.SPLASH, 1, "§6Speed II (30 s)",
          PotionEffectType.SPEED, 20 * 15, 2),
      ExItemStack.getPotion(ExItemStack.PotionMaterial.SPLASH, 1, "§6Invisibility (8 s)",
          PotionEffectType.INVISIBILITY, 20 * 8, 1, List.of("§fRemoves your armor")),
      ExItemStack.getPotion(ExItemStack.PotionMaterial.SPLASH, 1, "§6Fire Resistance (45 s)",
          PotionEffectType.FIRE_RESISTANCE, 20 * 45, 1),
      ExItemStack.getPotion(ExItemStack.PotionMaterial.SPLASH, 1, "§6Resistance (1 min)",
          PotionEffectType.RESISTANCE, 20 * 60, 1),
      new ExItemStack(Material.GOLDEN_APPLE, 2),
      new ExItemStack(Material.SHIELD).setDamage(300),
      new ExItemStack(Material.TOTEM_OF_UNDYING),
      new ExItemStack(Material.CHORUS_FRUIT, 5));

  public static final List<ExItemStack> ARCHER_ITEMS = List.of(
      new ExItemStack(Material.BOW).setDisplayName("§6Flame-Bow").setDamage(380)
          .addEnchantments(new Tuple<>(Enchantment.FLAME, 1)),
      new ExItemStack(Material.BOW).setDisplayName("§6Power-Bow").setDamage(384)
          .addEnchantments(new Tuple<>(Enchantment.POWER, 7)),
      new ExItemStack(Material.BOW).setDisplayName("§6Punch-Bow").setDamage(382)
          .addEnchantments(new Tuple<>(Enchantment.PUNCH, 2)),
      new ExItemStack(Material.SPECTRAL_ARROW, 32).setDisplayName("§6Spectral-Arrow"));

  public static final Instrument TIME_INSTRUMENT = Instrument.PLING;
  public static final Note TIME_NOTE = Note.natural(1, Note.Tone.A);

  public static final Double RUNNER_ITEM_CHANCE_MULTIPLIER = 0.6;
  public static final Double ARCHER_ITEM_CHANCE = 0.2;

  public static final int MAX_ARROWS = 4;
  public static final int RESPAWN_ARROW_AMOUNT = 2;
  public static final double ARROW_GENERATION_PERIOD = 20;
  public static final double ARROW_GENERATION_PLAYER_MULTIPLIER = 2;

  public static final float KILL_COINS_POOL = 16 * TimeCoins.MULTIPLIER;
  public static final float WIN_COINS = 10 * TimeCoins.MULTIPLIER;
  public static final float RECORD_COINS = 20 * TimeCoins.MULTIPLIER;

  public static final String SIDEBOARD_KILLS_TEXT = "§c§lKills";
  public static final String SIDEBOARD_DEATHS_TEXT = "§c§lDeaths";

  public static final StatType<Integer> RUNNER_WINS = new IntegerStat("runner_wins",
      "Runner Wins",
      0, 10, 2, true, 0, 1);
  public static final StatType<Integer> ARCHER_WINS = new IntegerStat("archer_wins",
      "Archer Wins",
      0, 10, 3, true, 0, 2);
  public static final StatType<Float> WIN_CHANCE = new PercentStat("win_chance", "Win Chance",
      0f, 10, 4, false, null, null);
  public static final StatType<Integer> DEATHS = new IntegerStat("runner_deaths", "Deaths",
      0, 10, 5, true, 0, 3);
  public static final StatType<Integer> KILLS = new IntegerStat("archer_kills", "Kills",
      0, 10, 6, true, 1, 1);
  public static final StatType<Integer> MOST_KILLS_PER_MATCH = new IntegerStat(
      "archer_most_kills_match",
      "Most Kills in a Match",
      0, 10, 7, true, 1, 2);
  public static final StatType<Integer> LONGEST_SHOT = new IntegerStat("archer_longest_shot",
      "Longest Shot",
      0, 10, 8, true, 1, 3);

  public static BossBar getTimeBar() {
    return server.getTimeBar();
  }

  public static void stopGame(BowRunServer.WinType winType, BowRunUser finisher) {
    server.stopGame(winType, finisher);
  }

  public static BowRunMap getMap() {
    return server.getMap();
  }

  public static BowRunGame getGame() {
    return server.getGame();
  }

  public static double getRunnerItemChance() {
    return server.getRunnerItemChance();
  }

  public static void setGameSideboardScore(int line, String text) {
    server.setGameSideboardScore(line, text);
  }

  public static void setSpectatorSideboardScore(int line, String text) {
    server.setSpectatorSideboardScore(line, text);
  }

  public static void updateGameTimeOnSideboard() {
    server.updateGameTimeOnSideboard();
  }

  public static ExItemStack getRandomRunnerItem() {
    return server.getRandomRunnerItem();
  }

  public static ExItemStack getRandomArcherItem() {
    return server.getRandomArcherItem();
  }

  public static void addRunnerDeath() {
    server.addRunnerDeath();
  }

  public static int getRunnerDeaths() {
    return server.getRunnerDeaths();
  }

  public static Integer getPlayingTime() {
    return server.getPlayingTime();
  }

  public static List<Boolean> getRunnerArmor() {
    return server.getRunnerArmor();
  }

  private static final BowRunServerManager server = BowRunServerManager.getInstance();

  public enum WinType {
    RUNNER_FINISH(BowRunServer.getGame().getRunnerTeam()),
    RUNNER(BowRunServer.getGame().getRunnerTeam()),
    ARCHER(BowRunServer.getGame().getArcherTeam()),
    ARCHER_TIME(BowRunServer.getGame().getArcherTeam()),
    END(null);

    private final Team team;

    WinType(Team team) {
      this.team = team;
    }

    public Team getTeam() {
      return team;
    }
  }

}
