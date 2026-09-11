package justfatlard.couch_controls;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class CouchControlsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Driver.init();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Driver.shutdown());
	}
}
