package net.mehvahdjukaar.courier_owls.owls.client.models;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;


public class OwlMeshes {
    private static final float TAIL_REST = 0.4363F;
    private static final float WING_RAKE = 0.2618F;
    private static final float CHICK_WING_RAKE = 0.2182F;

    private static final int WING_SEGMENT = 4;

    private static final int WING_STRIP = 15;

    private static final CubeDeformation THIGH_DEFLATE = new CubeDeformation(-0.1F);

    public static LayerDefinition grown() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition chestAndLegs = body.addOrReplaceChild("chestAndLegs", CubeListBuilder.create()
                        .texOffs(0, 14).addBox(-3.5F, 3.0F, -3.0F, 7.0F, 7.0F, 6.0F),
                PartPose.offset(0.0F, -13.0F, 0.0F));

        chestAndLegs.addOrReplaceChild("tail", CubeListBuilder.create()
                        .texOffs(0, 28).addBox(-2.5F, -0.5F, -0.5F, 5.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 10.05F, 3.25F, TAIL_REST, 0.0F, 0.0F));

        chestAndLegs.addOrReplaceChild("legRight", CubeListBuilder.create()
                        .texOffs(32, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, THIGH_DEFLATE)
                        .texOffs(14, 29).addBox(-0.5F, 4.0F, -0.5F, 1.0F, 2.0F, 1.0F)
                        .texOffs(52, 27).addBox(-1.5F, 6.0F, -1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(-2.0F, 7.0F, 0.0F));

        chestAndLegs.addOrReplaceChild("legLeft", CubeListBuilder.create()
                        .texOffs(40, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, THIGH_DEFLATE)
                        .texOffs(19, 29).addBox(-0.5F, 4.0F, -0.5F, 1.0F, 2.0F, 1.0F)
                        .texOffs(52, 27).addBox(-1.5F, 6.0F, -1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(2.0F, 7.0F, 0.0F));

        PartDefinition shoulderLeft = chestAndLegs.addOrReplaceChild("shoulderLeft",
                CubeListBuilder.create(), PartPose.offset(3.5F, 3.0F, 0.0F));
        PartDefinition shoulderRight = chestAndLegs.addOrReplaceChild("shoulderRight",
                CubeListBuilder.create(), PartPose.offset(-3.5F, 3.0F, 0.0F));

        PartDefinition wingLeft = shoulderLeft.addOrReplaceChild("wingLeft", CubeListBuilder.create()
                        .texOffs(35 + WING_STRIP, 10).addBox(0.0F, 0.0F, -3.0F, 1.0F, WING_SEGMENT, 6.0F),
                PartPose.rotation(WING_RAKE, 0.0F, 0.0F));
        wingLeft.addOrReplaceChild("wingLeftTip", CubeListBuilder.create()
                        .texOffs(35 + WING_STRIP, 10 + WING_SEGMENT).addBox(0.0F, 0.0F, -3.0F, 1.0F, WING_SEGMENT, 6.0F),
                PartPose.offset(0.0F, WING_SEGMENT, 0.0F));

        PartDefinition wingRight = shoulderRight.addOrReplaceChild("wingRight", CubeListBuilder.create()
                        .texOffs(35, 10).addBox(-1.0F, 0.0F, -3.0F, 1.0F, WING_SEGMENT, 6.0F),
                PartPose.rotation(WING_RAKE, 0.0F, 0.0F));
        wingRight.addOrReplaceChild("wingRightTip", CubeListBuilder.create()
                        .texOffs(35, 10 + WING_SEGMENT).addBox(-1.0F, 0.0F, -3.0F, 1.0F, WING_SEGMENT, 6.0F),
                PartPose.offset(0.0F, WING_SEGMENT, 0.0F));

        PartDefinition neck = root.addOrReplaceChild("neck", CubeListBuilder.create(),
                PartPose.offset(0.0F, 11.0F, 0.0F));

        neck.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-0.5F, -2.0F, -4.5F, 1.0F, 2.0F, 1.0F)
                        .texOffs(0, 0).addBox(-4.0F, -6.0F, -3.5F, 8.0F, 6.0F, 7.0F, new CubeDeformation(0.01F))
                        .texOffs(24, 30).addBox(-5.0F, -7.0F, -3.5F, 10.0F, 2.0F, 0.0F),
                PartPose.offset(0.0F, 3.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 32);
    }

    public static LayerDefinition chick() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition chestAndLegs = body.addOrReplaceChild("chestAndLegs", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-1.5F, 1.5F, -1.5F, 3.0F, 4.0F, 3.0F),
                PartPose.offset(0.0F, -7.5F, 0.0F));

        chestAndLegs.addOrReplaceChild("legRight", CubeListBuilder.create()
                        .texOffs(16, 4).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 3.0F, 1.0F),
                PartPose.offset(-1.0F, 4.5F, 0.0F));

        chestAndLegs.addOrReplaceChild("legLeft", CubeListBuilder.create()
                        .texOffs(16, 0).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 3.0F, 1.0F),
                PartPose.offset(1.0F, 4.5F, 0.0F));

        PartDefinition shoulderLeft = chestAndLegs.addOrReplaceChild("shoulderLeft",
                CubeListBuilder.create(), PartPose.offset(1.5F, 1.5F, 0.0F));
        PartDefinition shoulderRight = chestAndLegs.addOrReplaceChild("shoulderRight",
                CubeListBuilder.create(), PartPose.offset(-1.5F, 1.5F, 0.0F));

        PartDefinition wingLeft = shoulderLeft.addOrReplaceChild("wingLeft", CubeListBuilder.create()
                        .texOffs(0, 15).addBox(0.0F, 0.0F, -2.0F, 1.0F, 4.0F, 4.0F),
                PartPose.rotation(CHICK_WING_RAKE, 0.0F, 0.0F));
        wingLeft.addOrReplaceChild("wingLeftTip", CubeListBuilder.create(),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition wingRight = shoulderRight.addOrReplaceChild("wingRight", CubeListBuilder.create()
                        .texOffs(10, 15).addBox(-1.0F, 0.0F, -2.0F, 1.0F, 4.0F, 4.0F),
                PartPose.rotation(CHICK_WING_RAKE, 0.0F, 0.0F));
        wingRight.addOrReplaceChild("wingRightTip", CubeListBuilder.create(),
                PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition neck = root.addOrReplaceChild("neck", CubeListBuilder.create(),
                PartPose.offset(0.0F, 16.5F, 0.0F));

        neck.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 8).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F)
                        .texOffs(16, 8).addBox(-0.5F, -1.0F, -3.0F, 1.0F, 1.0F, 1.0F),
                PartPose.offset(0.0F, 1.5F, 0.0F));

        return LayerDefinition.create(mesh, 32, 32);
    }
}
