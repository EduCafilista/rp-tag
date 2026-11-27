package dev.rptag.vampire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Morceguinho fofo: cabeça grande e redonda, orelhas pontudas, olhões, dois dentinhos, corpo pequeno,
 * asinhas largas e pezinhos. Textura 64x32 (textures/entity/vampire_bat.png).
 */
public final class BatCuteModel {
    private final ModelPart root, head, body, wingL, wingR;

    public BatCuteModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.wingL = body.getChild("wing_l");
        this.wingR = body.getChild("wing_r");
    }

    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();

        PartDefinition head = r.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -3f, -3f, 8f, 6f, 6f), PartPose.offset(0f, 0f, 0f));
        head.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(28, 0).addBox(-1f, -4f, -0.5f, 2f, 4f, 1f),
                PartPose.offsetAndRotation(2.5f, -2.6f, 0f, 0f, 0f, 0.25f));
        head.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(28, 0).mirror().addBox(-1f, -4f, -0.5f, 2f, 4f, 1f),
                PartPose.offsetAndRotation(-2.5f, -2.6f, 0f, 0f, 0f, -0.25f));
        head.addOrReplaceChild("fang_l", CubeListBuilder.create().texOffs(38, 0).addBox(-0.5f, 0f, -0.5f, 1f, 1.2f, 1f),
                PartPose.offset(1.4f, 2.9f, -2.6f));
        head.addOrReplaceChild("fang_r", CubeListBuilder.create().texOffs(38, 0).addBox(-0.5f, 0f, -0.5f, 1f, 1.2f, 1f),
                PartPose.offset(-1.4f, 2.9f, -2.6f));

        PartDefinition body = r.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 13).addBox(-2.5f, 0f, -2f, 5f, 5f, 4f), PartPose.offset(0f, 3f, 0f));
        body.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(0, 23).addBox(0f, 0f, -0.5f, 9f, 6f, 1f),
                PartPose.offset(2.3f, 0.6f, 0.8f));
        body.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(0, 23).mirror().addBox(-9f, 0f, -0.5f, 9f, 6f, 1f),
                PartPose.offset(-2.3f, 0.6f, 0.8f));
        body.addOrReplaceChild("foot_l", CubeListBuilder.create().texOffs(34, 0).addBox(-0.5f, 0f, -0.5f, 1f, 2f, 1f),
                PartPose.offset(1f, 5f, 0f));
        body.addOrReplaceChild("foot_r", CubeListBuilder.create().texOffs(34, 0).addBox(-0.5f, 0f, -0.5f, 1f, 2f, 1f),
                PartPose.offset(-1f, 5f, 0f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** age em ticks (com parcial); fast = bate asas rápido (voando/andando). */
    public void setup(float age, boolean fast, float headYawRad, float headPitchRad) {
        float flap = fast ? Mth.sin(age * 0.95f) * 0.85f : Mth.sin(age * 0.22f) * 0.18f;
        wingL.zRot = 0.35f + flap;
        wingR.zRot = -(0.35f + flap);
        wingL.yRot = -0.25f;  wingR.yRot = 0.25f;
        body.xRot = 0.35f + Mth.sin(age * 0.3f) * 0.04f;              // levemente inclinado para frente
        head.yRot = Mth.clamp(headYawRad, -0.9f, 0.9f);
        head.xRot = Mth.clamp(headPitchRad, -0.7f, 0.7f);
        head.y = Mth.sin(age * 0.3f) * 0.35f;                          // balanço bobo
    }

    public void render(PoseStack ps, VertexConsumer vc, int light, int overlay) {
        root.render(ps, vc, light, overlay, 0xFFFFFFFF);
    }
}
