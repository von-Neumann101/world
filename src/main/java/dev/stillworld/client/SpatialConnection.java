package dev.stillworld.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** A rigid, visual-only quarter-turn between two parts of the same frozen world. */
final class SpatialConnection {
    static final float HALF_WIDTH=18,HALF_HEIGHT=24;
    private static final float TURN=(float)(Math.PI/2);
    private final Vec3 anchor,forward,right,center;
    SpatialConnection(Vec3 anchor,Vec3 direction) {
        this.anchor=anchor;
        var horizontal=new Vec3(direction.x,0,direction.z);
        forward=horizontal.lengthSqr()<1e-8?new Vec3(0,0,1):horizontal.normalize();
        right=new Vec3(forward.z,0,-forward.x);
        center=anchor.add(forward.scale(32)).add(0,8,0);
    }
    Vec3 anchor(){return anchor;}
    Vec3 center(){return center;}
    Vec3 forward(){return forward;}
    Vec3 right(){return right;}
    private static Vec3 rotate(Vec3 vector){return new Vec3(vector.z,vector.y,-vector.x);}
    Vec3 map(Vec3 point){return anchor.add(rotate(point.subtract(anchor)));}
    Matrix4f remoteView(Matrix4f original){return new Matrix4f(original).rotateY(-TURN);}
    AABB bounds(){return new AABB(center.subtract(right.scale(HALF_WIDTH)).add(0,-HALF_HEIGHT,0),
            center.add(right.scale(HALF_WIDTH)).add(0,HALF_HEIGHT,0)).inflate(.1);}
    float strength(Vec3 camera,double reach){
        double distance=forward.dot(center.subtract(camera));
        return smooth(reach+2,reach+12,distance)*(1-smooth(64,80,camera.distanceTo(anchor)));
    }
    static float smooth(double start,double end,double value){
        double t=Math.clamp((value-start)/(end-start),0,1);return (float)(t*t*(3-2*t));
    }
    /** Only changes clip-space Z; X/Y perspective stays identical across the source opening. */
    Matrix4f clippedProjection(Matrix4f projection,Matrix4f view,Vec3 camera){
        Vec3 normal=rotate(forward),point=map(center).subtract(camera);
        var plane=new Vector4f((float)normal.x,(float)normal.y,(float)normal.z,(float)-normal.dot(point));
        plane.mul(new Matrix4f(view).invert().transpose());
        var corner=new Vector4f(Math.copySign(1,plane.x),Math.copySign(1,plane.y),1,1).mul(new Matrix4f(projection).invert());
        float denominator=plane.dot(corner);
        if(denominator<=1e-5f)return null; // Plane faces away or is entirely beyond the far clip.
        plane.mul(2/denominator);
        return new Matrix4f(projection).m02(plane.x-projection.m03()).m12(plane.y-projection.m13())
                .m22(plane.z-projection.m23()).m32(plane.w-projection.m33());
    }
}
