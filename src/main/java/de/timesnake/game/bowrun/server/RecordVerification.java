package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.Server;
import de.timesnake.basic.bukkit.util.chat.Argument;
import de.timesnake.basic.bukkit.util.chat.CommandListener;
import de.timesnake.basic.bukkit.util.chat.Sender;
import de.timesnake.basic.bukkit.util.user.User;
import de.timesnake.basic.loungebridge.util.user.GameUser;
import de.timesnake.game.bowrun.chat.Plugin;
import de.timesnake.game.bowrun.main.GameBowRun;
import de.timesnake.library.basic.util.chat.ChatColor;
import de.timesnake.library.basic.util.cmd.Arguments;
import de.timesnake.library.basic.util.cmd.ExCommand;
import net.md_5.bungee.api.chat.ClickEvent;

import java.util.List;

public class RecordVerification implements CommandListener {

    public static final Integer MIN_RECORD_SIZE = 4;

    private Integer time;
    private User finisher;
    private BowRunMap map;

    private boolean rejected;

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

            if (gameUser.getTeam().equals(BowRunServer.getGame().getArcherTeam()) && gameUser.getBowShots() == 0) {
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
        Server.printText(Plugin.BOWRUN, "Send verification requests");
        for (User user : Server.getUsers()) {
            if (!user.hasPermission("game.bowrun.verify")) {
                continue;
            }

            user.sendClickableMessage(Server.getChatManager().getSenderPlugin(Plugin.BOWRUN) + ChatColor.WARNING + "§lVerify record, if it was legal", "/bowrun_verify", "Click to verify", ClickEvent.Action.RUN_COMMAND);
        }
    }

    public void sendRejectRequest() {
        Server.printText(Plugin.BOWRUN, "Send reject requests");
        for (User user : Server.getUsers()) {
            if (!user.hasPermission("game.bowrun.reject")) {
                continue;
            }

            user.sendClickableMessage(Server.getChatManager().getSenderPlugin(Plugin.BOWRUN) + ChatColor.WARNING + "§lReject record, if it was illegal", "/bowrun_reject", "Click to reject", ClickEvent.Action.RUN_COMMAND);
        }

        Server.runTaskLaterSynchrony(() -> {
            if (!this.rejected) {
                this.setRecord();
            }
        }, 20 * 10, GameBowRun.getPlugin());
    }

    public void setRecord() {
        this.map.setBestTime(time, this.finisher.getUniqueId());
        this.finisher.addCoins(BowRunServerManager.RECORD_COINS, true);
        this.time = null;
    }

    @Override
    public void onCommand(Sender sender, ExCommand<Sender, Argument> cmd, Arguments<Argument> args) {
        if (cmd.getName().equalsIgnoreCase("bowrun_verify")) {
            if (!sender.hasPermission("game.bowrun.verify", 2413)) {
                return;
            }

            if (this.time != null) {
                this.setRecord();
                sender.sendPluginMessage(ChatColor.WARNING + "Verified record");
                Server.printText(Plugin.BOWRUN, "Record verified by " + sender.getChatName());
            } else {
                sender.sendPluginMessage(ChatColor.WARNING + "Record already verified");
            }
        } else if (cmd.getName().equalsIgnoreCase("bowrun_reject")) {
            if (!sender.hasPermission("game.bowrun.reject", 2414)) {
                return;
            }

            if (!this.rejected) {
                this.rejected = true;
                sender.sendPluginMessage(ChatColor.WARNING + "Rejected record");
                Server.printText(Plugin.BOWRUN, "Record rejected by " + sender.getChatName());
            } else {
                sender.sendPluginMessage(ChatColor.WARNING + "Record already rejected");
            }
        }

    }

    @Override
    public List<String> getTabCompletion(ExCommand<Sender, Argument> cmd, Arguments<Argument> args) {
        return null;
    }
}
