/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.game.bowrun.user;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.user.inventory.ExItemStack;
import de.timesnake.basic.loungebridge.util.server.LoungeBridgeServer;
import de.timesnake.basic.loungebridge.util.user.GameUser;
import de.timesnake.game.bowrun.main.GameBowRun;
import de.timesnake.game.bowrun.server.BowRunMap;
import de.timesnake.game.bowrun.server.BowRunServer;
import de.timesnake.library.basic.util.Tuple;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class BowRunUser extends GameUser {

  public static final ItemStack RUNNER_REMOVER = new ExItemStack(Material.NETHERITE_SWORD,
      "§6RunnerRemover")
      .unbreakable()
      .addEnchantments(new Tuple<>(Enchantment.DAMAGE_ALL, 10),
          new Tuple<>(Enchantment.SWEEPING_EDGE, 10))
      .setDropable(false);
  public static final ExItemStack BOW = new ExItemStack(Material.BOW)
      .unbreakable()
      .enchant()
      .setDropable(false);
  public static final ExItemStack FOOD = new ExItemStack(Material.COOKED_BEEF, 32);
  public static final ExItemStack ARROW = new ExItemStack(Material.ARROW)
      .setDropable(false);
  public static final ExItemStack DEATH = new ExItemStack(8, Material.RED_DYE,
      "§cSuicide (left click, no special item)")
      .setDropable(false);

  public static final ExItemStack INSTANT_BOW =
      new ExItemStack(Material.BOW, "§6Instant-Bow")
          .unbreakable()
          .addEnchantments(new Tuple<>(Enchantment.ARROW_DAMAGE, 10))
          .setDropable(false);

  public static final ExItemStack PUNCH_BOW = new ExItemStack(Material.BOW,
      "§6Punch-Bow")
      .unbreakable()
      .addEnchantments(new Tuple<>(Enchantment.ARROW_KNOCKBACK, 5))
      .setDropable(false);

  private ItemStack[] armor;

  private boolean suicided;

  public BowRunUser(Player player) {
    super(player);
    this.setBossBar(BowRunServer.getTimeBar());
  }

  @Override
  public void addDeath() {
    super.addDeath();
    this.setScoreboardKillDeathScore();
  }

  @Override
  public void setDeaths(Integer deaths) {
    super.setDeaths(deaths);
    this.setScoreboardKillDeathScore();
  }

  @Override
  public void clearArmor() {
    this.armor = this.getInventory().getArmorContents().clone();
    super.clearArmor();
  }

  public void restoreArmor() {
    this.getInventory().setArmorContents(this.armor);
  }

  @Override
  public void onGameJoin() {
    super.onGameJoin();

    this.setDefault();

    BowRunMap map = BowRunServer.getMap();

    this.setGravity(true);
    this.setInvulnerable(true);

    if (this.getTeam() != null && this.getTeam()
        .equals(BowRunServer.getGame().getRunnerTeam())) {
      this.lockLocation();
      this.setCollitionWithEntites(true);
    } else if (this.getTeam() != null && this.getTeam()
        .equals(BowRunServer.getGame().getArcherTeam())) {
      if (map.isArcherHover()) {
        Server.runTaskSynchrony(() -> {
          this.setAllowFlight(true);
          this.setFlying(true);
        }, GameBowRun.getPlugin());
      }

      if (!map.isArcherNoSpeed()) {
        this.setWalkSpeed((float) 0.6);
        this.setFlySpeed((float) 0.4);
      }
      this.setCollitionWithEntites(false);
    }

    this.teleportToTeamSpawn();
    this.setGameEquipment();
    this.loadGameSideboard();
  }

  public void startGame() {
    BowRunMap map = BowRunServer.getMap();
    if (this.getTeam() != null && this.getTeam()
        .equals(BowRunServer.getGame().getRunnerTeam())) {
      this.setWalkSpeed((float) 0.2);
      this.setFlySpeed((float) 0.2);
      this.unlockLocation();
      this.setInvulnerable(false);

      if (map.isRunnerSpeed()) {
        this.addPotionEffect(PotionEffectType.SPEED, 2);
      }
      if (map.isRunnerJump()) {
        this.addPotionEffect(PotionEffectType.JUMP, 9);
      }
      if (map.isRunnerHover()) {
        this.setGravity(false);
      }
      this.setArmor();
    }
  }

  private void setArmor() {
    if (BowRunServer.getRunnerArmor().get(0)) {
      this.getInventory().setBoots(BowRunServer.armor.get(0));
    }
    if (BowRunServer.getRunnerArmor().get(1)) {
      this.getInventory().setLeggings(BowRunServer.armor.get(1));
    }
    if (BowRunServer.getRunnerArmor().get(2)) {
      this.getInventory().setChestplate(BowRunServer.armor.get(2));
    }
    if (BowRunServer.getRunnerArmor().get(3)) {
      this.getInventory().setHelmet(BowRunServer.armor.get(3));
    }
  }

  @Override
  public void addKill() {
    super.addKill();
    this.playSound(BowRunServer.KILL_SOUND, 4F);
    this.setScoreboardKillDeathScore();
  }

  @Override
  public void setKills(Integer kills) {
    super.setKills(kills);
    this.setScoreboardKillDeathScore();
  }

  public void teleportToTeamSpawn() {
    this.getPlayer().setVelocity(new Vector(0, 0, 0));

    if (this.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
      int spawnNumber = (int) (Math.random() * BowRunServer.getMap().getRunnerSpawns()
          .size());
      this.teleport(BowRunServer.getMap().getRunnerSpawns().get(spawnNumber));
    } else if (this.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
      this.teleport(BowRunServer.getMap().getArcherSpawn());
    }
  }

  public void setGameEquipment() {
    if (this.getTeam() == null) {
      return;
    }
    if (this.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
      this.setArcherItems();
    } else if (this.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
      this.addItem(FOOD);
      this.setItem(DEATH);
      this.getInventory().setHelmet(ExItemStack.getLeatherArmor(Material.LEATHER_HELMET,
          BowRunServer.getGame().getRunnerTeam().getColor()));
    }
  }

  public void resetGameEquipment() {
    this.getInventory().clear();
    this.removePotionEffects();
  }

  public void setRespawnEquipment() {
    this.clearInventory();
    if (this.getTeam() == null) {
      return;
    }

    BowRunMap map = LoungeBridgeServer.getMap();

    if (this.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
      this.addItem(FOOD);
      this.setItem(DEATH);
      this.getInventory().setHelmet(ExItemStack.getLeatherArmor(Material.LEATHER_HELMET,
          BowRunServer.getGame().getRunnerTeam().getColor()));

      this.setArmor();

      if (!this.suicided) {
        if (!map.isRunnerNoSpecialItems()) {
          if (Math.random() < BowRunServer.getRunnerItemChance()) {
            this.addItem(BowRunServer.getRandomRunnerItem());
          }
        }
      } else {
        this.suicided = false;
      }

      Server.runTaskLaterSynchrony(() -> {
        if (map.isRunnerSpeed()) {
          this.addPotionEffect(PotionEffectType.SPEED, 1);
        }
        if (map.isRunnerJump()) {
          this.addPotionEffect(PotionEffectType.JUMP, 9);
        }
      }, 5, GameBowRun.getPlugin());

      if (map.isRunnerHover()) {
        this.setGravity(false);
      }
    } else if (this.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
      this.setArcherItems();
    }
  }

  public void setArcherItems() {
    BowRunMap map = LoungeBridgeServer.getMap();
    this.setItem(0, RUNNER_REMOVER);
    if (map.isOnlyInstant()) {
      this.setItem(1, INSTANT_BOW);
    } else if (map.isOnlyPunch()) {
      this.setItem(1, PUNCH_BOW);
    } else {
      this.setItem(1, BOW);
    }
    this.setItem(8, ARROW.cloneWithId().asQuantity(BowRunServer.MAX_ARROWS));
    this.getInventory().setHelmet(ExItemStack.getLeatherArmor(Material.LEATHER_HELMET,
        BowRunServer.getGame().getArcherTeam().getColor()));
  }

  public void setScoreboardKillDeathScore() {
    if (this.getTeam() == null) {
      return;
    }
    if (getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
      super.setSideboardScore(0, String.valueOf(this.kills));
    } else if (getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
      super.setSideboardScore(0, String.valueOf(this.deaths));
    }
  }

  public void loadGameSideboard() {
    if (this.getTeam() == null) {
      return;
    }
    if (this.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
      this.setSideboardScore(1, BowRunServer.SIDEBOARD_DEATHS_TEXT);
    } else if (this.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
      this.setSideboardScore(1, BowRunServer.SIDEBOARD_KILLS_TEXT);
    }
    this.setScoreboardKillDeathScore();
  }

  public boolean isSuicided() {
    return suicided;
  }

  public void suicided() {
    this.suicided = true;
  }
}
