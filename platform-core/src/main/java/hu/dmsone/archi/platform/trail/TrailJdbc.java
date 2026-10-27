package hu.dmsone.archi.platform.trail;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL utasítások időmérése a trailhez: a DataSource kapcsolatait és utasításait proxyval burkolja, és minden
 * végrehajtást {@link TrailKind#SQL} lépésként rögzít (adatbázis neve, normalizált művelet, időtartam).
 * Csak aktív {@link TrailContext} esetén mér, egyébként semmit nem csinál.
 */
public final class TrailJdbc {

    private static final Set<String> EXECUTE = Set.of("execute", "executeQuery", "executeUpdate", "executeLargeUpdate",
            "executeBatch", "executeLargeBatch");
    private static final Pattern TABLE = Pattern.compile(
            "^\\s*(?:with\\b.*?\\)\\s*)?(select|insert|update|delete|merge)\\b.*?(?:\\bfrom|\\binto|\\bupdate)?\\s+([a-z_][\\w.\"]*)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FROM = Pattern.compile("\\b(from|into|update|join)\\s+([a-z_][\\w.\"]*)",
            Pattern.CASE_INSENSITIVE);

    private TrailJdbc() { }

    public static DataSource wrap(DataSource target, String database, TrailRecorder recorder) {
        return recorder == null || !recorder.enabled() ? target : new Wrapped(target, database, recorder);
    }

    /** {@code INSERT irat.kuldemeny}, {@code SELECT audit.audit_event} stb. */
    static String operation(String sql) {
        if (sql == null) {
            return "SQL";
        }
        String s = sql.strip();
        String verb = s.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        if (verb.equals("WITH")) {
            Matcher m = TABLE.matcher(s);
            verb = m.find() ? m.group(1).toUpperCase(Locale.ROOT) : verb;
        }
        String table = null;
        Matcher m = FROM.matcher(s);
        while (m.find()) {
            String kw = m.group(1).toLowerCase(Locale.ROOT);
            boolean match = switch (verb) {
                case "INSERT" -> kw.equals("into");
                case "UPDATE" -> kw.equals("update");
                default -> kw.equals("from");
            };
            if (match) {
                table = m.group(2).replace("\"", "");
                break;
            }
        }
        return table == null ? verb : verb + " " + table.toLowerCase(Locale.ROOT);
    }

    private static final class Wrapped implements DataSource {
        private final DataSource target;
        private final String database;
        private final TrailRecorder recorder;

        Wrapped(DataSource target, String database, TrailRecorder recorder) {
            this.target = target;
            this.database = database;
            this.recorder = recorder;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return connection(target.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return connection(target.getConnection(username, password));
        }

        private Connection connection(Connection c) {
            return (Connection) Proxy.newProxyInstance(TrailJdbc.class.getClassLoader(), new Class<?>[]{Connection.class},
                    new ConnectionHandler(c, database, recorder));
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return target.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            target.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            target.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return target.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return target.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return iface.isInstance(this) ? iface.cast(this) : target.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || target.isWrapperFor(iface);
        }
    }

    private record ConnectionHandler(Connection target, String database, TrailRecorder recorder) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("unwrap") && args != null && args[0] instanceof Class<?> c && c.isInstance(proxy)) {
                return proxy;
            }
            Object result = invokeTarget(target, method, args);
            String name = method.getName();
            if (result instanceof Statement st && (name.equals("prepareStatement") || name.equals("prepareCall")
                    || name.equals("createStatement"))) {
                String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : null;
                Class<?> iface = st instanceof CallableStatement ? CallableStatement.class
                        : st instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                return Proxy.newProxyInstance(TrailJdbc.class.getClassLoader(), new Class<?>[]{iface},
                        new StatementHandler(st, sql, database, recorder));
            }
            return result;
        }
    }

    private record StatementHandler(Statement target, String sql, String database, TrailRecorder recorder)
            implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("unwrap") && args != null && args[0] instanceof Class<?> c && c.isInstance(proxy)) {
                return proxy;
            }
            if (!EXECUTE.contains(method.getName()) || TrailContext.get() == null) {
                return invokeTarget(target, method, args);
            }
            String text = args != null && args.length > 0 && args[0] instanceof String s ? s : sql;
            TrailRecorder.Handle h = recorder.beginNested();
            try {
                Object r = invokeTarget(target, method, args);
                recorder.end(h, TrailRecorder.Spec.of(TrailKind.SQL, operation(text), database), "OK", shorten(text));
                return r;
            } catch (Throwable e) {
                recorder.end(h, TrailRecorder.Spec.of(TrailKind.SQL, operation(text), database), "ERROR",
                        TrailRecorder.errorText(e));
                throw e;
            }
        }
    }

    private static String shorten(String sql) {
        if (sql == null) {
            return null;
        }
        String s = sql.replaceAll("\\s+", " ").strip();
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }

    private static Object invokeTarget(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
