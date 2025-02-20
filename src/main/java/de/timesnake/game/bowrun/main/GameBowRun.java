/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.game.bowrun.main;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.ServerManager;
import de.timesnake.game.bowrun.server.BowRunServerManager;
import de.timesnake.library.chat.Plugin;
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
        BowRunServerManager.getInstance().getRecordVerification(), Plugin.GAME);
    Server.getCommandManager().addCommand(this, "bowrun_reject",
        BowRunServerManager.getInstance().getRecordVerification(), Plugin.GAME);
  }

  public static GameBowRun getPlugin() {
    return plugin;
  }
}
