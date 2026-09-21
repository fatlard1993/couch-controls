package justfatlard.couch_controls.ui;

import justfatlard.couch_controls.CouchControls;
import justfatlard.couch_controls.CouchSettings;
import justfatlard.couch_controls.CouchSettings.Number;
import justfatlard.couch_controls.CouchSettings.Toggle;
import justfatlard.couch_controls.play.Rumble;
import justfatlard.pandorical.client.api.ClientSettingsApi;
import justfatlard.pandorical.client.api.PandoricalClientApi;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * This mod's page in Pandorical's mod menu. The settings file stays the source of truth: each
 * setting reads it live and writes a change straight back.
 */
public final class PandoricalSettings {
	private PandoricalSettings() {}

	/**
	 * The installed Pandorical may predate its client settings API. Naming that API only inside
	 * {@link Page} keeps the failure to one class that is loaded here, where it can be caught.
	 */
	public static void register() {
		try {
			Page.register();
		} catch (LinkageError older) {
			CouchControls.LOGGER.warn("Pandorical predates client settings; they are set in config/couch-controls/settings.properties");
		}
	}

	private static final class Page {
		static void register() {
			ClientSettingsApi.Group group = PandoricalClientApi.settings().group(CouchControls.MOD_ID, "Couch Controls");

			number(group, Number.LOOK_SPEED, "Look speed", "How fast the right stick turns the camera, in percent", value -> {});
			toggle(group, Toggle.INVERT_LOOK, "Invert look", "Stick up looks down", on -> {});
			number(group, Number.POINTER_SPEED, "Pointer speed",
				"How fast the right stick moves the pointer in menus, in percent", value -> {});
			number(group, Number.LEFT_DEADZONE, "Left stick deadzone",
				"How far the left stick must move, in percent, before it counts; raise it if the stick drifts", value -> {});
			number(group, Number.RIGHT_DEADZONE, "Right stick deadzone",
				"How far the right stick must move, in percent, before it counts; raise it if the camera drifts", value -> {});
			toggle(group, Toggle.RUMBLE, "Rumble",
				"The controller shakes when you are hurt, land a hit, break a block, get a bite on the line, or an explosion goes off nearby",
				on -> {
					if (on) Rumble.sample();
					else Rumble.stop();
				});
			number(group, Number.RUMBLE_STRENGTH, "Rumble strength", "Percent of full", value -> Rumble.sample());
		}

		private static void toggle(ClientSettingsApi.Group group, Toggle toggle, String label, String description,
				Consumer<Boolean> then) {
			group.toggle(toggle.key, label, description, () -> CouchSettings.get(toggle), on -> {
				CouchSettings.set(toggle, on);
				then.accept(on);
			});
		}

		private static void number(ClientSettingsApi.Group group, Number number, String label, String description, IntConsumer then) {
			group.number(number.key, label, description, number.min, number.max, number.step,
				() -> CouchSettings.get(number), value -> {
					CouchSettings.set(number, value);
					then.accept(value);
				});
		}
	}
}
