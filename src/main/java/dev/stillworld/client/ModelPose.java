package dev.stillworld.client;
/** Implemented only by explicit vanilla model-progress mixins. Values never apply to a real entity. */
public interface ModelPose {
    String stillworld$model();
    float[] stillworld$captureModel();
    void stillworld$applyModel(float[] values);
}
