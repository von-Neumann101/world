package dev.stillworld.client;

import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Exercises the shipped compositor in an invisible context; no Minecraft window or cursor capture. */
public final class ConnectionShaderProbe {
    private static int width=640,height=360;
    private final List<String> checks=new ArrayList<>();
    private int program,framebuffer,mainColor,mainDepth,remoteColor,output;
    private final SpatialConnection connection=new SpatialConnection(Vec3.ZERO,new Vec3(0,0,1));
    public static void main(String[] args) throws Exception {
        width=Integer.parseInt(args[2]);height=Integer.parseInt(args[3]);
        try(var errors=GLFWErrorCallback.createPrint(System.err)) {
            errors.set();if(!glfwInit())throw new AssertionError("GLFW initialization");
            glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_FOCUSED,GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            long window=glfwCreateWindow(width,height,"Invisible connection probe",0,0);
            if(window==0)throw new AssertionError("Invisible context");
            try {
                glfwMakeContextCurrent(window);GL.createCapabilities();glBindVertexArray(glGenVertexArrays());
                if(glfwGetWindowAttrib(window,GLFW_VISIBLE)!=GLFW_FALSE || glfwGetInputMode(window,GLFW_CURSOR)!=GLFW_CURSOR_NORMAL)
                    throw new AssertionError("Window/cursor safety");
                new ConnectionShaderProbe().run(Path.of(args[0]),Path.of(args[1]).resolve(width+"x"+height));
            } finally {glfwDestroyWindow(window);glfwTerminate();glfwSetErrorCallback(null);}
        }
    }
    private void run(Path shaders,Path folder) throws Exception {
        Files.createDirectories(folder);Files.writeString(folder.resolve("result.txt"),"RUNNING\n");
        mathChecks();checkSnapshotTint();
        program=program(Files.readString(shaders.resolve("connection.fsh")));
        mainColor=texture(GL_RGBA32F,GL_RGBA);mainDepth=texture(GL_R32F,GL_RED);
        remoteColor=texture(GL_RGBA32F,GL_RGBA);output=texture(GL_RGBA32F,GL_RGBA);
        framebuffer=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,output,0);
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"complete framebuffer");
        var camera=Vec3.ZERO;var view=view(new Vec3(0,0,1));var projection=projection(70);
        var vp=new Matrix4f(projection).mul(view);
        float[] base=solid(.18f,.28f,.38f),other=solid(.8f,.3f,.1f),sky=new float[width*height];Arrays.fill(sky,1);
        check(difference(base,render(base,sky,other,vp,camera,0))<.00001,"zero strength is exact identity");
        float[] connected=render(base,sky,other,vp,camera,1);
        check(pixel(connected,width/2,height/2,0)>.79,"opening reveals independently rendered destination");
        check(pixel(connected,0,0,0)==base[0],"outside opening preserves original scene");
        check(Math.abs(pixel(render(base,sky,other,vp,camera,.5f),width/2,height/2,0)-.49f)<.00001,
                "partial strength cross-fades two intact perspectives");
        float[] foreground=depthPlane(vp,8);
        check(difference(base,render(base,foreground,other,vp,camera,1))<.00001,"foreground occludes the entire opening");
        for(int y=0;y<height;y++)for(int x=0;x<width/2;x++)foreground[y*width+x]=1;
        float[] occluded=render(base,foreground,other,vp,camera,1);
        check(pixel(occluded,width/2+10,height/2,0)<.19 && pixel(occluded,width/2-10,height/2,0)>.79,
                "near object cuts the view correctly without bending either image");
        float[] ramp=gradient();float[] preserved=render(base,sky,ramp,vp,camera,1);
        check(Math.abs(pixel(preserved,width/2,height/2,0)-pixel(ramp,width/2,height/2,0))<.00001,
                "destination uses matching projection coordinates with no UV refraction");
        // Two ray-traced views of the SAME world; the orange tower is behind the viewer's right side.
        // It is absent from the normal image, present through the quarter-turn opening, and parallax moves it.
        double firstCenter=0;
        for(int step=0;step<2;step++) {
            camera=new Vec3(step*4,2,0);vp=new Matrix4f(projection).mul(view);
            float[][] normal=scene(camera,view,projection,false);
            var remoteView=connection.remoteView(view);var remoteCamera=connection.map(camera);
            float[][] remote=scene(remoteCamera,remoteView,projection,true);
            float[] result=render(normal[0],normal[1],remote[0],vp,camera,1);
            save(folder.resolve("view-"+step+"-normal.png"),normal[0]);
            save(folder.resolve("view-"+step+"-destination.png"),remote[0]);
            save(folder.resolve("view-"+step+"-connection.png"),result);
            check(orange(normal[0])==0 && orange(result)>20,"off-screen landmark visible through connection, viewpoint "+step);
            if(step==0)firstCenter=orangeCenter(result);
            else check(Math.abs(orangeCenter(result)-firstCenter)>5,"lateral camera movement produces scene parallax");
        }
        check(glGetError()==GL_NO_ERROR,"OpenGL error-free");
        Files.writeString(folder.resolve("result.txt"),String.join("\n",checks)+"\nPASS "+checks.size()+" checks\n");
        System.out.println("PASS "+checks.size()+" connection checks at "+width+"x"+height);
    }
    private void checkSnapshotTint() {
        var captured=new CapturedVertex();
        VertexConsumer tint=new StillworldRendering.SnapshotTintConsumer(captured);
        tint.addVertex(1,2,3).setColor(200,100,50,73).setUv(.25f,.75f).setUv1(7,13).setUv2(100,200).setNormal(0,1,0);
        check(captured.r>0 && captured.r<150 && captured.g<75 && captured.b<38
                && captured.r==captured.g*2 && captured.g==captured.b*2,"snapshot tint lowers brightness while retaining color ratios");
        check(captured.a==73 && captured.u==.25f && captured.v==.75f,"snapshot tint preserves alpha and texture cutout coordinates");
        check(captured.u1==7 && captured.v1==13 && captured.u2==100 && captured.v2==200,"snapshot tint preserves overlay and world lighting");
        check(captured.x==1 && captured.y==2 && captured.z==3 && captured.nx==0 && captured.ny==1 && captured.nz==0,"snapshot tint preserves geometry and normals");
        tint.setColor(0,0,0,0);
        check(captured.r==0 && captured.g==0 && captured.b==0 && captured.a==0,"snapshot tint does not lift black or fill transparent vertices");
    }
    private static final class CapturedVertex implements VertexConsumer {
        private int r,g,b,a,u1,v1,u2,v2;
        private float x,y,z,u,v,nx,ny,nz;
        public VertexConsumer addVertex(float x,float y,float z){this.x=x;this.y=y;this.z=z;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;return this;}
        public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer setUv1(int u,int v){u1=u;v1=v;return this;}
        public VertexConsumer setUv2(int u,int v){u2=u;v2=v;return this;}
        public VertexConsumer setNormal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
    }
    private void mathChecks() {
        Vec3 a=new Vec3(3,8,12),b=new Vec3(-4,3,-9);
        check(Math.abs(a.distanceTo(b)-connection.map(a).distanceTo(connection.map(b)))<1e-10,"rigid mapping preserves lengths");
        check(connection.map(new Vec3(0,0,50)).distanceTo(new Vec3(50,0,0))<1e-10,"quarter turn connects another direction");
        check(connection.strength(Vec3.ZERO,5)==1,"distant connection fully visible");
        check(connection.strength(connection.center(),5)==0,"opening disappears before camera crosses it");
        check(connection.strength(new Vec3(0,0,26),5)==0,"interaction reach is protected");
        check(connection.strength(new Vec3(0,0,40),5)==0,"backside does not teleport or show connection");
        check(connection.strength(Vec3.ZERO,40)==0,"extended interaction reach is respected");
        for(int fov:new int[]{50,70,100})for(float yaw:new float[]{-.45f,0,.45f}) {
            var camera=new Vec3(4,2,-3);var v=view(new Vec3(Math.sin(yaw),0,Math.cos(yaw)));
            v.translate(.05f,-.08f,0); // Walking bob includes translation, not only orientation.
            var p=projection(fov);var rv=connection.remoteView(v);var rc=connection.map(camera);
            var rp=connection.clippedProjection(p,rv,rc);
            var source=new Vec3(2,3,48);var remote=connection.map(source);
            var normalClip=clip(p,v,source.subtract(camera));var remoteClip=clip(rp,rv,remote.subtract(rc));
            check(Math.abs(normalClip.x/normalClip.w-remoteClip.x/remoteClip.w)<.00001 &&
                    Math.abs(normalClip.y/normalClip.w-remoteClip.y/remoteClip.w)<.00001,"matching rigid perspective "+fov+"/"+yaw);
            var inside=clip(rp,rv,connection.map(new Vec3(0,8,40)).subtract(rc));
            var outside=clip(rp,rv,connection.map(new Vec3(0,8,28)).subtract(rc));
            check(inside.z>=-inside.w && outside.z < -outside.w,"destination plane clips near side "+fov+"/"+yaw);
        }
    }
    private float[] render(float[] base,float[] depth,float[] remote,Matrix4f vp,Vec3 camera,float strength) {
        upload(mainColor,GL_RGBA,base);upload(mainDepth,GL_RED,depth);upload(remoteColor,GL_RGBA,remote);
        glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);glViewport(0,0,width,height);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);
        glUseProgram(program);bind("SceneSampler",mainColor,0);bind("DepthSampler",mainDepth,1);bind("ConnectionSampler",remoteColor,2);
        matrix("InverseViewProjection",new Matrix4f(vp).invert());
        vec("PlaneCenter",connection.center().subtract(camera));vec("PlaneNormal",connection.forward());vec("PlaneRight",connection.right());
        glUniform2f(glGetUniformLocation(program,"HalfSize"),SpatialConnection.HALF_WIDTH,SpatialConnection.HALF_HEIGHT);
        glUniform1f(glGetUniformLocation(program,"Strength"),strength);
        glDrawArrays(GL_TRIANGLES,0,3);float[] result=new float[width*height*4];glReadPixels(0,0,width,height,GL_RGBA,GL_FLOAT,result);return result;
    }
    private float[][] scene(Vec3 camera,Matrix4f view,Matrix4f projection,boolean destination) {
        var inverse=new Matrix4f(projection).mul(view).invert();var vp=new Matrix4f(projection).mul(view);
        float[] colors=new float[width*height*4],depth=new float[width*height];Arrays.fill(depth,1);
        double[][] boxes={{48,-2,-7,55,15,0},{-9,-2,57,-3,9,63},{5,-2,18,7,5,20},{-5,-2,75,3,16,80}};
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            var ray=new Vector4f((x+.5f)/width*2-1,(y+.5f)/height*2-1,.5f,1).mul(inverse);
            var direction=new Vec3(ray.x/ray.w,ray.y/ray.w,ray.z/ray.w).normalize();
            double distance=direction.y<0?(-2-camera.y)/direction.y:1e8;int hit=-1;
            for(int i=0;i<boxes.length;i++) {
                double t=box(camera,direction,boxes[i]);
                if(t>0 && t<distance && (!destination || camera.add(direction.scale(t)).x>=32)) {distance=t;hit=i;}
            }
            Vec3 point=camera.add(direction.scale(distance));
            if(destination && point.x<32)distance=1e8;
            int index=(y*width+x)*4;float r=.72f,g=.74f,b=.76f;
            if(distance<1e7) {
                float checker=(((int)Math.floor(point.x)+(int)Math.floor(point.z))&1)==0?.40f:.46f;
                r=g=b=hit>=0?.55f:checker;
                if(hit==0){r=.90f;g=.28f;b=.06f;}
                var c=clip(projection,view,point.subtract(camera));depth[y*width+x]=(c.z/c.w+1)/2;
            }
            colors[index]=r;colors[index+1]=g;colors[index+2]=b;colors[index+3]=1;
        }
        return new float[][]{colors,depth};
    }
    private static double box(Vec3 o,Vec3 d,double[] b) {
        double lo=0,hi=1e8;double[] origin={o.x,o.y,o.z},dir={d.x,d.y,d.z};
        for(int i=0;i<3;i++){if(Math.abs(dir[i])<1e-10){if(origin[i]<b[i]||origin[i]>b[i+3])return -1;continue;}
            double a=(b[i]-origin[i])/dir[i],c=(b[i+3]-origin[i])/dir[i];lo=Math.max(lo,Math.min(a,c));hi=Math.min(hi,Math.max(a,c));}
        return hi>=lo?lo:-1;
    }
    private static Matrix4f view(Vec3 forward){return new Matrix4f().lookAlong((float)forward.x,(float)forward.y,(float)forward.z,0,1,0);}
    private static Matrix4f projection(float fov){return new Matrix4f().perspective((float)Math.toRadians(fov),(float)width/height,.05f,512);}
    private static Vector4f clip(Matrix4f p,Matrix4f v,Vec3 point){return new Vector4f((float)point.x,(float)point.y,(float)point.z,1).mul(v).mul(p);}
    private static int orange(float[] a){int n=0;for(int i=0;i<a.length;i+=4)if(a[i]>.8&&a[i+1]<.35)n++;return n;}
    private static double orangeCenter(float[] a){double sum=0;int count=0;for(int i=0;i<a.length;i+=4)if(a[i]>.8&&a[i+1]<.35){sum+=(i/4)%width;count++;}return sum/count;}
    private static float pixel(float[] a,int x,int y,int c){return a[(y*width+x)*4+c];}
    private static float difference(float[] a,float[] b){float d=0;for(int i=0;i<a.length;i++)d=Math.max(d,Math.abs(a[i]-b[i]));return d;}
    private static float[] solid(float r,float g,float b){float[] a=new float[width*height*4];for(int i=0;i<a.length;i+=4){a[i]=r;a[i+1]=g;a[i+2]=b;a[i+3]=1;}return a;}
    private static float[] gradient(){float[] a=solid(0,0,.4f);for(int y=0;y<height;y++)for(int x=0;x<width;x++){a[(y*width+x)*4]=(x+.5f)/width;a[(y*width+x)*4+1]=(y+.5f)/height;}return a;}
    private static float[] depthPlane(Matrix4f vp,float distance){var c=new Vector4f(0,0,distance,1).mul(vp);float[] d=new float[width*height];Arrays.fill(d,(c.z/c.w+1)/2);return d;}
    private void check(boolean value,String description){if(!value)throw new AssertionError(description);checks.add("PASS "+description);}
    private static int texture(int internal,int format){int id=glGenTextures();glBindTexture(GL_TEXTURE_2D,id);glTexImage2D(GL_TEXTURE_2D,0,internal,width,height,0,format,GL_FLOAT,(java.nio.ByteBuffer)null);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return id;}
    private static void upload(int id,int format,float[] a){glBindTexture(GL_TEXTURE_2D,id);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,width,height,format,GL_FLOAT,a);}
    private void bind(String name,int id,int unit){glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,id);glUniform1i(glGetUniformLocation(program,name),unit);}
    private void matrix(String name,Matrix4f m){glUniformMatrix4fv(glGetUniformLocation(program,name),false,m.get(new float[16]));}
    private void vec(String name,Vec3 v){glUniform3f(glGetUniformLocation(program,name),(float)v.x,(float)v.y,(float)v.z);}
    private static int program(String fragment){int p=glCreateProgram();glAttachShader(p,shader(GL_VERTEX_SHADER,"#version 150\nout vec2 texCoord;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texCoord=p;gl_Position=vec4(p*2.-1.,0.,1.);}"));glAttachShader(p,shader(GL_FRAGMENT_SHADER,fragment));glLinkProgram(p);if(glGetProgrami(p,GL_LINK_STATUS)==0)throw new AssertionError(glGetProgramInfoLog(p));return p;}
    private static int shader(int kind,String code){int s=glCreateShader(kind);glShaderSource(s,code);glCompileShader(s);if(glGetShaderi(s,GL_COMPILE_STATUS)==0)throw new AssertionError(glGetShaderInfoLog(s));return s;}
    private static void save(Path path,float[] rgba)throws Exception{var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(y*width+x)*4;int r=Math.round(Math.clamp(rgba[i],0,1)*255),g=Math.round(Math.clamp(rgba[i+1],0,1)*255),b=Math.round(Math.clamp(rgba[i+2],0,1)*255);image.setRGB(x,height-1-y,0xff000000|r<<16|g<<8|b);}ImageIO.write(image,"png",path.toFile());}
}
