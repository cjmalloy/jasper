package jasper.component;

import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.api.jdbc.PGNotificationListener;
import jasper.repository.RefRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

@Component
public class Notifications implements PGNotificationListener {

	@Autowired
	RefRepository repository;

	@Autowired
	JdbcTemplate tpl;

	public List<Listener> refListeners = new ArrayList<>();
	public List<Listener> extListeners = new ArrayList<>();
	public List<Listener> userListeners = new ArrayList<>();
	public List<Listener> pluginListeners = new ArrayList<>();
	public List<Listener> templateListeners = new ArrayList<>();

	@PostConstruct
	void init() {
		tpl.execute((Connection c) -> {
			// TODO: try LISTEN *
			var stmt = c.createStatement();
			stmt.execute("LISTEN notifyRef");
			stmt.close();
			c.unwrap(PGConnection.class).addNotificationListener(this);
			return 0;
		});
		tpl.execute((Connection c) -> {
			// TODO: try LISTEN *
			c.createStatement().execute("LISTEN notifyExt");
//			extCon = c.unwrap(PGConnection.class);
			return 0;
		});
		tpl.execute((Connection c) -> {
			// TODO: try LISTEN *
			c.createStatement().execute("LISTEN notifyUser");
//			userCon = c.unwrap(PGConnection.class);
			return 0;
		});
		tpl.execute((Connection c) -> {
			// TODO: try LISTEN *
			c.createStatement().execute("LISTEN notifyPlugin");
//			pluginCon = c.unwrap(PGConnection.class);
			return 0;
		});
		tpl.execute((Connection c) -> {
			// TODO: try LISTEN *
			c.createStatement().execute("LISTEN notifyTemplate");
//			templateCon = c.unwrap(PGConnection.class);
			return 0;
		});
	}

	@Override
	public void notification(int processId, String channelName, String payload) {
		if (channelName.equals("notifyRef")) {
			for (var l : refListeners) l.doNotify();
		}
	}

	@Transactional
	public void notifyRef() {
		tpl.execute("NOTIFY notifyRef");
	}

	@Transactional
	public void notifyExt() {
		tpl.execute("NOTIFY notifyExt");
	}

	@Transactional
	public void notifyUser() {
		tpl.execute("NOTIFY notifyUser");
	}

	@Transactional
	public void notifyPlugin() {
		tpl.execute("NOTIFY notifyPlugin");
	}

	@Transactional
	public void notifyTemplate() {
		tpl.execute("NOTIFY notifyTemplate");
	}

	public void allRefListener(Listener l) {
		this.refListeners.add(l);
	}

	public interface Listener {
		void doNotify();
	}
}
