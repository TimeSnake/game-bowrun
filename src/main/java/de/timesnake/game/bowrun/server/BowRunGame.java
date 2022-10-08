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

package de.timesnake.game.bowrun.server;

import de.timesnake.basic.bukkit.util.exceptions.UnsupportedGroupRankException;
import de.timesnake.basic.game.util.Team;
import de.timesnake.basic.game.util.TmpGame;
import de.timesnake.basic.loungebridge.util.user.Kit;
import de.timesnake.database.util.game.DbKit;
import de.timesnake.database.util.game.DbMap;
import de.timesnake.database.util.game.DbTeam;
import de.timesnake.database.util.game.DbTmpGame;

public class BowRunGame extends TmpGame {

    public static final String RUNNER_TEAM_NAME = "runner";
    public static final String ARCHER_TEAM_NAME = "archer";

    public BowRunGame(DbTmpGame game, boolean loadWorlds) {
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
