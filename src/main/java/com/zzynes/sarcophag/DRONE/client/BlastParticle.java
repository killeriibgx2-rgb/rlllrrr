package com.zzynes.sarcophag.DRONE.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class BlastParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final Style style;
    private final float baseSize;
    private final float speedScale;

    private BlastParticle(ClientLevel level, double x, double y, double z, double xs, double ys, double zs, SpriteSet sprites, Style style) {
        super(level, x, y, z, xs, ys, zs);
        this.sprites = sprites;
        this.style = style;
        this.speedScale = (float) Math.sqrt(xs * xs + ys * ys + zs * zs);
        this.xd = xs;
        this.yd = ys;
        this.zd = zs;
        this.hasPhysics = false;
        this.friction = 0.92f;
        this.gravity = style == Style.SMOKE ? -0.018f : 0.0f;
        this.lifetime = switch (style) {
            case CORE -> 14 + this.random.nextInt(5);
            case FLARE -> 10 + this.random.nextInt(8);
            case SMOKE -> 48 + this.random.nextInt(32);
            case RING -> 12 + this.random.nextInt(4);
            case SPARK -> 9 + this.random.nextInt(8);
        };
        this.baseSize = switch (style) {
            case CORE -> 1.55f + this.random.nextFloat() * 1.1f;
            case FLARE -> 0.22f + this.random.nextFloat() * 0.28f;
            case SMOKE -> 0.75f + this.random.nextFloat() * 1.1f;
            case RING -> 0.55f + this.random.nextFloat() * 0.45f;
            case SPARK -> 0.08f + this.random.nextFloat() * 0.08f;
        };
        this.quadSize = this.baseSize;
        this.setSpriteFromAge(sprites);
        this.selectStartColor();
    }

    private void selectStartColor() {
        switch (style) {
            case CORE -> this.setColor(1.0f, 0.72f, 0.30f);
            case FLARE -> this.setColor(1.0f, 0.38f, 0.055f);
            case SMOKE -> this.setColor(0.44f, 0.46f, 0.49f);
            case RING -> this.setColor(0.76f, 0.80f, 0.82f);
            case SPARK -> this.setColor(1.0f, 0.54f, 0.10f);
        }
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        float t = this.age / (float) this.lifetime;
        this.move(this.xd, this.yd, this.zd);
        float damp = style == Style.SMOKE ? 0.965f : style == Style.SPARK ? 0.90f : 0.88f;
        this.xd *= damp;
        this.yd = this.yd * damp + (style == Style.SMOKE ? 0.0018 : 0.0);
        this.zd *= damp;

        switch (style) {
            case CORE -> {
                float expansion = 1.0f + 1.75f * (1.0f - (1.0f - t) * (1.0f - t));
                this.quadSize = this.baseSize * expansion;
                float red = Mth.lerp(t, 1.0f, 0.20f);
                float green = Mth.lerp(t, 0.52f, 0.025f);
                float blue = Mth.lerp(t, 0.12f, 0.015f);
                this.setColor(red, green, blue);
                this.setAlpha(0.97f * (1.0f - t) * (1.0f - t));
            }
            case FLARE -> {
                this.quadSize = this.baseSize * (1.0f + 2.4f * t);
                this.setColor(1.0f, Mth.lerp(t, 0.60f, 0.055f), Mth.lerp(t, 0.11f, 0.018f));
                this.setAlpha(0.88f * (1.0f - t));
            }
            case SMOKE -> {
                this.quadSize = this.baseSize * (1.0f + 3.4f * t);
                float gray = Mth.lerp(t, 0.27f, 0.62f);
                this.setColor(gray, gray * 1.015f, gray * 1.04f);
                this.setAlpha(0.76f * (1.0f - t) * (1.0f - t));
            }
            case RING -> {
                this.quadSize = this.baseSize * (1.0f + 15.0f * t) * (0.9f + speedScale * 0.12f);
                this.setColor(Mth.lerp(t, 0.86f, 0.48f), Mth.lerp(t, 0.88f, 0.53f), Mth.lerp(t, 0.90f, 0.58f));
                this.setAlpha(0.82f * (1.0f - t));
            }
            case SPARK -> {
                this.quadSize = this.baseSize * (1.0f - 0.48f * t);
                this.setColor(1.0f, Mth.lerp(t, 0.67f, 0.18f), Mth.lerp(t, 0.12f, 0.015f));
                this.setAlpha(0.98f * (1.0f - t));
            }
        }
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public enum Style {
        CORE,
        FLARE,
        SMOKE,
        RING,
        SPARK
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;
        private final Style style;

        public Provider(SpriteSet sprites, Style style) {
            this.sprites = sprites;
            this.style = style;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xs, double ys, double zs) {
            return new BlastParticle(level, x, y, z, xs, ys, zs, this.sprites, this.style);
        }
    }
}
