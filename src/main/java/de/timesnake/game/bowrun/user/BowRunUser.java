package de.timesnake.game.bowrun.user;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.user.ExItemStack;
import de.timesnake.basic.loungebridge.util.server.LoungeBridgeServer;
import de.timesnake.basic.loungebridge.util.user.GameUser;
import de.timesnake.game.bowrun.main.GameBowRun;
import de.timesnake.game.bowrun.server.BowRunMap;
import de.timesnake.game.bowrun.server.BowRunServer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

public class BowRunUser extends GameUser {

    public static final ItemStack RUNNER_REMOVER = new ExItemStack(Material.NETHERITE_SWORD, "§6RunnerRemover", true, List.of(Enchantment.DAMAGE_ALL, Enchantment.SWEEPING_EDGE), List.of(10, 10));
    public static final ExItemStack BOW = new ExItemStack(Material.BOW, true);
    public static final ExItemStack FOOD = new ExItemStack(Material.COOKED_BEEF, 32);
    public static final ExItemStack ARROW = new ExItemStack(Material.ARROW);
    public static final ExItemStack DEATH = new ExItemStack(8, Material.RED_DYE, "§cSuicide (no special item)");

    public static final ExItemStack INSTANT_BOW = new ExItemStack(Material.BOW, "§6Instant-Bow", true, List.of(Enchantment.ARROW_DAMAGE), List.of(10));

    public static final ExItemStack PUNCH_BOW = new ExItemStack(Material.BOW, "§6Punch-Bow", true, List.of(Enchantment.ARROW_KNOCKBACK), List.of(5));

    private ItemStack[] armor;

    private boolean suicided;

    public BowRunUser(Player player) {
        super(player);
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
    public void joinGame() {
        this.setDefault();

        BowRunMap map = BowRunServer.getMap();

        this.getPlayer().setGravity(true);
        this.getPlayer().setInvulnerable(true);

        if (this.getTeam() != null && this.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
            this.lockLocation(true);
        } else if (this.getTeam() != null && this.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
            if (map.isArcherHover()) {
                Server.runTaskSynchrony(() -> {
                    this.setAllowFlight(true);
                    this.setFlying(true);
                }, GameBowRun.getPlugin());
            }

            if (!map.isArcherNoSpeed()) {
                this.getPlayer().setWalkSpeed((float) 0.6);
                this.getPlayer().setFlySpeed((float) 0.4);
            }
        }

        this.teleportToTeamSpawn();
        this.setGameEquipment();
        this.loadGameSideboard();
    }

    public void startGame() {
        BowRunMap map = BowRunServer.getMap();
        if (this.getTeam() != null && this.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
            this.getPlayer().setWalkSpeed((float) 0.2);
            this.getPlayer().setFlySpeed((float) 0.2);
            this.lockLocation(false);
            this.getPlayer().setInvulnerable(false);

            if (map.isRunnerSpeed()) {
                this.addPotionEffect(PotionEffectType.SPEED, 2);
            }
            if (map.isRunnerJump()) {
                this.addPotionEffect(PotionEffectType.JUMP, 9);
            }
            if (map.isRunnerHover()) {
                this.getPlayer().setGravity(false);
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
            int spawnNumber = (int) (Math.random() * BowRunServer.getMap().getRunnerSpawns().size());
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
            this.getInventory().setHelmet(new ExItemStack(Material.LEATHER_HELMET, BowRunServer.getGame().getRunnerTeam().getColor()));
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
            this.getInventory().setHelmet(new ExItemStack(Material.LEATHER_HELMET, BowRunServer.getGame().getRunnerTeam().getColor()));

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
                this.getPlayer().setGravity(false);
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
        this.getInventory().setHelmet(new ExItemStack(Material.LEATHER_HELMET, BowRunServer.getGame().getArcherTeam().getColor()));
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
        this.setSideboard(BowRunServer.getGameSideboard());
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
