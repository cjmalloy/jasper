package jasper.component;

import jasper.domain.Ref;

public interface Messages {
	void updateRef(Ref ref, Ref existing);
	void disconnectUser(String username);
}
