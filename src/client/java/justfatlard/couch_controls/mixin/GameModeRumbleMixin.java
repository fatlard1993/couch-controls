package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.play.Rumble;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class GameModeRumbleMixin {
	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void couch_controls$blockBroken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) Rumble.blockBroken();
	}

	@Inject(method = "attack", at = @At("HEAD"))
	private void couch_controls$hitLanded(Player player, Entity target, CallbackInfo ci) {
		Rumble.hitLanded();
	}
}
