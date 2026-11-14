package dev.stillworld.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Exercises shipped GLSL without starting Minecraft or ever showing/focusing a window. */
public final class SpatialShaderProbe {
    private static final int W=640, H=360;
    private static final String VERTEX="""
            #version 150
            out vec2 texCoord;
            void main() {
                vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);
                texCoord=p; gl_Position=vec4(p*2.0-1.0,0.0,1.0);
            }
            """;
    private final int color=texture(GL_RGBA32F,GL_RGBA), depth=texture(GL_R32F,GL_RED), edges=texture(GL_RGBA32F,GL_RGBA);
    private final int target=texture(GL_RGBA32F,GL_RGBA), framebuffer=glGenFramebuffers();
    private final StringBuilder report=new StringBuilder();
    private int checks;

    public static void main(String[] args) throws Exception {
        try(var errors=GLFWErrorCallback.createPrint(System.err)) {
            errors.set();
            if(!glfwInit())throw new AssertionError("GLFW initialization failed");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED,GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW,GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            long window=glfwCreateWindow(W,H,"Stillworld invisible shader probe",0,0);
            if(window==0)throw new AssertionError("Hidden context creation failed");
            try {
                glfwMakeContextCurrent(window); GL.createCapabilities();
                glBindVertexArray(glGenVertexArrays());
                if(glfwGetWindowAttrib(window,GLFW_VISIBLE)!=GLFW_FALSE || glfwGetInputMode(window,GLFW_CURSOR)!=GLFW_CURSOR_NORMAL)
                    throw new AssertionError("Probe must stay hidden with an ordinary cursor");
                new SpatialShaderProbe().run(Path.of(args[0]),Path.of(args[1]));
            } finally {glfwDestroyWindow(window);glfwTerminate();glfwSetErrorCallback(null);}
        }
    }

    private void run(Path shaders,Path output) throws Exception {
        Files.createDirectories(output);
        Files.writeString(output.resolve("result.txt"),"RUNNING: hidden shader validation\n");
        glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("Incomplete framebuffer");
        int spatial=program(shaders.resolve("spatial.fsh"));
        int contours=program(shaders.resolve("contours.fsh"));
        int monochrome=program(shaders.resolve("monochrome.fsh"));
        var camera=new Vector3f();
        var matrix=projection(70,0,0,camera);
        float[] colors=gradient(), near=plane(matrix,6), far=plane(matrix,100), sky=new float[W*H];
        Arrays.fill(sky,1);
        upload(edges,GL_RGBA,new float[W*H*4]);
        float[] reference=render(spatial,colors,far,matrix,camera,1,0,5);
        check(difference(colors,reference)<.00001,"zero strength preserves input");
        check(difference(colors,render(spatial,colors,far,matrix,camera,0,1,0))<.00001,"pre-scan preserves input");
        check(difference(colors,render(spatial,colors,near,matrix,camera,1,1,5))<.00001,"near interaction stays aligned");
        float[] changed=render(spatial,colors,far,matrix,camera,1,1,5);
        check(difference(reference,changed)>.001,"far scene visibly deforms");
        check(difference(changed,render(spatial,colors,far,matrix,camera,1,1,100))<.00001,"settled deformation has no time wobble");
        var bob=new Matrix4f().perspective((float)Math.toRadians(70),(float)W/H,.05f,1024).translate(.025f,-.04f,0);
        float[] bobbed=render(spatial,colors,plane(bob,100),bob,camera,1,1,5);
        check(difference(changed,bobbed)<.00015,"walking bob does not amplify into far-scene swimming");
        var otherNear=new Matrix4f().perspective((float)Math.toRadians(70),(float)W/H,.10f,1024).translate(.025f,-.04f,0);
        check(difference(bobbed,render(spatial,colors,plane(otherNear,100),otherNear,camera,1,1,5))<.0001,
                "optical field is independent of projection near plane under bob translation");
        check(borderDifference(reference,changed)<.00001,"screen borders do not smear or expose gaps");
        check(maxDifference(reference,changed)<.10,"displacement remains bounded");
        float[] foreground=far.clone();
        for(int y=80;y<280;y++)for(int x=250;x<390;x++)foreground[y*W+x]=near[y*W+x];
        float[] protectedView=render(spatial,colors,foreground,matrix,camera,1,1,5);
        check(regionDifference(colors,protectedView,250,80,390,280)<.00001,"near foreground remains fixed over a bent background");
        float[] plain=new float[W*H*4];
        for(int i=0;i<W*H;i++){plain[i*4]=.69f;plain[i*4+1]=.74f;plain[i*4+2]=.8f;plain[i*4+3]=1;}
        check(difference(plain,render(spatial,plain,sky,matrix,camera,1,1,5))<.00001,"sky stays open and color is preserved");
        // Run the existing color treatment on alpha-zero sky as well: final alpha must be opaque.
        float[] blackSky=new float[W*H*4];
        float[] litSky=render(monochrome,blackSky,sky,matrix,camera,1,1,5);
        check(litSky[0]>.55 && litSky[3]>.999,"existing treatment does not turn empty sky black");
        for(int fov:new int[]{50,70,100})for(int yaw:new int[]{0,75,170}) {
            var view=projection(fov,yaw,18,camera);
            float[] d=plane(view,100);
            float[] pixels=render(spatial,colors,d,view,camera,1,1,5);
            check(allFinite(pixels)&&borderDifference(colors,pixels)<.00001,"bounded view fov="+fov+" yaw="+yaw);
        }
        for(int i=0;i<3;i++) {
            var eye=new Vector3f(i==1?5:0,i==2?3:0,0);
            var view=projection(i==2?95:70,i==1?18:0,i==2?-25:-5,eye);
            var fixture=fixture(view,eye);
            float[] edgeImage=render(contours,fixture[0],fixture[1],view,eye,1,1,5); upload(edges,GL_RGBA,edgeImage);
            float[] base=render(monochrome,fixture[0],fixture[1],view,eye,1,1,5);
            float[] result=render(spatial,base,fixture[1],view,eye,1,1,5);
            write(output.resolve("view-"+i+"-before.png"),base);
            write(output.resolve("view-"+i+"-after.png"),result);
        }
        int error=glGetError();check(error==GL_NO_ERROR,"OpenGL error state is clear: "+error);
        Files.writeString(output.resolve("result.txt"),"PASS "+checks+" checks\nGPU: "+glGetString(GL_RENDERER)+"\n"+report);
        System.out.println("SPATIAL SHADER PASS: "+checks+" checks; hidden context; evidence "+output);
    }

