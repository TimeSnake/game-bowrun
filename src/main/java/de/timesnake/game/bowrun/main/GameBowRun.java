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

package de.timesnake.game.bowrun.main;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.ServerManager;
import de.timesnake.game.bowrun.chat.Plugin;
import de.timesnake.game.bowrun.server.BowRunServerManager;
import org.bukkit.plugin.java.JavaPlugin;

public class GameBowRun extends JavaPlugin {

    private static GameBowRun plugin;

    @Override
    public void onLoad() {
        ServerManager.setInstance(new BowRunServerManager());
    }

    @Override
    public void onEnable() {
        GameBowRun.plugin = this;

        BowRunServerManager.getInstance().onBowRunEnable();

        Server.getCommandManager().addCommand(this, "bowrun_verify",
                BowRunServerManager.getInstance().getRecordVerification(), Plugin.BOWRUN);
        Server.getCommandManager().addCommand(this, "bowrun_reject",
                BowRunServerManager.getInstance().getRecordVerification(), Plugin.BOWRUN);
    }

    public static GameBowRun getPlugin() {
        return plugin;
    }
}
