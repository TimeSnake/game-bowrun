package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.exceptions.UnsupportedGroupRankException;
import de.timesnake.basic.game.util.Game;
import de.timesnake.basic.game.util.Team;
import de.timesnake.basic.loungebridge.util.user.Kit;
import de.timesnake.database.util.game.DbGame;
import de.timesnake.database.util.game.DbKit;
import de.timesnake.database.util.game.DbMap;
import de.timesnake.database.util.game.DbTeam;

public class BowRunGame extends Game {

    public static final String RUNNER_TEAM_NAME = "runner";
    public static final String ARCHER_TEAM_NAME = "archer";

    public BowRunGame(DbGame game, boolean loadWorlds) {
        super(game, loadWorlds);
    }

    @Override
    public Team loadTeam(DbTeam team) throws UnsupportedGroupRankException {
        return new Team(team);
    }

    @Override
    public BowRunMap loadMap(DbMap dbMap, boolean loadWorld) {
        return new BowRunMap(dbMap);
    }

    @Override
    public Kit loadKit(DbKit dbKit) {
        return new Kit(dbKit, null);
    }

    public Team getRunnerTeam() {
        return super.getTeam(RUNNER_TEAM_NAME);
    }

    public Team getArcherTeam() {
        return super.getTeam(ARCHER_TEAM_NAME);
    }
}
