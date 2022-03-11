package de.timesnake.game.bowrun.user;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.user.ExItemStack;
import de.timesnake.basic.bukkit.util.user.User;
import de.timesnake.basic.bukkit.util.user.UserDamage;
import de.timesnake.basic.bukkit.util.user.event.*;
import de.timesnake.basic.game.util.TeamUser;
import de.timesnake.basic.loungebridge.util.server.LoungeBridgeServer;
import de.timesnake.game.bowrun.main.GameBowRun;
import de.timesnake.game.bowrun.server.BowRunMap;
import de.timesnake.game.bowrun.server.BowRunServer;
import de.timesnake.game.bowrun.server.BowRunServerManager;
import de.timesnake.game.bowrun.server.RelayManager;
import de.timesnake.library.basic.util.Status;
import de.timesnake.library.basic.util.Tuple;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class UserManager implements Listener, UserInventoryInteractListener {

    private static final double WATER_DAMAGE = 2; // per sec
    private static final int ITEM_REMOVE_DELAY = 15 * 20; // in ticks

    private BukkitTask arrowGeneratorTask;

    public UserManager() {
        Server.registerListener(this, GameBowRun.getPlugin());
        Server.getInventoryEventManager().addInteractListener(this, BowRunUser.DEATH);

        this.runWaterDamage();
    }

    private void runWaterDamage() {
        Server.runTaskTimerSynchrony(() -> {
            if (BowRunServer.getMap() != null && BowRunServer.getMap().isRunnerWaterDamage()) {
                for (User user : Server.getInGameUsers()) {
                    if (user.getLocation().getBlock().getType().equals(Material.WATER)) {
                        user.getPlayer().damage(WATER_DAMAGE);
                    }
                }
            }
        }, 0, 10, GameBowRun.getPlugin());
    }

    public void runArrowGenerator(int periodInTicks) {
        this.arrowGeneratorTask = Server.runTaskTimerSynchrony(() -> {
            if (BowRunServer.getMap() != null) {
                for (TeamUser user : BowRunServer.getGame().getArcherTeam().getInGameUsers()) {
                    int delta = user.containsAtLeast(BowRunUser.ARROW, BowRunServer.MAX_ARROWS, true);
                    if (delta < 0) {
                        user.addItem(BowRunUser.ARROW.cloneWithId().asQuantity(Math.min(BowRunServer.RESPAWN_ARROW_AMOUNT, -delta)));
                    }
                }
            }
        }, 0, periodInTicks, GameBowRun.getPlugin());
    }

    public void cancelArrowGenerator() {
        if (this.arrowGeneratorTask != null) {
            this.arrowGeneratorTask.cancel();
        }
    }

    @EventHandler
    public void onUserDamageByUser(UserDamageByUserEvent e) {
        TeamUser user = ((TeamUser) e.getUser());
        TeamUser damager = (TeamUser) e.getUserDamager();

        if (e.getDamageCause().equals(EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            if (damager.getTeam() != null && damager.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
                e.setCancelled(true);
                e.setCancelDamage(true);
                user.setLastDamager(new UserDamage(user, damager, e.getDamageCause(), UserDamage.DamageType.INSTANT));
                user.kill();
            }
        }

        if (user.getTeam() != null && damager.getTeam() != null && user.getTeam().equals(damager.getTeam())) {
            e.setCancelled(true);
            e.setCancelDamage(true);
        }
    }

    @EventHandler
    public void onPotion(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }

        BowRunUser user = (BowRunUser) Server.getUser(((Player) e.getEntity()));

        if (user.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onUserDamage(UserDamageEvent e) {
        TeamUser user = (TeamUser) e.getUser();

        if (user.getTeam() == null) {
            return;
        }

        if (user.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
            e.getUser().getPlayer().setFireTicks(0);
            e.setCancelled(true);
        }
        // user fall damage in map with game rule
    }

    @EventHandler
    public void onPlayerPickUpArrow(PlayerPickupArrowEvent e) {
        e.getArrow().remove();
        e.setCancelled(true);
    }

    @EventHandler
    public void onUserMoveEvent(UserMoveEvent e) {

        BowRunUser user = (BowRunUser) e.getUser();

        if (user.getTeam() == null) {
            return;
        }

        if (user.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
            if (BowRunServer.getMap().isArcherHover()) {
                if (e.getFrom().getY() != e.getTo().getY()) {
                    e.setCancelled(true);
                }
            }

            if ((BowRunServer.getMap().isArcherBorder() || BowRunServer.getMap().isArcherKnockbackBorder()) && !e.getFrom().getBlock().equals(e.getTo().getBlock())) {
                if (BowRunServer.getMap().getArcherBorderLocs().contains(new Tuple<>(e.getTo().getBlockX(), e.getTo().getBlockZ()))) {
                    if (BowRunServer.getMap().isArcherBorder()) {
                        user.teleportToTeamSpawn();
                    } else if (BowRunServer.getMap().isArcherKnockbackBorder()) {
                        e.setCancelled(true);
                        Server.runTaskLaterSynchrony(() -> user.setVelocity(e.getFrom().toVector().subtract(e.getTo().toVector()).normalize().multiply(1)), 1, GameBowRun.getPlugin());
                    }
                }
            }
        }

        if (!BowRunServer.isGameRunning()) {
            if (e.getTo().getBlockY() < BowRunServer.getMap().getRunnerDeathHeight()) {
                user.teleportToTeamSpawn();
            }
            return;
        }

        if (!(user.getStatus().equals(Status.User.IN_GAME))) {
            return;
        }

        if (e.getTo().getBlockY() < BowRunServer.getMap().getRunnerDeathHeight()) {
            if (user.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
                user.kill();
            } else if (user.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
                user.teleportToTeamSpawn();
            }
        }

        if (user.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
            if (user.getLocation().getBlock().equals(BowRunServer.getMap().getRunnerFinish().getBlock())) {
                if (BowRunServer.getMap().isRelayRace()) {
                    if (user.contains(RelayManager.RELAY)) {
                        user.addCoins(BowRunServer.WIN_COINS, true);
                        BowRunServer.stopGame(BowRunServer.WinType.RUNNER_FINISH, user);
                    }
                } else {
                    user.addCoins(BowRunServer.WIN_COINS, true);
                    BowRunServer.stopGame(BowRunServer.WinType.RUNNER_FINISH, user);
                }
            }

        }

    }

    /**
     * Keeps the archer-user inventory
     *
     * @param e The PlayerDeathEvent called by Bukkit
     */
    @EventHandler
    public void onUserDeath(UserDeathEvent e) {
        e.getDrops().clear();
        e.setAutoRespawn(true);
    }

    /**
     * Sets the respawn location for user
     *
     * @param e The PlayerRespawnEvent called by Bukkit
     */
    @EventHandler
    public void onUserRespawn(UserRespawnEvent e) {
        User user = e.getUser();

        if (user.getStatus().equals(Status.User.IN_GAME)) {
            BowRunUser brUser = ((BowRunUser) user);
            brUser.clearInventory();
            brUser.setRespawnEquipment();

            if (brUser.getTeam() == null) {
                return;
            }

            if (brUser.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
                int random = (int) (Math.random() * ((BowRunMap) LoungeBridgeServer.getMap()).getRunnerSpawns().size());
                e.setRespawnLocation(((BowRunMap) LoungeBridgeServer.getMap()).getRunnerSpawns().get(random));

            } else if (brUser.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
                e.setRespawnLocation(((BowRunMap) LoungeBridgeServer.getMap()).getArcherSpawn());
            }
            brUser.getPlayer().setAbsorptionAmount(0);
        }
    }

    @EventHandler
    public void onPlayerDropItem(UserDropItemEvent e) {
        TeamUser user = (TeamUser) e.getUser();

        if (user.getTeam() == null || user.isService()) {
            return;
        }

        ExItemStack item = ExItemStack.getItem(e.getItemStack(), true);

        if (user.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
            e.setCancelled(true);
            e.getItemDrop().remove();
        } else if (user.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
            if (e.getItemDrop().getItemStack().equals(BowRunUser.DEATH)) {
                e.setCancelled(true);
            } else if (!item.equals(RelayManager.RELAY)) {
                Server.runTaskLaterSynchrony(() -> {
                    if (!e.getItemDrop().isDead()) e.getItemDrop().remove();
                }, ITEM_REMOVE_DELAY, GameBowRun.getPlugin());
            }
        }
    }

    @EventHandler
    public void onPlayerPickUpItem(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {

            BowRunUser user = ((BowRunUser) Server.getUser(((Player) e.getEntity())));

            if (user.getTeam() == null || user.isService()) {
                return;
            }

            if (user.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player) {
            TeamUser user = (TeamUser) Server.getUser(e.getEntity().getUniqueId());
            if (user.getTeam() == null) {
                return;
            }

            if (user.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
                e.setFoodLevel(20);
            }
        }
    }

    @Override
    public void onUserInventoryInteract(UserInventoryInteractEvent e) {
        BowRunUser user = ((BowRunUser) e.getUser());

        if (user.isService()) {
            return;
        }

        if (BowRunServerManager.getInstance().isGameRunning() && (e.getAction().equals(Action.LEFT_CLICK_AIR) || e.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
            if (user.getLastDamager() == null) {
                user.suicided();
            }
            user.kill();
        }
        e.setCancelled(true);
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }

        if ((e.getOldEffect() != null && e.getOldEffect().getType().equals(PotionEffectType.INVISIBILITY)) || (e.getNewEffect() != null && e.getNewEffect().getType().equals(PotionEffectType.INVISIBILITY))) {
            switch (e.getAction()) {
                case ADDED, CHANGED -> Server.getUser(((Player) e.getEntity())).clearArmor();
                case CLEARED, REMOVED -> ((BowRunUser) Server.getUser(((Player) e.getEntity()))).restoreArmor();
            }
        }

    }

    @EventHandler
    public void onProjectileLaunch(PlayerLaunchProjectileEvent e) {
        BowRunUser user = (BowRunUser) Server.getUser(e.getPlayer());

        if (!BowRunServer.getMap().isArcherBowNoGravity()) {
            return;
        }

        if (user.getTeam().equals(BowRunServer.getGame().getArcherTeam())) {
            Projectile projectile = e.getProjectile();
            projectile.setGravity(false);
        }
    }

}
