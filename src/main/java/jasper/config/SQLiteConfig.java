package jasper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.sqlite.Function;
import org.sqlite.SQLiteConnection;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Registers custom SQLite functions that emulate PostgreSQL JSONB functions
 * used in native queries. Only active when the "sqlite" profile is enabled.
 *
 * Functions are registered on every new connection via HikariCP's connection
 * init SQL callback to ensure they survive connection recycling.
 */
@Configuration
@Profile("sqlite")
public class SQLiteConfig {
	private static final Logger logger = LoggerFactory.getLogger(SQLiteConfig.class);
	private static final ObjectMapper om = new ObjectMapper();

	@Autowired
	public void configureDataSource(DataSource dataSource) {
		if (dataSource instanceof HikariDataSource hikari) {
			// Set connection lifetime to infinite to prevent recycling (keeps UDFs registered)
			hikari.setMaxLifetime(0); // 0 = infinite lifetime
			hikari.setKeepaliveTime(0); // disable keepalive
		}
		try (var conn = dataSource.getConnection()) {
			registerFunctionsOnConnection(conn);
			logger.info("Registered custom SQLite functions for JSONB compatibility");
		} catch (SQLException e) {
			logger.error("Failed to register custom SQLite functions", e);
		}
	}

	/**
	 * Register all custom functions on the given connection.
	 */
	static void registerFunctionsOnConnection(Connection conn) throws SQLException {
		var sqliteConn = conn.unwrap(SQLiteConnection.class);
		registerJsonbExists(sqliteConn);
	}

	/**
	 * Registers jsonb_exists(json, key) function for SQLite.
	 * For JSON arrays: returns true if the array contains the key as a value.
	 * For JSON objects: returns true if the object has the key as a field name.
	 */
	private static void registerJsonbExists(SQLiteConnection conn) throws SQLException {
		Function.create(conn, "jsonb_exists", new Function() {
			@Override
			protected void xFunc() throws SQLException {
				if (args() < 2) {
					result(0);
					return;
				}
				var json = value_text(0);
				var key = value_text(1);
				if (json == null || key == null) {
					result(0);
					return;
				}
				try {
					var node = om.readTree(json);
					if (node.isArray()) {
						for (var elem : node) {
							if (elem.isTextual() && elem.asText().equals(key)) {
								result(1);
								return;
							}
						}
					} else if (node.isObject()) {
						if (node.has(key)) {
							result(1);
							return;
						}
					}
					result(0);
				} catch (Exception e) {
					result(0);
				}
			}
		});
	}
}
