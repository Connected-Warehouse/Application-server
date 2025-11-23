package io.absolutwarehouse.manager;

import io.absolutwarehouse.config.ServerConfig;

import java.sql.*;
import java.util.*;

/**
 * DatabaseManager pour PostgreSQL
 * - Fournit un mini query builder : SELECT / UPDATE / DELETE / INSERT
 */
public class DatabaseManager {

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        try {
            Class.forName("org.postgresql.Driver");

            String url = String.format(
                    "jdbc:postgresql://%s:%d/%s", 
                    ServerConfig.DB_HOSTNAME,
                    ServerConfig.DB_PORT,
                    ServerConfig.DB_NAME
            );

            connection = DriverManager.getConnection(
                    url,
                    ServerConfig.DB_USERNAME,
                    ServerConfig.DB_PASSWORD
            );

            System.out.println("[DatabaseManager] ✅ Connexion PostgreSQL établie !");
        } catch (Exception e) {
            System.err.println("[DatabaseManager] ❌ Erreur : " + e.getMessage());
        }
    }

    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    // ======================================================
    // ===  QUERY BUILDER : SELECT / UPDATE / DELETE / INSERT
    // ======================================================

    /* ---------- SELECT ---------- */
    public Query from(String table) { return new Query(table); }

    public static class Query {
        private final String table;
        private final List<String> columns = new ArrayList<>();
        private final List<String> joins = new ArrayList<>();
        private final List<String> conditions = new ArrayList<>();
        private final List<Object> parameters = new ArrayList<>();

        public Query(String table) { this.table = table; }

        public Query select(String... cols) {
            columns.addAll(Arrays.asList(cols));
            return this;
        }

        public Query join(String joinType, String otherTable, String onClause) {
            joins.add(joinType + " JOIN " + otherTable + " ON " + onClause);
            return this;
        }

        public Query where(String condition, Object... values) {
            conditions.add(condition);
            parameters.addAll(Arrays.asList(values));
            return this;
        }

        public ResultSet execute() throws SQLException {
            String sql = buildQuery();
            PreparedStatement stmt = DatabaseManager.getInstance()
                    .getConnection()
                    .prepareStatement(sql);
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }
            System.out.println("[SQL] " + sql);
            return stmt.executeQuery();
        }

        private String buildQuery() {
            StringBuilder sb = new StringBuilder("SELECT ");
            sb.append(columns.isEmpty() ? "*" : String.join(", ", columns))
                    .append(" FROM ").append(table);

            if (!joins.isEmpty()) sb.append(" ").append(String.join(" ", joins));
            if (!conditions.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", conditions));
            return sb.toString();
        }
    }

    /* ---------- UPDATE ---------- */
    public UpdateQuery update(String table) { return new UpdateQuery(table); }

    public static class UpdateQuery {
        private final String table;
        private final Map<String, Object> updates = new LinkedHashMap<>();
        private final List<String> conditions = new ArrayList<>();
        private final List<Object> parameters = new ArrayList<>();

        public UpdateQuery(String table) { this.table = table; }

        public UpdateQuery set(String column, Object value) {
            updates.put(column, value);
            return this;
        }

        public UpdateQuery where(String condition, Object... values) {
            conditions.add(condition);
            parameters.addAll(Arrays.asList(values));
            return this;
        }

        public int execute() throws SQLException {
            StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
            sql.append(String.join(", ",
                    updates.keySet().stream().map(c -> c + " = ?").toList()));

            if (!conditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", conditions));
            }

            PreparedStatement stmt = DatabaseManager.getInstance()
                    .getConnection()
                    .prepareStatement(sql.toString());

            int idx = 1;
            for (Object value : updates.values()) stmt.setObject(idx++, value);
            for (Object param : parameters) stmt.setObject(idx++, param);

            System.out.println("[SQL] " + sql);
            return stmt.executeUpdate();
        }
    }

    /* ---------- DELETE ---------- */
    public DeleteQuery deleteFrom(String table) { return new DeleteQuery(table); }

    public static class DeleteQuery {
        private final String table;
        private final List<String> conditions = new ArrayList<>();
        private final List<Object> parameters = new ArrayList<>();

        public DeleteQuery(String table) { this.table = table; }

        public DeleteQuery where(String condition, Object... values) {
            conditions.add(condition);
            parameters.addAll(Arrays.asList(values));
            return this;
        }

        public int execute() throws SQLException {
            StringBuilder sql = new StringBuilder("DELETE FROM ").append(table);
            if (!conditions.isEmpty())
                sql.append(" WHERE ").append(String.join(" AND ", conditions));

            PreparedStatement stmt = DatabaseManager.getInstance()
                    .getConnection()
                    .prepareStatement(sql.toString());

            for (int i = 0; i < parameters.size(); i++)
                stmt.setObject(i + 1, parameters.get(i));

            System.out.println("[SQL] " + sql);
            return stmt.executeUpdate();
        }
    }

    /* ---------- INSERT ---------- */
    public int insert(String table, Map<String, Object> values) throws SQLException {
        StringJoiner cols = new StringJoiner(", ");
        StringJoiner qs = new StringJoiner(", ");
        for (String c : values.keySet()) {
            cols.add(c);
            qs.add("?");
        }

        String sql = "INSERT INTO " + table + " (" + cols + ") VALUES (" + qs + ")";
        PreparedStatement stmt = connection.prepareStatement(sql);

        int i = 1;
        for (Object v : values.values()) stmt.setObject(i++, v);

        System.out.println("[SQL] " + sql);
        return stmt.executeUpdate();
    }


    public long insertAndGetId(String table, Map<String, Object> values, String idColumn) throws SQLException {
        StringJoiner cols = new StringJoiner(", ");
        StringJoiner qs = new StringJoiner(", ");
        for (String c : values.keySet()) {
            cols.add(c);
            qs.add("?");
        }

        String sql = "INSERT INTO " + table + " (" + cols + ") VALUES (" + qs + ") RETURNING " + idColumn;
        PreparedStatement stmt = connection.prepareStatement(sql);

        int i = 1;
        for (Object v : values.values()) stmt.setObject(i++, v);

        System.out.println("[SQL] " + sql);

        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            long id = rs.getLong(1);
            rs.close();
            stmt.close();
            return id;
        } else {
            rs.close();
            stmt.close();
            throw new SQLException("Impossible de récupérer l'ID généré");
        }
    }



    public static void close(ResultSet rs, Statement stmt) {
        try {
            if (rs != null) rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            if (stmt != null) stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
