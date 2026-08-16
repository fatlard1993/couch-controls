package justfatlard.couch_controls;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class CouchControlsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Driver.init();

		// SDL hands out a real device handle; dropping the client without
		// closing it leaves the pad claimed until the process dies.
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Driver.shutdown());
	}
}
