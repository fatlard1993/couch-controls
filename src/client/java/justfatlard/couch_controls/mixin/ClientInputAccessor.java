package justfatlard.couch_controls.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface ClientInputAccessor {
	@Accessor("moveVector")
	void couch_controls$setMoveVector(Vec2 moveVector);
}
