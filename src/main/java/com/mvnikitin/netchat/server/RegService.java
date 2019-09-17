package com.mvnikitin.netchat.server;

import java.sql.ResultSet;
import java.sql.SQLException;

public class RegService {
    public static boolean register(String nick, String username, String password)
            throws SQLException {
        boolean res = false;

        if (!checkIfExists(nick, username)) {
            String statement = String.format(
                    "INSERT INTO users (username, password, nick) " +
                            "VALUES ('%s', '%d', '%s')",
                    username, password.hashCode(), nick);

            if (DataService.executeStatement(statement) > 0)
                res = true;
        }

        return res;
    }

    private static boolean checkIfExists(String nick, String userName)
            throws SQLException {
        String query = String.format(
                "SELECT COUNT() FROM users WHERE username = '%s' OR nick = '%s'",
                userName, nick);
        ResultSet rs = DataService.getData(query);

        if (rs.next() && !rs.getString(1).equals("0")) {
            return true;
        } else
            return false;
    }
}
