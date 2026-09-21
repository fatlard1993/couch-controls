package justfatlard.couch_controls;

import justfatlard.couch_controls.input.PadBinds;
import justfatlard.couch_controls.play.HotbarCycle;
import justfatlard.couch_controls.play.Rumble;
import justfatlard.couch_controls.ui.PandoricalSettings;
import justfatlard.couch_controls.ui.ScreenKeyboard;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class CouchControlsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HotbarCycle.register();
		PadBinds.load();
		CouchSettings.load();
		Rumble.register();
		ScreenKeyboard.register();
		if (CouchControls.PANDORICAL_LOADED) PandoricalSettings.register();
		Driver.init();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Driver.shutdown());
	}
}
