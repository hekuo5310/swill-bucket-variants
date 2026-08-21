package com.hekuo.swillvariants.client;

import com.hekuo.swillvariants.SwillBucketBlockEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

public final class SwillBucketRenderer implements BlockEntityRenderer<SwillBucketBlockEntity> {
    private final BlockEntityRendererFactory.Context context;

    public SwillBucketRenderer(BlockEntityRendererFactory.Context context) { this.context = context; }

    @Override
    public void render(SwillBucketBlockEntity bucket, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertices, int light, int overlay) {
        int layer = 0;
        for (ItemStack stack : bucket.getRenderedFoods()) {
            if (stack.isEmpty()) continue;
            matrices.push();
            matrices.translate(.5, .15 + layer * .035, .5);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((layer * 137) % 360));
            matrices.scale(.42F, .42F, .42F);
            context.getItemRenderer().renderItem(stack, ModelTransformationMode.GROUND,
                    light, OverlayTexture.DEFAULT_UV, matrices, vertices, bucket.getWorld(), layer);
            matrices.pop();
            layer++;
        }
    }
}
