package jasper.component;

import jasper.domain.Ref;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("no-websocket")
@Component
public class MessagesImplNop implements Messages {
	public void updateRef(Ref ref, Ref existing) { }

	@Override
	public void disconnectUser(String username) { }
}
