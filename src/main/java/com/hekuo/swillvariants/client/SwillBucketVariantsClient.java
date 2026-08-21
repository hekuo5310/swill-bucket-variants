package com.hekuo.swillvariants.client;

import com.hekuo.swillvariants.SwillBucketVariants;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class SwillBucketVariantsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockEntityRendererFactories.register(SwillBucketVariants.BLOCK_ENTITY, SwillBucketRenderer::new);
    }
}
