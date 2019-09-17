package com.mvnikitin.netchat.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BlackList {

    private Set<String> blacklist;
    private String owner;

    public BlackList(String ownerNick) {
        owner = ownerNick;
        blacklist = Collections.synchronizedSet(new HashSet<>());

        String qwery = String.format(
                "SELECT u2.nick as 'blocked_user' from blacklist " +
                        "inner join users as 'u1' on blacklist_owner_id = u1.id " +
                        "inner join users as 'u2' on blocked_user_id = u2.id " +
                        "where u1.nick = '%s'", ownerNick);

        try {
            ResultSet rs = DataService.getData(qwery);
            while (rs.next()) {
                blacklist.add(rs.getString(1));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Unable to populate the blacklist for user: " + owner, e);
        }
    }

    public boolean addToBlackList(String nick) {
        boolean res = false;

        String statement = String.format(
                "INSERT INTO blacklist (blacklist_owner_id, blocked_user_id) " +
                        "SELECT u1.id, u2.id FROM users as 'u1', users as 'u2' " +
                        "WHERE u1.nick = '%s' and u2.nick = '%s'", owner, nick);

        try {
            if (DataService.executeStatement(statement) > 0) {
                blacklist.add(nick);
                res = true;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Unable to add in the " + owner +
                            " blacklist the user: " + nick, e);
        }

        return res;
    }

    public boolean removeFromBlackList(String nick) {
        boolean res = false;

        String statement = String.format(
                "DELETE FROM blacklist WHERE blacklist_owner_id IN " +
                        "(SELECT id FROM users WHERE nick = '%s') AND " +
                        "blocked_user_id IN " +
                        "(SELECT id FROM users WHERE nick = '%s')",
                owner, nick);

        try {
            if (DataService.executeStatement(statement) > 0) {
                blacklist.remove(nick);
                res = true;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Unable to remove from the " + owner +
                            " blacklist the user: " + nick, e);
        }

        return res;
    }

    public boolean isInBlackList(String nick) {
        return blacklist.contains(nick);
    }
}
