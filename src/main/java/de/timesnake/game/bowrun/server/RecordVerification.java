/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.chat.cmd.Argument;
import de.timesnake.basic.bukkit.util.chat.cmd.CommandListener;
import de.timesnake.basic.bukkit.util.chat.cmd.Completion;
import de.timesnake.basic.bukkit.util.chat.cmd.Sender;
import de.timesnake.basic.bukkit.util.user.User;
import de.timesnake.basic.loungebridge.util.user.GameUser;
import de.timesnake.game.bowrun.chat.Plugin;
import de.timesnake.game.bowrun.main.GameBowRun;
import de.timesnake.library.basic.util.Loggers;
import de.timesnake.library.chat.ExTextColor;
import de.timesnake.library.commands.PluginCommand;
import de.timesnake.library.commands.simple.Arguments;
import de.timesnake.library.extension.util.chat.Code;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;

public class RecordVerification implements CommandListener {

  public static final Integer MIN_RECORD_SIZE = 4;

  private Integer time;
  private User finisher;
  private BowRunMap map;

  private boolean rejected;

  private final Code verifyPerm = Plugin.BOWRUN.createPermssionCode("game.bowrun.verify");
  private final Code rejectPerm = Plugin.BOWRUN.createPermssionCode("game.bowrun.reject");

  public void checkRecord(int time, User finisher, BowRunMap map) {
    this.time = time;
    this.finisher = finisher;
    this.map = map;

    this.rejected = false;

    if (Server.getInOutGameUsers().size() < MIN_RECORD_SIZE) {
      this.sendVerificationRequest();
      return;
    }

    for (User user : Server.getInOutGameUsers()) {
      GameUser gameUser = ((GameUser) user);

      if (gameUser.getTeam() == null) {
        continue;
      }

      if (gameUser.getTeam().equals(BowRunServer.getGame().getArcherTeam())
          && gameUser.getBowShots() == 0) {
        this.sendVerificationRequest();
        return;
      }
    }

    if (BowRunServer.getGame().getArcherTeam().getKills() == 0) {
      this.sendVerificationRequest();
      return;
    }

    this.sendRejectRequest();
  }

  public void sendVerificationRequest() {
    Loggers.GAME.info("Send verification requests");
    for (User user : Server.getUsers()) {
      if (!user.hasPermission("game.bowrun.verify")) {
        continue;
      }

      user.sendClickablePluginMessage(Plugin.BOWRUN,
          Component.text("Verify ", ExTextColor.GREEN, TextDecoration.BOLD)
              .append(Component.text("record, if it was legal", ExTextColor.WARNING,
                  TextDecoration.BOLD)),
          "/bowrun_verify", Component.text("Click to verify"),
          ClickEvent.Action.RUN_COMMAND);
    }
  }

  public void sendRejectRequest() {
    Loggers.GAME.info("Send reject requests");
    for (User user : Server.getUsers()) {
      if (!user.hasPermission("game.bowrun.reject")) {
        continue;
      }

      user.sendClickablePluginMessage(Plugin.BOWRUN,
          Component.text("Reject record, if it was illegal", ExTextColor.WARNING,
              TextDecoration.BOLD),
          "/bowrun_reject", Component.text("Click to reject"),
          ClickEvent.Action.RUN_COMMAND);
    }

    Server.runTaskLaterSynchrony(() -> {
      if (!this.rejected) {
        this.setRecord();
        for (User user : Server.getUsers()) {
          if (!user.hasPermission("game.bowrun.reject")) {
            continue;
          }

          user.sendPluginMessage(Plugin.BOWRUN,
              Component.text("Saved verified record", ExTextColor.WARNING));
        }
      } else {
        for (User user : Server.getUsers()) {
          if (!user.hasPermission("game.bowrun.reject")) {
            continue;
          }

          user.sendPluginMessage(Plugin.BOWRUN,
              Component.text("Discarded unverified record", ExTextColor.WARNING));
        }
      }
    }, 20 * 8, GameBowRun.getPlugin());
  }

  public void setRecord() {
    this.map.setBestTime(time, this.finisher.getUniqueId());
    this.finisher.addCoins(BowRunServer.RECORD_COINS, true);
    this.time = null;
  }

  @Override
  public void onCommand(Sender sender, PluginCommand cmd,
                        Arguments<Argument> args) {
    if (cmd.getName().equalsIgnoreCase("bowrun_verify")) {
      if (!sender.hasPermission(this.verifyPerm)) {
        return;
      }

      if (this.time != null) {
        this.setRecord();
        sender.sendPluginMessage(Component.text("Verified record", ExTextColor.WARNING));
        Loggers.GAME.info("Record verified by " + sender.getChatName());
      } else {
        sender.sendPluginMessage(
            Component.text("Record already verified", ExTextColor.WARNING));
      }
    } else if (cmd.getName().equalsIgnoreCase("bowrun_reject")) {
      if (!sender.hasPermission(this.rejectPerm)) {
        return;
      }

      if (!this.rejected) {
        this.rejected = true;
        sender.sendPluginMessage(Component.text("Rejected record", ExTextColor.WARNING));
        Loggers.GAME.info("Record rejected by " + sender.getChatName());
      } else {
        sender.sendPluginMessage(
            Component.text("Record already rejected", ExTextColor.WARNING));
      }
    }

  }

  @Override
  public Completion getTabCompletion() {
    return new Completion(this.rejectPerm);
  }

  @Override
  public String getPermission() {
    return this.rejectPerm.getPermission();
  }
}
