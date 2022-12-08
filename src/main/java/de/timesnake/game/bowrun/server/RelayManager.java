/*
 * workspace.game-bowrun.main
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

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.user.ExItemStack;
import de.timesnake.basic.bukkit.util.user.User;
import de.timesnake.basic.bukkit.util.user.event.UserDeathEvent;
import de.timesnake.basic.bukkit.util.user.event.UserDropItemEvent;
import de.timesnake.basic.bukkit.util.user.event.UserMoveEvent;
import de.timesnake.basic.game.util.user.TeamUser;
import de.timesnake.game.bowrun.main.GameBowRun;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

public class RelayManager implements Listener {

    public static final ExItemStack RELAY = new ExItemStack(Material.MOJANG_BANNER_PATTERN, "§6Relay");
    private static final int DELAY = 20 * 40;
    private static final int MAX_RELAYS = 3;
    private int relays = 0;
    private int counter = 0;

    public RelayManager() {
        Server.registerListener(this, GameBowRun.getPlugin());
    }

    public void reset() {
        this.relays = 0;
        this.counter = 0;
    }

    @EventHandler
    public void onUserDrop(UserDropItemEvent e) {
        ExItemStack item = ExItemStack.getItem(e.getItemStack(), true);

        if (!item.equals(RELAY)) {
            return;
        }

        Server.runTaskLaterSynchrony(() -> {
            if (!e.getItemDrop().isDead()) {
                e.getItemDrop().remove();
            }
        }, DELAY, GameBowRun.getPlugin());
    }

    @EventHandler
    public void onPlayerPickUp(PlayerAttemptPickupItemEvent e) {
        User user = Server.getUser(e.getPlayer());

        if (new ExItemStack(e.getItem().getItemStack()).equals(RELAY)) {
            if (user.contains(RELAY)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveFromWorldEvent e) {
        if (!e.getEntityType().equals(EntityType.DROPPED_ITEM)) {
            return;
        }

        Item item = (Item) e.getEntity();

        if (item.getItemStack() == null || item.getItemStack().getType().equals(Material.AIR)) {
            return;
        }

        if (new ExItemStack(item.getItemStack()).equals(RELAY)) {
            this.relays--;
        }
    }

    @EventHandler
    public void onUserMoveEvent(UserMoveEvent e) {

        TeamUser user = (TeamUser) e.getUser();

        if (!BowRunServer.getMap().isRelayRace()) {
            return;
        }

        if (user.getTeam() == null || !user.getTeam().equals(BowRunServer.getGame().getRunnerTeam())) {
            return;
        }

        if (!user.getLocation().getBlock().equals(BowRunServer.getMap().getRelayPickUp().getBlock())) {
            return;
        }

        if (this.relays >= MAX_RELAYS) {
            return;
        }

        if (user.contains(RELAY)) {
            return;
        }

        ExItemStack customRelay = RELAY.cloneWithId().addExEnchantment(Enchantment.DAMAGE_ALL, this.counter).hideAll();

        user.addItem(customRelay);
        this.relays++;
        this.counter++;
    }

    @EventHandler
    public void onUserDeath(UserDeathEvent e) {
        if (e.getUser().contains(RELAY)) {
            this.relays--;
        }
    }
}
