package justfatlard.couch_controls.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Slot coordinates are stored relative to the container's top-left corner,
 * and only the screen knows where that corner ended up.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int couch_controls$getLeftPos();

	@Accessor("topPos")
	int couch_controls$getTopPos();
}
