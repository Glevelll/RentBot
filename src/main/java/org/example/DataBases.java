package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DataBases {
    private static final String CONNECTION_STRING = "jdbc:sqlite:/Users/Глеб/IdeaProjects/RentBot/RentBot.db";

    private final Connection connection;

    public DataBases() throws SQLException {
        this.connection = DriverManager.getConnection(CONNECTION_STRING);
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        Statement statement = connection.createStatement();
        return statement.executeQuery(sql);
    }

    public int executeUpdate(String sql) throws SQLException {
        Statement statement = connection.createStatement();
        return statement.executeUpdate(sql);
    }

    public Connection getConnection() {
        return connection;
    }
}