    private float[] render(int program,float[] rgba,float[] depths,Matrix4f matrix,Vector3f camera,float progress,float strength,float elapsed) {
        upload(color,GL_RGBA,rgba);upload(depth,GL_RED,depths);
        glUseProgram(program);
        int[] textures={color,depth,edges};String[] names={"DiffuseSampler","DepthSampler","EdgeSampler"};
        for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,textures[i]);glUniform1i(glGetUniformLocation(program,names[i]),i);}
        glUniform2f(glGetUniformLocation(program,"InSize"),W,H);
        glUniformMatrix4fv(glGetUniformLocation(program,"InverseViewProjection"),false,matrix.invert(new Matrix4f()).get(new float[16]));
        glUniformMatrix4fv(glGetUniformLocation(program,"ViewProjection"),false,matrix.get(new float[16]));
        glUniform3f(glGetUniformLocation(program,"CameraFromOrigin"),camera.x,camera.y,camera.z);
        uniform(program,"ScanProgress",progress);uniform(program,"ScanRadius",progress*progress*256);
        uniform(program,"ScanRange",256);uniform(program,"DistortionStrength",strength);uniform(program,"Elapsed",elapsed);
        glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);glViewport(0,0,W,H);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);
        glDrawArrays(GL_TRIANGLES,0,3);
        float[] pixels=new float[W*H*4];glReadPixels(0,0,W,H,GL_RGBA,GL_FLOAT,pixels);return pixels;
    }

    private static Matrix4f projection(float fov,float yaw,float pitch,Vector3f eye) {
        // Minecraft's event model-view contains rotation only; positions are camera-relative.
        return new Matrix4f().perspective((float)Math.toRadians(fov),(float)W/H,.05f,1024)
                .rotateX((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(yaw));
    }
    private static float[] plane(Matrix4f matrix,float distance) {
        var inverse=matrix.invert(new Matrix4f());float[] result=new float[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            Vector4f p=inverse.transform(new Vector4f((x+.5f)/W*2-1,(y+.5f)/H*2-1,1,1));
            Vector3f ray=new Vector3f(p.x,p.y,p.z).normalize().mul(distance);
            p=matrix.transform(new Vector4f(ray,1));result[y*W+x]=p.z/p.w*.5f+.5f;
        }return result;
    }
    private static float[] gradient() {
        float[] result=new float[W*H*4];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;result[i]=.15f+.7f*(x+.5f)/W;result[i+1]=.15f+.7f*(y+.5f)/H;result[i+2]=.25f;result[i+3]=1;}
        return result;
    }
    private static float[][] fixture(Matrix4f matrix,Vector3f eye) {
        // Independent CPU ray/box scene supplies genuine perspective depth and first-person occlusions.
        float[][] boxes={{-9,-2,-32,-4,12,-25},{4,-2,-55,13,22,-45},{-38,-2,-100,-22,35,-80},
                {18,-2,-135,42,48,-110},{-75,-2,-240,-40,85,-180},{-1,-2,-7,1,0,-5},
                {-3,8,-40,3,9,-25},{-3,-2,-40,-2,9,-25},{2,-2,-40,3,9,-25}};
        float[] colors=new float[W*H*4], depths=new float[W*H];var inverse=matrix.invert(new Matrix4f());
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            Vector4f p=inverse.transform(new Vector4f((x+.5f)/W*2-1,(y+.5f)/H*2-1,1,1));
            Vector3f ray=new Vector3f(p.x,p.y,p.z).normalize();
            float t=ray.y<0?(-2-eye.y)/ray.y:Float.POSITIVE_INFINITY;int object=-1,side=1;
            for(int b=0;b<boxes.length;b++) {
                float lo=0,hi=1024;int face=0;
                for(int axis=0;axis<3;axis++) {
                    float a=(boxes[b][axis]-eye.get(axis))/ray.get(axis),z=(boxes[b][axis+3]-eye.get(axis))/ray.get(axis);
                    float enter=Math.min(a,z);if(enter>lo){lo=enter;face=axis;}hi=Math.min(hi,Math.max(a,z));
                }
                if(hi>=lo&&lo>0&&lo<t){t=lo;object=b;side=face;}
            }
            int i=y*W+x;float tone;
            if(t>700) {depths[i]=1;tone=.55f+.18f*(y/(float)H);}
            else {
                var hit=new Vector3f(ray).mul(t).add(eye);
                p=matrix.transform(new Vector4f(new Vector3f(ray).mul(t),1));depths[i]=p.z/p.w*.5f+.5f;
                float grid=((int)Math.floor(hit.x)+(int)Math.floor(hit.y)+(int)Math.floor(hit.z))%2==0?.055f:0;
                tone=(object<0?.32f:side==0?.25f:side==1?.65f:.42f)+grid;
            }
            colors[i*4]=tone;colors[i*4+1]=tone;colors[i*4+2]=tone;colors[i*4+3]=1;
        }return new float[][]{colors,depths};
    }
    private static int texture(int internal,int format) {
        int id=glGenTextures();glBindTexture(GL_TEXTURE_2D,id);
        glTexImage2D(GL_TEXTURE_2D,0,internal,W,H,0,format,GL_FLOAT,0L);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return id;
    }
    private static void upload(int texture,int format,float[] data){glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,W,H,format,GL_FLOAT,data);}
    private static int program(Path path) throws Exception {
        int vertex=shader(GL_VERTEX_SHADER,VERTEX),fragment=shader(GL_FRAGMENT_SHADER,Files.readString(path));
        int result=glCreateProgram();glAttachShader(result,vertex);glAttachShader(result,fragment);glLinkProgram(result);
        if(glGetProgrami(result,GL_LINK_STATUS)==GL_FALSE)throw new AssertionError(path+": "+glGetProgramInfoLog(result));
        glDeleteShader(vertex);glDeleteShader(fragment);return result;
    }
    private static int shader(int type,String source){int id=glCreateShader(type);glShaderSource(id,source);glCompileShader(id);if(glGetShaderi(id,GL_COMPILE_STATUS)==GL_FALSE)throw new AssertionError(glGetShaderInfoLog(id));return id;}
    private static void uniform(int p,String name,float v){glUniform1f(glGetUniformLocation(p,name),v);}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;report.append("PASS ").append(label).append('\n');}
    private static double difference(float[] a,float[] b){double sum=0;for(int i=0;i<a.length;i++)sum+=Math.abs(a[i]-b[i]);return sum/a.length;}
    private static double maxDifference(float[] a,float[] b){double max=0;for(int i=0;i<a.length;i++)max=Math.max(max,Math.abs(a[i]-b[i]));return max;}
    private static boolean allFinite(float[] a){for(float v:a)if(!Float.isFinite(v)||v<0||v>1)return false;return true;}
    private static double regionDifference(float[] a,float[] b,int x0,int y0,int x1,int y1){double max=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)for(int c=0;c<4;c++)max=Math.max(max,Math.abs(a[(y*W+x)*4+c]-b[(y*W+x)*4+c]));return max;}
    private static double borderDifference(float[] a,float[] b){return Math.max(Math.max(regionDifference(a,b,0,0,W,2),regionDifference(a,b,0,H-2,W,H)),Math.max(regionDifference(a,b,0,0,2,H),regionDifference(a,b,W-2,0,W,H)));}
    private static void write(Path path,float[] pixels)throws Exception {
        var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;int r=Math.round(Math.clamp(pixels[i],0,1)*255),g=Math.round(Math.clamp(pixels[i+1],0,1)*255),b=Math.round(Math.clamp(pixels[i+2],0,1)*255);image.setRGB(x,H-1-y,(r<<16)|(g<<8)|b);}
        ImageIO.write(image,"png",path.toFile());
    }
}
