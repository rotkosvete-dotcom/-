package foxgirl.client;

import foxgirl.FoxGirlMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class FoxGirlClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(FoxGirlMod.FOX_GIRL, FoxGirlRenderer::new);
	}
}
