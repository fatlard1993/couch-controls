package justfatlard.couch_controls;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CouchControls {
	public static final String MOD_ID = "couch-controls";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Pandorical is a soft dependency. Only the {@code Pandorical*} classes name its types, and
	 * each is reached only behind this flag, so a client without it never loads one. The flag
	 * says loaded, not which version: API newer than the oldest published Pandorical is probed
	 * before first use, as {@code PandoricalScroll.linked()} does.
	 */
	public static final boolean PANDORICAL_LOADED = FabricLoader.getInstance().isModLoaded("pandorical");

	private CouchControls() {}
}
