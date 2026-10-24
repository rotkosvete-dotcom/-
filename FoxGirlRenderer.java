package foxgirl.client;

import foxgirl.FoxGirlEntity;
import foxgirl.FoxGirlMod;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class FoxGirlRenderer extends MobEntityRenderer<FoxGirlEntity, FoxGirlModel> {
	private static final Identifier TEXTURE = new Identifier(FoxGirlMod.MOD_ID, "textures/entity/foxgirl.png");

	public FoxGirlRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new FoxGirlModel(ctx.getResourceManager()), 0.4f);
	}

	@Override
	public Identifier getTexture(FoxGirlEntity entity) {
		return TEXTURE;
	}
}
